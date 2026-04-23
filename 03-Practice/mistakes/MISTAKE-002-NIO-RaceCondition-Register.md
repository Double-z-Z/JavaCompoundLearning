---
created: 2026-04-21
tags: [mistake, nio, concurrency, race-condition]
error_id: MISTAKE-002
status: resolved
related_concepts:
  - [[NIO-Selector]]
  - [[Boss-Worker模型]]
  - [[线程安全]]
---

# NIO Worker 注册竞态条件导致客户端连接丢失

**错误ID**: MISTAKE-002  
**所属主题**: [[MOC-nio]]  
**状态**: 🟢 已解决


## 错误现象

NIO 聊天室启动后，5 个客户端连接进来，但广播测试时发现只有 1 个客户端能收到消息，其余 4 个客户端完全没有注册到 Selector 上。

```java
// 问题代码（ServerHandler.register()）
public void register(SocketChannel channel) throws ClosedChannelException {
    if (!isRunning) return;  // ← 罪魁祸首
    channel.register(selector, SelectionKey.OP_READ);
    channels.add(channel);
    clientContextMap.put(channel, new ClientContext());
}
```


## 我的错误理解

> "Worker 还没启动（isRunning=false），不应该注册客户端，等启动了再说。"

我在 `register()` 里加了 `if (!isRunning) return;`，意图是防止在 Worker 线程启动前接受注册。但这个 guard 和启动时序形成了竞态条件。


## 根本原因分析

### 时序图

```
时间轴 ──────────────────────────────────────────────►

Boss 线程: 创建 Worker ──► 提交到线程池 ──► 进入 accept 循环 ──► accept() 返回 client1
                                                  │
                                                  ▼
Worker 线程:                    [异步启动中...]        [刚执行到 run() 里的 isRunning=true]
                                                  │
                                                  ▼
register(client1): isRunning=false → 直接返回    ←── 客户端丢失！
```

### 为什么竞态会发生？

1. **`workerExecutor.execute()` 只是提交任务，不是同步启动**
   - `execute()` 把 Runnable 放入线程池队列，线程何时真正启动由线程池调度决定
   - 在提交和实际执行 `run()` 之间有一个时间窗口

2. **Boss 线程在提交 Worker 后立即开始 accept**
   ```java
   for (int i = 0; i < workerCount; i++) {
       ServerHandler newHandler = new ServerHandler(this).openSelector();
       workers[i] = newHandler;
       workerExecutor.execute(() -> newHandler.run()); // 异步！
   }
   // 这里不等 Worker 启动，直接进入 accept 循环
   while (isRunning) {
       selector.select();
       // accept 到连接后立刻调用 handler.register(channel)
   }
   ```

3. **`register()` 被 Boss 线程调用，但检查的是 Worker 线程的状态**
   - `isRunning` 的默认值是 `false`
   - Worker 线程在 `run()` 里才设置 `this.isRunning = true`
   - 如果 Boss 线程先执行到 `register()`，检查 `isRunning` 为 false，直接返回

### 后果

- `channel.register()` 没执行 → Selector 不知道这个 Channel
- `channels.add()` 没执行 → 广播时遍历不到这个客户端
- `clientContextMap.put()` 没执行 → 读写时找不到上下文
- **客户端静默丢失**，没有任何报错


## 正确理解

✅ **`register()` 不依赖 `isRunning`，它只负责把 Channel 注册到 Selector**
   - `channel.register()` 是原子操作，不依赖循环是否运行
   - Selector 会记住注册，等 `select()` 开始调用时就能感知事件

✅ **`isRunning` 只控制 `run()` 循环的启停**，不是注册的前置条件
   - 即使 Worker 还没进入 `while` 循环，先注册也是安全的
   - 真正的关闭保护在 `shutdown()` / `stop()` 中处理

✅ **多线程启动需要关注时序**，异步提交后不能假设目标线程已就绪


## 纠正过程

1. 添加 DEBUG 日志后发现 `register called ...` 和 `register done` 日志缺失
2. 定位到 `if (!isRunning) return;` 导致注册被跳过
3. 理解 `execute()` 的异步语义：提交 ≠ 执行
4. 移除 `isRunning` guard，让 `register()` 无条件执行

```java
// 修复后
public void register(SocketChannel channel) throws ClosedChannelException {
    channel.register(selector, SelectionKey.OP_READ);
    channels.add(channel);
    clientContextMap.put(channel, new ClientContext());
}
```


## 关联知识

- [[NIO-Selector]] - 关联：`channel.register()` 的语义，注册和 select() 的时序无关
- [[Boss-Worker模型]] - 关联：Boss 线程和 Worker 线程的职责边界，异步启动的时序问题
- [[线程池]] - 关联：`execute()` 的异步提交语义，任务放入队列后线程何时启动不确定
- [[CopyOnWriteArrayList]] - 关联：为修复此问题而引入的线程安全集合（`channels` 被多线程访问）


## 预防措施

- [ ] 多线程启动时，区分"任务已提交"和"线程已就绪"两个状态
- [ ] guard 条件要精确匹配真正需要保护的操作，不要过度防御
- [ ] 对异步边界添加同步机制（如 CountDownLatch）或取消不必要的 guard
- [ ] 调试此类问题时，优先怀疑"操作是否被静默跳过"


## 类似错误

- 暂无历史相似错误


---

## 🤖 AI评价

### 错误类型
- 类型：时序竞态条件（Race Condition）
- 严重程度：高（导致功能完全失效，但无报错）
- 复发风险：中

### 对掌握度的影响
- [[NIO-Selector]]: -10分 (发现对 register() 语义理解偏差)
- [[Boss-Worker模型]]: +15分 (深刻理解异步启动时序)
- [[线程安全]]: +10分 (理解多线程边界的 guard 条件设计)

### 模式识别
- 是否与历史错误相似：否（但和并发编程中的"先检查后执行"模式类似）

### 针对性建议
1. 深入理解 `ThreadPoolExecutor.execute()` 的异步语义
2. 在多线程边界处画出时序图，确认"提交-执行-使用"的先后顺序
3. 为 NIO 聊天室画出完整的线程交互图

---

## 📊 错误模式统计

```dataview
TABLE status, created, related_concepts
FROM #mistake
SORT created DESC
```
