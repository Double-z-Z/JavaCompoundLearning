# 线程阻塞机制

> **标签**：#并发编程 #JVM #操作系统 #futex #park  
> **难度**：高级  
> **前置知识**：[[Java 内存模型]]、[[AQS 框架]]、[[锁机制]]  
> **关联知识**：[[futex 深度探索]]、[[JVM 线程状态]]、[[CPU 调度]]

---

## 📖 核心概念

### **什么是线程阻塞？**

线程阻塞是指线程在等待某个条件满足时，主动让出 CPU 时间片，从运行状态转为等待状态的过程。

**典型场景**：
- 等待锁释放（synchronized、ReentrantLock）
- 等待条件满足（Condition.await()）
- 等待 IO 完成（socket.read()）
- 等待时间流逝（Thread.sleep()）

---

## 🏗️ 设计思想

### **核心设计原则**

**原则 1：分层抽象**
```
每一层封装底层细节，提供简洁的 API

Java 层：LockSupport.park()  ← 一行代码
    ↓
JVM 层：Parker::park()       ← C++ 实现
    ↓
OS 层：futex_wait()          ← 系统调用
    ↓
硬件：CPU 上下文切换          ← 硬件指令
```

**原则 2：用户态优先**
```
能用户态解决的，不进内核态

无竞争场景：
- CAS 原子操作（用户态）
- 耗时：~20 纳秒

有竞争场景：
- futex_wait 系统调用（内核态）
- 耗时：~1-5 微秒
```

**原则 3：解耦状态变更与资源分配**
```
唤醒（状态变更）：WAITING → RUNNABLE
调度（资源分配）：分配 CPU 时间片

两者独立，提高灵活性
```

---

## 🔗 完整调用链路

### **从 Java 到硬件的全链路**

```
┌─────────────────────────────────────────┐
│  Java 层                                 │
│  LockSupport.park()                     │
│  - 调用 Unsafe.park()                   │
│  - 可中断、可超时                        │
└─────────────────────────────────────────┘
              ↓ JNI 调用
┌─────────────────────────────────────────┐
│  JDK 层                                  │
│  Unsafe.park()                          │
│  - JVM_Unsafe_Park()                    │
│  - 线程状态转换                          │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│  JVM 层（C++）                           │
│  Parker::park()                         │
│  - 检查_counter（未消费的 unpark）      │
│  - pthread_cond_wait()                  │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│  glibc 层                                │
│  pthread_cond_wait()                    │
│  - 准备 futex 参数                        │
│  - syscall 指令                          │
└─────────────────────────────────────────┘
              ↓ syscall 指令（Ring 3 → Ring 0）
┌─────────────────────────────────────────┐
│  内核层                                  │
│  futex(FUTEX_WAIT)                      │
│  - futex_wait()                         │
│  - 加入等待队列                          │
│  - schedule()                           │
│  - context_switch()                     │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│  硬件层                                  │
│  CPU 保存/恢复寄存器                     │
│  - 保存当前线程上下文                    │
│  - 加载下一个线程上下文                  │
└─────────────────────────────────────────┘
```

---

### **关键切换点**

**唯一的用户态→内核态切换**：
```c
// glibc 中的 syscall 指令
mov rax, 202          ; futex 系统调用号
syscall               ; ← 这里！CPU 特权级提升
                      ;    Ring 3 → Ring 0
```

**性能含义**：
- 之前的 CAS 操作：用户态，~20 纳秒
- syscall 切换：~100 纳秒
- 之后的 schedule()：内核态，~1-5 微秒

---

## 🧩 核心组件

### **1. Parker（JVM 层）**

**职责**：封装平台相关的阻塞实现

**关键设计**：
```cpp
class Parker : public os::PlatformParker {
    volatile int _counter;      // unpark 计数器
    pthread_mutex_t _mutex;     // 互斥锁
    pthread_cond_t _cond;       // 条件变量
    
public:
    void park(jlong millis) {
        pthread_mutex_lock(&_mutex);
        
        // 优化：检查未消费的 unpark
        if (_counter > 0) {
            _counter = 0;
            pthread_mutex_unlock(&_mutex);
            return;  // 无需阻塞
        }
        
        // 调用 futex_wait 阻塞
        pthread_cond_wait(&_cond, &_mutex);
        pthread_mutex_unlock(&_mutex);
    }
    
    void unpark() {
        pthread_mutex_lock(&_mutex);
        if (_counter == 0) {
            _counter = 1;
            pthread_cond_signal(&_cond);  // 唤醒
        }
        pthread_mutex_unlock(&_mutex);
    }
};
```

**优化策略**：
- 先检查 `_counter > 0`，避免不必要的系统调用
- 多次 `unpark()` 只计 1 次（计数器饱和）
- 使用 pthread 库，跨平台兼容

---

### **2. futex（内核层）**

**全称**：Fast User-space mu**tex**

**设计哲学**：
> "大多数情况下无竞争，仅在真正需要时才进入内核"

**核心机制**：
```c
// 用户态变量
int futex_var = 0;  // 0=无锁，1=有锁

// 无竞争场景（快速路径）
if (atomic_compare_and_swap(&futex_var, 0, 1) == 0) {
    return;  // 成功，无需系统调用
}

// 有竞争场景（慢速路径）
futex_wait(&futex_var, 1);  // 系统调用
```

**性能对比**：
| 场景 | 耗时 | 原因 |
|------|------|------|
| 无竞争（CAS） | ~20 纳秒 | 用户态原子操作 |
| 有竞争（futex_wait） | ~1-5 微秒 | 系统调用 + 调度 |

---

### **3. schedule()（内核调度器）**

**职责**：选择下一个运行的线程

**核心流程**：
```c
void schedule(void) {
    struct task_struct *tsk = current;
    
    // 1. 标记当前线程状态
    tsk->state = TASK_INTERRUPTIBLE;
    
    // 2. 从运行队列移除
    deactivate_task(tsk);
    
    // 3. 选择优先级最高的线程
    next = pick_next_task(rq, tsk);
    
    // 4. 上下文切换
    context_switch(rq, tsk, next);
    // ↑ 保存 tsk 的寄存器，加载 next 的寄存器
}
```

**关键数据结构**：
- `task_struct`：线程描述符
- `runqueue`：运行队列（每 CPU 一个）
- `wait_queue`：等待队列

---

## ⚠️ 常见误区

### **误区 1：park() 直接调用 futex**

❌ **错误**：
> "LockSupport.park() 直接调用 futex_wait()"

✅ **正确**：
```
park() → pthread_cond_wait() → futex_wait()

中间还有 glibc 的 pthread 库封装
```

---

### **误区 2：阻塞 = 失去 CPU**

❌ **错误**：
> "线程阻塞后，CPU 就去执行其他线程了"

✅ **正确**：
```
阻塞是两个独立操作：
1. 状态变更：RUNNABLE → WAITING（立即完成）
2. 调度切换：稍后由 schedule() 完成

被阻塞的线程可能还在 CPU 上（中断返回前）
```

---

### **误区 3：唤醒 = 立即执行**

❌ **错误**：
> "unpark() 后，线程立即恢复执行"

✅ **正确**：
```
唤醒流程：
unpark() → 状态：WAITING → RUNNABLE
    ↓
加入运行队列
    ↓
等待 schedule() 选择
    ↓
可能立即执行，也可能继续等待

取决于：
- 线程优先级
- 其他线程的运行状态
- 调度器策略
```

---

## 💡 实践建议

### **1. 减少阻塞频率**

**场景**：高频计数器
```java
// ❌ 不推荐：每次 increment 都阻塞
synchronized(counter) {
    counter++;
}

// ✅ 推荐：使用 LongAdder
LongAdder counter = new LongAdder();
counter.increment();  // 分段 CAS，极少阻塞
```

---

### **2. 合理设置超时**

**场景**：等待资源
```java
// ❌ 不推荐：无限等待
lock.lock();

// ✅ 推荐：带超时
if (lock.tryLock(100, TimeUnit.MILLISECONDS)) {
    try {
        // 业务逻辑
    } finally {
        lock.unlock();
    }
} else {
    // 降级处理
}
```

**好处**：
- 避免死锁
- 快速失败
- 系统更健壮

---

### **3. 避免虚假唤醒**

**场景**：Condition 等待
```java
// ❌ 不推荐：if 判断
if (conditionNotMet) {
    cond.await();
}

// ✅ 推荐：while 循环
while (conditionNotMet) {
    cond.await();
}
```

**原因**：
- 可能被虚假唤醒（spurious wakeup）
- 可能条件又被其他线程改变
- while 保证条件真正满足

---

### **4. 性能调试技巧**

**工具**：
```bash
# 查看线程状态
jstack <pid>

# 查看锁竞争
jstat -lock <pid>

# 查看系统调用
strace -e futex -p <pid>

# 查看 CPU 调度
perf sched record
perf sched report
```

**关键指标**：
- 阻塞次数
- 平均阻塞时间
- 上下文切换频率
- 系统调用占比

---

## 🔗 知识关联

### **上游知识**
- [[Java 内存模型]] → happens-before 规则
- [[AQS 框架]] → 等待队列管理
- [[锁机制]] → synchronized vs ReentrantLock

### **下游知识**
- [[futex 深度探索]] → 内核实现细节
- [[JVM 线程状态]] → 状态流转
- [[CPU 调度]] → schedule() 原理

### **横向关联**
- [[LongAdder 设计]] → 分段减少阻塞
- [[中断处理]] → 硬件唤醒机制
- [[缓存行伪共享]] → 性能优化

---

## 📊 性能参考

### **阻塞开销参考值**

| 操作 | 耗时 | 说明 |
|------|------|------|
| park()/unpark() | ~130-530 纳秒 | 含系统调用 |
| synchronized | ~200-800 纳秒 | 含锁竞争 |
| ReentrantLock | ~150-600 纳秒 | AQS 框架 |
| LockSupport.park() | ~130-500 纳秒 | 直接调用 |

**对比**：
- CAS 原子操作：~20 纳秒
- volatile 读：~5 纳秒
- 普通方法调用：~1 纳秒

---

## 🎯 总结

**核心要点**：
1. 线程阻塞是跨层次协作（Java → JVM → OS → Hardware）
2. 唯一的用户态→内核态切换点是 futex 系统调用
3. 唤醒 ≠ 调度（状态变更 vs 资源分配）
4. 阻塞有开销，能用户态解决就不进内核

**应用价值**：
- 理解并发库的底层实现
- 调试性能问题（如死锁、活锁）
- 设计高并发系统（减少阻塞）

---

> **下一步探索**：[[futex 深度探索]]、[[Netty 事件循环]]、[[背压机制]]
