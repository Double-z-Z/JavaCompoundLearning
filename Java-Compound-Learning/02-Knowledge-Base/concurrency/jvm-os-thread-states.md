# JVM 线程状态与操作系统线程状态

> **标签**：#JVM #操作系统 #线程管理 #状态流转 #调度  
> **难度**：中级  
> **前置知识**：[[Java 线程基础]]、[[线程阻塞机制]]  
> **关联知识**：[[futex 深度探索]]、[[CPU 调度]]、[[中断处理]]

---

## 📖 核心概念

### **双层状态管理**

Java 线程系统采用**双层状态管理**：

**Java 层（逻辑状态）**：
- 反映代码执行上下文
- 面向 Java 开发者
- 通过 `Thread.getState()` 查询

**操作系统层（物理状态）**：
- 反映 CPU 调度状态
- 面向操作系统内核
- 通过 `/proc/<pid>/status` 查询

**关键区别**：
> Java 的 RUNNABLE 包含"等待 IO"  
> OS 的 Running 仅指"正在 CPU 执行"

---

## 🏗️ 设计思想

### **为什么需要双层状态？**

**原因 1：抽象层次不同**

```
Java 层关注：
- 代码执行到哪了？
- 在等什么资源？
- 能否继续执行？

OS 层关注：
- 线程在 CPU 上吗？
- 在等待什么硬件事件？
- 调度器如何选择下一个线程？
```

**原因 2：跨平台需求**

```
Java 需要支持：
- Windows（线程状态：Running、Ready、Blocked）
- Linux（线程状态：R、S、D、T、Z）
- macOS（线程状态：running、runnable、blocked）

Java 统一抽象为 6 种状态，屏蔽 OS 差异
```

---

### **状态映射关系**

```
┌─────────────────────────────────────────────────────────┐
│  Java 层（逻辑状态）                                     │
├─────────────────────────────────────────────────────────┤
│  NEW              → OS: 无对应（线程未启动）            │
│  RUNNABLE         → OS: Running 或 Runnable             │
│  BLOCKED          → OS: Blocked（等待锁）               │
│  WAITING          → OS: Sleeping（可中断睡眠）          │
│  TIMED_WAITING    → OS: Sleeping（超时睡眠）            │
│  TERMINATED       → OS: Zombie（僵尸进程）              │
└─────────────────────────────────────────────────────────┘
              ↓ JVM 映射
┌─────────────────────────────────────────────────────────┐
│  OS 层（Linux 物理状态）                                 │
├─────────────────────────────────────────────────────────┤
│  R (Running)      → 正在 CPU 执行                         │
│  S (Sleeping)     → 可中断睡眠（WAITING）               │
│  D (Disk Sleep)   → 不可中断睡眠（IO 等待）              │
│  T (Stopped)      → 停止（调试、信号）                  │
│  Z (Zombie)       → 僵尸（已终止，父进程未回收）        │
└─────────────────────────────────────────────────────────┘
```

---

## 🧩 详细状态对比

### **1. NEW 状态**

**Java 层**：
```java
Thread t = new Thread(() -> {
    System.out.println("Hello");
});
// t.getState() → NEW
```

**OS 层**：
- 无对应状态
- 线程未创建，仅 Java 对象存在
- 不占用 OS 资源

**底层实现**：
```cpp
// JVM 调用 pthread_create 前
JavaThread* thread = new JavaThread();
thread->set_thread_state(_thread_new);
// 此时 OS 还未创建线程
```

---

### **2. RUNNABLE 状态**

**Java 层**：
```java
t.start();
// t.getState() → RUNNABLE
```

**OS 层**：
```
可能状态 1：Running（正在 CPU 执行）
ps -o state -p <pid> → R

可能状态 2：Runnable（等待 CPU 时间片）
ps -o state -p <pid> → R

可能状态 3：等待 IO（阻塞但 Java 层仍显示 RUNNABLE）
socket.read();  // 阻塞 IO，但 Java 状态仍是 RUNNABLE
```

**关键洞察**：
> Java 的 RUNNABLE 是**广义的可运行**  
> 包含"正在运行"和"等待 IO"两种情况  
> 因为 JVM 认为"等待 IO 也是可运行的，只是暂时被 OS 阻塞"

---

### **3. BLOCKED 状态**

**Java 层**：
```java
synchronized(obj) {
    // 线程 A 持有锁
}

// 线程 B 尝试进入
synchronized(obj) {  // ← BLOCKED
    // 等待线程 A 释放锁
}
```

**OS 层**：
```
状态：Blocked（等待锁）
ps -o state -p <pid> → D 或 S

等待队列：ObjectMonitor.EntryList
唤醒条件：锁释放
```

**底层实现**：
```cpp
// ObjectMonitor::enter()
void ObjectMonitor::enter(TRAPS) {
    if (TryLock(self) > 0) {
        return;  // 成功
    }
    
    // 失败，加入 EntryList
    AddEntryWait(self);
    
    // 阻塞（调用 futex_wait）
    pthread_cond_wait(_cond, _mutex);
    // OS 状态：RUNNING → BLOCKED
}
```

---

### **4. WAITING 状态**

**Java 层**：
```java
// 场景 1：等待 Condition
condition.await();  // WAITING

// 场景 2：等待其他线程
thread.join();  // WAITING

// 场景 3：park 阻塞
LockSupport.park();  // WAITING
```

**OS 层**：
```
状态：Sleeping（可中断睡眠）
ps -o state -p <pid> → S

等待队列：Condition 队列 / futex 等待队列
唤醒条件：signal() / unpark() / interrupt()
```

**关键特性**：
- 可中断：`Thread.interrupt()` 可唤醒
- 无超时：无限期等待
- 不占用 CPU：线程挂起

---

### **5. TIMED_WAITING 状态**

**Java 层**：
```java
// 场景 1：带超时的等待
condition.await(100, TimeUnit.MILLISECONDS);  // TIMED_WAITING

// 场景 2：sleep
Thread.sleep(1000);  // TIMED_WAITING

// 场景 3：带超时的 park
LockSupport.parkNanos(1000000);  // TIMED_WAITING
```

**OS 层**：
```
状态：Sleeping（超时睡眠）
ps -o state -p <pid> → S

唤醒条件：signal() / 超时
```

**与 WAITING 的区别**：
| 特性 | WAITING | TIMED_WAITING |
|------|---------|---------------|
| 超时 | 无 | 有 |
| 唤醒 | 只能手动 | 手动或自动 |
| 使用场景 | 无限期等待 | 带超时的等待 |

---

### **6. TERMINATED 状态**

**Java 层**：
```java
t.join();  // 等待线程终止
// t.getState() → TERMINATED
```

**OS 层**：
```
状态：Zombie（僵尸进程）
ps -o state -p <pid> → Z

等待父进程回收资源
```

---

## 📊 状态流转图

### **完整状态流转**

```
NEW
  ↓ start()
RUNNABLE
  ├─ 竞争锁失败 → BLOCKED
  │                ↓ 锁释放
  │              RUNNABLE
  │
  ├─ 调用 wait()/park() → WAITING
  │                        ↓ signal()/unpark()
  │                      RUNNABLE
  │
  ├─ 调用 sleep()/wait(timeout) → TIMED_WAITING
  │                                ↓ timeout/signal()
  │                              RUNNABLE
  │
  └─ run() 方法返回 → TERMINATED
```

### **park() 调用的状态流转**

```
详细流程：

Java 层：
RUNNABLE
  ↓ LockSupport.park()
WAITING

JVM 层：
_thread_runnable
  ↓ JVM_Unsafe_Park()
_thread_blocked

OS 层：
Running
  ↓ futex_wait()
Sleeping (S)
  ↓ futex_wake()
Running
```

---

## ⚠️ 常见误区

### **误区 1：RUNNABLE = 正在执行**

❌ **错误**：
> "线程状态是 RUNNABLE，说明它正在 CPU 上执行"

✅ **正确**：
```
RUNNABLE 包含三种情况：

情况 1：正在 CPU 执行
- OS 状态：Running
- 真正占用 CPU

情况 2：等待 CPU 时间片
- OS 状态：Runnable
- 在运行队列中等待调度

情况 3：等待 IO（被 OS 阻塞）
- OS 状态：Blocked/Sleeping
- Java 层仍显示 RUNNABLE
- 因为 JVM 认为"IO 完成后就能继续运行"

示例：
Thread t = new Thread(() -> {
    socket.read();  // 阻塞 IO
});
t.start();
t.getState();  // RUNNABLE（但实际被 OS 阻塞）
```

---

### **误区 2：BLOCKED = WAITING**

❌ **错误**：
> "BLOCKED 和 WAITING 都是等待，没区别"

✅ **正确**：
```
BLOCKED：
- 等待 synchronized 锁
- 自动唤醒（锁释放时）
- 不可中断（interrupt() 无效）

WAITING：
- 等待 Condition/park
- 需要手动 signal()/unpark()
- 可中断（interrupt() 有效）

示例：
// BLOCKED
synchronized(obj) { }  // 等待锁

// WAITING
condition.await();     // 等待条件
LockSupport.park();    // 等待 unpark
```

---

### **误区 3：interrupt() 能中断所有阻塞**

❌ **错误**：
> "调用 interrupt() 可以中断任何阻塞的线程"

✅ **正确**：
```
可中断的阻塞：
- WAITING（condition.await()、park()）
- TIMED_WAITING（sleep()、wait(timeout)）
- 可中断的 IO（SocketChannel）

不可中断的阻塞：
- BLOCKED（synchronized 锁等待）
- 不可中断的 IO（传统 BIO）
- futex_wait()（内核态阻塞）

示例：
// 可中断
try {
    LockSupport.park();
} catch (InterruptedException e) {
    // 能捕获
}

// 不可中断
synchronized(obj) {
    // 等待锁，interrupt() 无效
}
```

---

## 💡 实践建议

### **1. 调试线程状态**

**工具**：
```bash
# Java 层：查看线程状态
jstack <pid>

# 输出示例：
"main" #1 prio=5 os_prio=0 tid=0x00007f8c48006800 nid=0x1234 waiting on condition
java.lang.Thread.State: WAITING (parking)
    at sun.misc.Unsafe.park(Native Method)
    - Parking to wait for <0x00000000deadbeef>

# OS 层：查看线程状态
ps -o state,cmd -p <pid>

# 输出示例：
S  java -jar app.jar  ← Sleeping

# 查看 IO 等待
cat /proc/<pid>/status | grep State

# 输出示例：
State:  S (sleeping)
```

---

### **2. 状态分析技巧**

**场景 1：CPU 利用率高**
```bash
# 查看 RUNNABLE 状态的线程
jstack <pid> | grep -A 5 "RUNNABLE"

# 分析：
- 如果是计算密集型，正常
- 如果是自旋锁，优化为阻塞
```

**场景 2：线程都 BLOCKED**
```bash
# 查看锁等待
jstack <pid> | grep -B 5 "BLOCKED"

# 分析：
- 找到锁持有者
- 检查是否死锁
- 优化锁粒度
```

**场景 3：大量 WAITING**
```bash
# 查看等待条件
jstack <pid> | grep -B 5 "WAITING"

# 分析：
- 检查是否缺少 signal()
- 检查线程池是否耗尽
- 检查是否有未唤醒的线程
```

---

### **3. 性能优化建议**

**减少 BLOCKED**：
```java
// ❌ 不推荐：粗粒度锁
synchronized(this) {
    // 大量代码
}

// ✅ 推荐：细粒度锁
private final Object lock = new Object();
synchronized(lock) {
    // 最小化临界区
}
```

**减少 WAITING**：
```java
// ❌ 不推荐：无限等待
condition.await();

// ✅ 推荐：带超时
if (condition.await(100, TimeUnit.MILLISECONDS)) {
    // 成功
} else {
    // 降级处理
}
```

---

## 🔗 知识关联

### **上游知识**
- [[Java 线程基础]] → Thread 类与状态枚举
- [[JVM 内存模型]] → 线程与工作内存
- [[锁机制]] → synchronized 与 BLOCKED 状态

### **下游知识**
- [[线程阻塞机制]] → park() 的状态流转
- [[futex 深度探索]] → 内核等待队列
- [[CPU 调度]] → schedule() 与状态切换

### **横向关联**
- [[中断处理]] → interrupt() 机制
- [[IO 模型]] → BIO/NIO 的阻塞特性
- [[线程池]] → 线程复用与状态管理

---

## 📈 性能参考

### **状态切换开销**

| 切换 | 耗时 | 说明 |
|------|------|------|
| RUNNABLE → WAITING | ~130-530 纳秒 | park() 系统调用 |
| WAITING → RUNNABLE | ~100 纳秒 | unpark() 唤醒 |
| RUNNABLE → BLOCKED | ~200 纳秒 | 锁竞争失败 |
| BLOCKED → RUNNABLE | ~200 纳秒 | 锁释放唤醒 |

**对比**：
- 线程创建：~1-10 微秒
- 线程销毁：~1-10 微秒
- 上下文切换：~1-10 微秒

---

## 🎯 总结

**核心要点**：
1. Java 层和 OS 层有独立的状态管理
2. RUNNABLE 包含"正在运行"和"等待 IO"
3. BLOCKED 和 WAITING 的本质不同
4. interrupt() 不能中断所有阻塞
5. 状态切换有开销，避免频繁切换

**应用价值**：
- 调试线程问题（死锁、活锁）
- 性能优化（减少阻塞）
- 设计合理的线程模型

---

> **下一步探索**：[[CPU 调度]]、[[中断处理]]、[[线程池优化]]
