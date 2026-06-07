# 代码审查报告 — `src/feature.py`

> 审查对象：`src/feature.py`（TaskScheduler 调度模块，1064 行）
> 设计参考：`docs/design.md` v1.0
> 审查日期：2026-06-07
> 审查员：opencode (严格模式)

---

## 0. 概览

| 维度 | 评价 | 严重度 |
|---|---|---|
| 设计符合度 | **基本符合**，但有 2 处明确偏离、3 处 NFR 缺失 | 中 |
| Bug / 并发 / 空指针 | **存在 1 个致命死锁 + 2 个高危竞态** | 高 |
| 错误处理 | **Listener 异常被篡改、熔断时序错乱** | 高 |
| 代码风格 | **前向引用、模块级副作用、私有成员越权访问** | 中 |

**总体结论：❌ 不建议合并**。`Metrics.snapshot()` 存在确定性死锁；超时控制完全失效；`cancel()` 存在数据竞争；错误处理多处丢失原始异常类型。需修复 P0/P1 后重审。

---

## 1. 设计符合度

### 1.1 ✅ 符合的部分

- 5 层架构（API/Facade/Core/Infra/SPI）划分清晰，依赖方向正确
- `Task` 数据模型字段齐全（`id`/`name`/`payload`/`trigger`/`retryPolicy`/`timeout`）
- 状态机 `PENDING/RUNNING/SUCCESS/FAILED/CANCELLED` 与设计 §3.4 一致
- 触发策略 SPI 实现了 `Immediate`/`Delayed`/`Periodic`/`FixedRate` 四种
- 重试策略 SPI 实现了 `NoRetry`/`ExponentialBackoff`/`FixedDelay`
- 线程池参数符合 §5.4：核心 = CPU 核数，最大 = 核数 × 2，有界队列
- 熔断参数符合 §5.4：失败率 > 50% 暂停 30s
- 监听器 SPI 完整

### 1.2 ⚠️ 偏离设计

| # | 位置 | 设计要求 | 实际实现 | 严重度 |
|---|---|---|---|---|
| D1 | `TaskSchedulerFacade.query` L752-764 | §3.2 接口签名 `TaskStatus query(String taskId)`，返回**枚举** | 返回 `Dict[str, Any]`，包含 7 个字段 | 中 |
| D2 | `Dispatcher._watch_timeout` L594-611 | §5.3 明确要求 "使用 `Future.get(timeout)` 强制中断"，"超时视作 `TransientException` 触发重试" | 仅设置 `task.last_error` 标记，实际执行线程**无法被中断**，超时后任务仍可正常完成并标记 SUCCESS | **高** |
| D3 | `TaskRepository` 全文 | §6 NFR 要求 "WAL + 定期快照，重启自动恢复 PENDING/RUNNING" | 纯内存 `Dict[str, Task]`，无任何持久化，进程崩溃即丢失所有任务 | **高** |
| D4 | `Metrics` 全文 | §5.5 明确要求 `task_queue_size` 是 Gauge | 已实现 `queue_size`，但 §5.5 要求 `task_execute_duration` 是 Histogram；实现为 `Deque[float]`（带 `maxlen=1024`），无分桶、无 P50/P99 分位 | 低 |
| D5 | `query` 返回值 | §3.4 数据契约规定 `taskId/status/attempt/lastError/nextFireTime` | 多了 `name` 和 `createdAt`，字段命名混用驼峰与无驼峰 | 低 |

### 1.3 设计遗漏

- §4.2 失败流程图要求 `attempt++` 显式递增，代码在 `_dispatch` 中递增后再交给 retry 判定。语义正确（首次执行 attempt=1），但缺少文档说明递增时机。

---

## 2. Bug / 并发 / 空指针

### 🔴 P0-1：`Metrics.snapshot()` 确定性死锁

**位置**：`src/feature.py:140-157`

```python
@property
def failure_rate(self) -> float:
    with self._lock:                       # 非可重入 Lock
        ...
        return sum(...) / len(...)

def snapshot(self) -> Dict[str, Any]:
    with self._lock:                       # 已持有锁
        return {
            ...,
            "failure_rate": self.failure_rate,   # 再访问属性 → 再次 with self._lock
        }
```

**问题**：`Metrics._lock` 是 `threading.Lock`（非可重入），`snapshot()` 持有锁后访问 `self.failure_rate` 属性会再次进入 `with self._lock:`，**当前线程永久阻塞**。

**触发条件**：任意线程调用 `client.metrics_snapshot()`。

**修复建议**：
- 方案 A：把 `_lock` 改为 `threading.RLock`
- 方案 B：在 `snapshot()` 内读取 `self.failure_rate_window` 后本地计算，避免递归取锁

```python
# 推荐
def snapshot(self) -> Dict[str, Any]:
    with self._lock:
        window = list(self.failure_rate_window)
        rate = (sum(window) / len(window)) if window else 0.0
        return {..., "failure_rate": rate}
```

---

### 🔴 P0-2：超时控制完全失效

**位置**：`src/feature.py:594-611`

**问题**：设计 §5.3 要求"使用 `Future.get(timeout)` 强制中断"且"超时视作 TransientException 触发重试"。Python `ThreadPoolExecutor` 返回的 `Future` **不支持取消正在运行的同步任务**，`future.cancel()` 对已开始执行的任务总是返回 `False`。

实际行为：
1. 看门狗线程轮询 30s 后仅设置 `task.last_error = "timeout..."`
2. Worker 线程中的 `payload` 继续运行
3. 任务最终自然完成 → `_execute_payload` 调用 `inc_success()` 和 `_on_success()` → 状态被设为 **SUCCESS**，覆盖了超时标记
4. 已计入 `task_success_total` 指标，与 `last_error` 矛盾

**影响**：超长任务永远不会被强制中断；超时重试逻辑形同虚设；用户无法依赖 `timeout` 字段保护下游。

**修复建议**：
- 短期：明确文档说明"timeout 是软超时，最小化资源浪费但不能保证中断"
- 长期：使用 `multiprocessing` 替代 `threading`，或要求 `payload` 自行轮询取消标志

---

### 🟠 P1-1：`cancel()` 与 `dispatcher` 数据竞争

**位置**：`src/feature.py:737-750`

```python
def cancel(self, task_id: str) -> bool:
    task = self._repo.get(task_id)         # ① 读取
    if task is None: raise TaskNotFoundError(...)
    if task.status == TaskStatus.RUNNING:  # ② 检查
        raise TaskNotCancellableError(...)
    ...
    task.status = TaskStatus.CANCELLED     # ③ 写入
    self._repo.update(task)
```

**竞态**：
- 时序 A：cancel 读到 `status=PENDING` → 进入写入路径
- 时序 B：dispatcher 在 ② 与 ③ 之间 `_dispatch()` 把状态改为 `RUNNING` 并开始执行
- 结果：repo 中状态被改为 `CANCELLED`，但 worker 仍在执行
- worker 完成后调用 `_on_success`，把状态再覆盖回 `SUCCESS`，repo 与现实不符
- `query()` 也可能在 `CANCELLED` 与 `SUCCESS` 之间闪变

**修复建议**：
- 把"读-改-写"封装为 repo 的原子操作：
  ```python
  def cancel_if_pending(repo, task_id) -> bool:
      with repo._lock:
          task = repo._tasks.get(task_id)
          if task is None: raise TaskNotFoundError(...)
          if task.status in (RUNNING,) | TaskStatus.terminal_states():
              return False
          task.status = CANCELLED
          return True
  ```
- 状态机不变量应在 repo 层守护，而非 facade 层

---

### 🟠 P1-2：`_check_circuit` 越权访问私有成员 + 时序错乱

**位置**：`src/feature.py:551-561`

```python
def _check_circuit(self) -> None:
    rate = self._metrics.failure_rate        # ① 不持锁读
    with self._metrics._lock:                # ② 越权访问 _lock
        window_len = len(self._metrics.failure_rate_window)
    if window_len >= self._cb_window and rate > self._cb_threshold:
        self._cb_until = self._clock.now() + self._cb_pause
```

**问题**：
1. `rate` 在 ① 处读取时不持锁，② 处 `window_len` 持锁读，**两者来自不同时刻的快照**，判断可能误判
2. 越权访问 `self._metrics._lock` 与 `_failure_rate_window`，破坏封装
3. 熔断开启后**只检查** `_run_loop` 入口，**不阻止**已经在执行的失败任务继续重试入队
4. `_handle_retry_or_fail` 在熔断开启时仍会重新 `offer` 任务，熔断期间队列堆积

**修复建议**：
- 在 `Metrics` 中添加 `public def failure_window_size() -> int` 与 `current_rate_and_size() -> tuple[float, int]`
- 在 `offer` / `_handle_retry_or_fail` 中先检查 `_circuit_open()`，开启时拒绝入队或延迟入队

---

### 🟡 P2-1：`FixedRateTrigger._fired` 非线程安全

**位置**：`src/feature.py:208-221`

```python
class FixedRateTrigger(Trigger):
    def __init__(self, fire_at: datetime):
        self.fire_at = fire_at
        self._fired = False                  # 普通 bool
    def next_fire_time(self, from_time: datetime) -> Optional[datetime]:
        if self._fired: return None
        if from_time >= self.fire_at:
            self._fired = True                # 竞态写
            return from_time
```

**问题**：多个 dispatcher 线程并发调用 `next_fire_time`，可能多次设置 `_fired` 并都返回 `from_time`，导致**任务被重复触发**。

**修复建议**：用 `threading.Event` 或 `itertools.count` + 原子比较。

---

### 🟡 P2-2：`_purge_cancelled` 复杂度 O(n) 触发堆重建

**位置**：`src/feature.py:415-423`

```python
def _purge_cancelled(self) -> None:
    if not self._cancelled: return
    kept = [item for item in self._heap if item[2] not in self._cancelled]  # O(n) 扫描
    self._cancelled.clear()
    heapq.heapify(kept)                        # O(n) 重建
    self._heap = kept
```

**问题**：每次 `take_ready` 都可能触发 O(n) 扫描 + 重建。大量取消事件时退化为 O(n²)。

**修复建议**：
- 方案 A：懒删除标记法（双 set：`_removed`），`take_ready` 时遇跳过
- 方案 B：限制 `_cancelled` 集合大小，定期整体重建

---

### 🟡 P2-3：空指针 / None 处理

| 位置 | 风险 | 备注 |
|---|---|---|
| `Task.payload` 注释为 `Callable`，但 dataclass 字段无类型约束 | 若 `payload=None` 进入执行流会 `TypeError` | `_validate` 已覆盖，但 `_dispatch` 内部无防御 |
| `TaskRepository.get` 返回 `Optional[Task]`，调用方应判 None | `TaskSchedulerFacade.query` 已判 | OK |
| `Metrics.failure_rate_window` 为空时返回 0.0 | 合理 | OK |
| `trigger.next_fire_time` 可能返回 None | `_validate` 未检查；`submit` 检查首次返回值但不检查周期 | 周期触发器 `next_fire_time` 返回 None 时 `_on_success` 优雅退出，OK |
| `Listener.on_failure(task, RuntimeError(...))` | 异常类型被偷换（见 §3） | 严重 |

---

### 🟢 P3-1：`WorkerPool.submit` 信号量语义可疑

**位置**：`src/feature.py:434-472`

- 用 `BoundedSemaphore(queue_capacity)` 限流，但捕获 `ValueError: pass`，可能掩盖真实 bug
- `caller-runs` 分支同步执行，**会阻塞 dispatcher 线程**（因为 dispatcher 调 `_pool.submit`）。背压到 dispatcher 自身而非调用方，与设计 §5.4 描述的"背压到调用方"不一致

---

## 3. 错误处理

### 🟠 P1-3：Listener 接收被篡改的异常

**位置**：`src/feature.py:662-670`

```python
def _on_failure(self, task: Task, terminal: bool) -> None:
    task.status = TaskStatus.FAILED
    self._repo.update(task)
    for listener in self._listeners:
        try:
            listener.on_failure(task, RuntimeError(task.last_error or "business error"))
        ...
```

**问题**：
- 原始 `BusinessException`（含业务错误码、上下文）被替换为 `RuntimeError(task.last_error)` 字符串
- `task.last_error` 本身是 `str`，异常类型与堆栈全部丢失
- 监控/告警系统无法按异常类型做策略路由

**对比**：`_handle_retry_or_fail` 中走 retry 路径时，listener 收到的是**原始** `exc`（L682）。两路径行为不一致。

**修复建议**：
- `_on_failure` 增加 `cause: BaseException` 参数，由调用方传入原始异常
- 移除 `RuntimeError(...)` 包装

---

### 🟡 P2-4：异常吞噬

- `_run_loop` 用 `except Exception` + `logger.exception` 兜底（OK）
- 所有 listener 调用都用 `except Exception: logger.exception(...)`（OK）
- 但 `_execute_payload` 抛出后**先调用 `inc_failure` 与 `inc_retry` 等指标更新再 raise**，若指标更新本身抛异常会破坏外层捕获

---

### 🟡 P2-5：`_validate` 前向引用未定义类

**位置**：`src/feature.py:766-781`

```python
def _validate(self, task: Task) -> None:    # L766
    ...
    if task is None:
        raise IllegalArgumentError(...)     # IllegalArgumentError 在 L781 才定义

class IllegalArgumentError(ValueError): ...  # L781
```

**问题**：运行时正确（方法体延迟求值），但**可读性差、IDE 跳转失效、重构工具可能误判**。且 `IllegalArgumentError` 继承 `ValueError` 而非 `SchedulerError`，与 §5.1 异常体系不一致。

**修复建议**：
- 上移 `IllegalArgumentError` 定义到 L48 附近
- 让其继承 `BusinessException`（参数异常属于业务异常，不重试），符合 §5.1 表格

---

### 🟢 P3-2：模块级 Logger 副作用

**位置**：`src/feature.py:32-37`

```python
logger = logging.getLogger("task_scheduler")
if not logger.handlers:
    handler = logging.StreamHandler()
    ...
    logger.addHandler(handler)
    logger.setLevel(logging.INFO)
```

**问题**：
- 导入模块即修改全局日志配置，干扰调用方日志框架
- 多进程/多实例下多个 handler 累积（`if not logger.handlers` 仅在首次生效）
- 应由调用方配置日志，模块仅 `getLogger(__name__)` 即可

---

## 4. 代码风格

### 4.1 命名

- ✅ 总体遵循 PEP 8，类名 PascalCase，方法/变量 snake_case
- ⚠️ `Task.is_async: bool` 含义模糊（异步任务？异步 payload？），建议 `is_async_payload`
- ⚠️ `Metrics._lock` 与 `Metrics.failure_rate` 同名属性+方法外的私有字段，命名空间拥挤
- ⚠️ 异常类名 `IllegalArgumentError` 是 Java 风格；Python 应为 `InvalidArgumentError` 或复用 `ValueError`

### 4.2 注释 / 文档

- ✅ 模块顶部有分层概览 docstring
- ✅ 关键设计点有行内注释（如 `# 引入单调递增 counter 保证同时间下顺序稳定`）
- ⚠️ `LoggingTaskListener`、`SubmitBuilder`、`SchedulerClientBuilder` 等公共类无 docstring
- ⚠️ 中英混用（如 `# 使用轮询 + 取消策略` `# 标记超时：置 cause 后由执行线程处理`），建议统一为中文或英文
- ⚠️ `# noqa: BLE001` 频繁出现（至少 5 处），说明 `except Exception` 过宽，应改用具体异常类型

### 4.3 异常处理风格

- ⚠️ `except Exception` 与具体异常（`BusinessException`/`TransientException`）混用，规则不一致
- ⚠️ `_run_loop` 与 listener 调用处都用 `except Exception: logger.exception(...)`，但 `_execute_payload` 的非业务异常分支直接 `raise`，监听器拿不到最终堆栈
- ⚠️ 熔断检查、状态转换应集中到状态机类，零散分布在 `_on_success` / `_on_failure` / `_handle_retry_or_fail` 三个方法中，难维护

### 4.4 类型注解

- ✅ 主流签名有完整类型注解
- ⚠️ `dict`/`list`/`tuple` 应使用小写内置（PEP 585），但代码用的是 `Dict`/`List`/`Tuple`（typing 模块）。3.9+ 建议统一
- ⚠️ `Task.is_async: bool = False` 缺少注释说明触发条件
- ⚠️ `Listener.on_success` / `on_failure` 是 `abc.ABC` 抽象方法，但有默认 `...` 实现，等价于非抽象，破坏 SPI 约束

### 4.5 其它

- ⚠️ 私有成员 `_lock` 被 `Dispatcher` 直接访问（`self._metrics._lock`），破坏封装
- ⚠️ `Metrics` 既有 `_lock: threading.Lock`（field）又有 `inc_*` 方法（with lock），模式统一但与 `TaskRepository` 的 `RLock` 不一致——为什么 `Metrics` 用 `Lock`、`Repository` 用 `RLock`？原因不明
- ⚠️ `Dispatcher.stop` 注释"通过插入一个哨兵不可行——这里让线程超时自然返回"——承认了 stop 有 1s+ 延迟，应提供 `_stop_event` 唤醒机制（如 `_queue.offer_sentinel()` 触发 `notify_all`）
- ⚠️ `_self_check` 是冒烟测试，应分离到 `tests/` 目录，不应与产品代码同文件

---

## 5. 修复优先级与建议

### 必须修复（阻断合并）

| ID | 简述 | 估计工作量 |
|---|---|---|
| P0-1 | `Metrics.snapshot()` 死锁 | 10 分钟 |
| P0-2 | 超时控制失效（文档化或实现真中断） | 1-2 天 |
| P1-1 | `cancel()` 数据竞争 | 2 小时 |
| P1-3 | Listener 异常类型丢失 | 30 分钟 |

### 强烈建议（合并前修复）

| ID | 简述 | 估计工作量 |
|---|---|---|
| P1-2 | `_check_circuit` 越权 + 时序 | 1 小时 |
| D2 | 显式标注"软超时"语义 | 10 分钟 |
| P2-5 | `IllegalArgumentError` 重新定位 | 10 分钟 |

### 建议（合并后修复）

- D3 持久化（P0 NFR，工作量大）
- P2-1/2/3/4 并发与异常处理细节
- 代码风格与注释统一

---

## 6. 测试覆盖建议

当前 `_self_check` 仅覆盖 5 个场景，缺失：

- 并发 submit/cancel 压测（验证 P1-1）
- 熔断开启后的行为（验证 P1-2）
- 持久化崩溃恢复（D3）
- TaskListener 收到的异常类型与 `last_error` 关系（P1-3）
- 极端 `timeout` 值（0、负数、超大）
- `Metrics.snapshot()` 在高并发下不卡死（P0-1 单元测试）

---

*End of Report*
