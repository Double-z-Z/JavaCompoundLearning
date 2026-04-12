# futex 深度探索

> **标签**：#操作系统 #并发编程 #内核 #线程阻塞 #性能优化  
> **难度**：高级  
> **前置知识**：[[线程阻塞机制]]、[[CAS 原理]]、[[用户态与内核态]]  
> **关联知识**：[[ObjectMonitor]]、[[pthread 库]]、[[中断处理]]

---

## 📖 核心概念

### **什么是 futex？**

**futex** = **F**ast **U**ser-space mu**tex**（快速用户态互斥锁）

**定义**：
futex 是一种混合锁机制，大多数操作在用户态通过原子指令完成，仅在真正需要阻塞时才进入内核态。

**设计哲学**：
> "大多数情况下无竞争，仅在真正需要时才进入内核"

**核心价值**：
- 无竞争：纯用户态 CAS，~20 纳秒
- 有竞争：进入内核阻塞，~1-5 微秒
- 比传统 mutex 快 5-10 倍

---

## 🏗️ 设计思想

### **核心原则：用户态优先**

**传统 mutex 的问题**：
```
pthread_mutex_lock()
  ↓
系统调用（每次都需要）
  ↓
内核态加锁
  ↓
系统调用返回

总开销：~1000 纳秒（即使无竞争）
```

**futex 的优化**：
```
futex_lock()
  ↓
用户态 CAS 检查（无竞争）
  ↓
成功 → 返回（~20 纳秒）
失败 → futex_wait() 系统调用（~1-5 微秒）
```

**性能对比**：
| 场景 | 传统 mutex | futex | 提升倍数 |
|------|-----------|-------|----------|
| 无竞争 | ~1000 纳秒 | ~20 纳秒 | 50 倍 |
| 有竞争 | ~1000 纳秒 | ~1-5 微秒 | 相当 |

---

### **双重检查模式**

**核心机制**：
```c
// 用户态代码
int futex_lock(int *futex_var) {
    // 第一次检查（用户态）
    if (*futex_var == 0) {
        // 尝试 CAS
        if (atomic_compare_and_swap(futex_var, 0, 1) == 0) {
            return 0;  // 成功，无需系统调用
        }
    }
    
    // CAS 失败，进入内核
    return futex_wait(futex_var, 1);
}

// 内核态代码（futex_wait 系统调用）
SYSCALL_DEFINE2(futex_wait, u32 *, uaddr, u32, val) {
    // 第二次检查（内核态）
    if (*uaddr != val) {
        return -EWOULDBLOCK;  // 值已变化，无需阻塞
    }
    
    // 加入等待队列
    struct futex_q q;
    q.task = current;
    queue_me(&q);
    
    // 阻塞线程
    schedule();
    
    return 0;
}
```

**为什么需要双重检查？**
```
防止竞态条件：

线程 A: 检查 *futex_var == 0
    ↓
线程 B: futex_var = 1（其他线程加锁）
    ↓
线程 A: CAS 失败
    ↓
进入 futex_wait()
    ↓
如果不二次检查：
  线程 A 阻塞（但锁已可用！）→ 死锁

二次检查后：
  发现 *futex_var != 0
  直接返回，重新 CAS
```

---

## 🧩 核心组件

### **1. futex 变量（用户态）**

```c
// 用户内存中的整数
int futex_var = 0;  // 0=无锁，1=有锁

// 语义：
// 0 → 锁可用
// 1 → 锁被占用
// 2+ → 锁被占用，且有等待者
```

**关键特性**：
- 普通整数，无特殊结构
- 位于用户内存（进程地址空间）
- 通过原子指令访问（CAS、fetch_add）

---

### **2. 等待队列（内核态）**

```c
// 内核中的等待队列
struct futex_q {
    struct task_struct *task;  // 等待的线程
    struct list_head list;     // 队列链表指针
    u32 val;                   // 期望值
};

// 全局哈希表（按 futex 地址哈希）
struct futex_hash_bucket {
    spinlock_t lock;
    struct list_head chain;
};
```

**组织方式**：
```
futex 地址 → 哈希 → hash_bucket → 链表 → futex_q

示例：
futex_var 地址：0x12345678
    ↓ hash(0x12345678)
hash_bucket[42]
    ↓
futex_q1 → futex_q2 → futex_q3
 (线程 A)  (线程 B)  (线程 C)
```

---

### **3. 系统调用接口**

```c
// Linux 系统调用（x86_64，syscall 号 202）
SYSCALL_DEFINE6(futex, u32 *, uaddr, int, op, u32, val, 
                struct timespec *, utime, u32 *, uaddr2, u32, val3);

// 主要操作：
FUTEX_WAIT      // 阻塞，直到被唤醒或超时
FUTEX_WAKE      // 唤醒 n 个等待者
FUTEX_LOCK_PI   // 优先级继承锁（实时系统）
FUTEX_UNLOCK_PI // 释放优先级继承锁
```

**使用示例（汇编）**：
```asm
; 调用 futex_wait
mov rax, 202          ; futex 系统调用号
mov rdi, uaddr        ; 参数 1: futex 地址
mov rsi, FUTEX_WAIT   ; 参数 2: 操作类型
mov rdx, val          ; 参数 3: 期望值
syscall               ; 进入内核态
```

---

## 📊 futex vs ObjectMonitor

### **对比分析**

| 维度 | futex（内核） | ObjectMonitor（JVM） |
|------|--------------|---------------------|
| **实现层级** | 内核层（C） | JVM 层（C++） |
| **保护对象** | 内核等待队列 | Java 堆对象内存访问 |
| **同步目标** | 阻塞/唤醒顺序 | 多线程访问顺序 |
| **使用场景** | pthread、JVM park() | synchronized |
| **等待队列** | 内核 futex_q | ObjectMonitor.EntryList |
| **唤醒机制** | futex_wake() | notify()/notifyAll() |

### **共同点**

```
1. 都使用"用户态 + 内核态"混合
   - 用户态：原子操作（快速路径）
   - 内核态：阻塞等待（慢速路径）

2. 都依赖等待队列
   - futex: futex_q 链表
   - ObjectMonitor: EntryList + WaitSet

3. 都使用 CAS 检测竞争
   - futex: atomic_compare_and_swap
   - ObjectMonitor: Atomic::cmpxchg

4. 都依赖 CPU 调度器
   - 阻塞：schedule()
   - 唤醒：加入运行队列
```

### **本质区别**

```
futex 的职责：
- 管理内核等待队列
- 提供阻塞/唤醒原语
- 不关心业务逻辑

ObjectMonitor 的职责：
- 管理 Java 对象的锁状态
- 实现 synchronized 语义
- 处理 wait()/notify()

关系：
ObjectMonitor 底层调用 futex（通过 pthread）
```

---

## ⚠️ 常见误区

### **误区 1：futex 保护 CPU 调度**

❌ **错误**：
> "futex 加锁是为了保护 schedule() 函数"

✅ **正确**：
```
futex 保护的是：内核等待队列的数据结构

具体场景：
线程 A: futex_wait() → 加入等待队列
    ↓
线程 B: futex_wait() → 也加入等待队列
    ↓
问题：如果同时加入，队列链表指针会混乱！

解决：futex_wait() 内部有自旋锁保护队列操作

schedule() 本身不需要保护：
- 每个 CPU 核心有独立的运行队列
- CPU0 的 schedule() 只操作 rq0
- CPU1 的 schedule() 只操作 rq1
- 不会并发访问同一数据结构
```

---

### **误区 2：futex 消除 CAS**

❌ **错误**：
> "futex 不用 CAS，直接修改 futex_var"

✅ **正确**：
```c
// futex_lock 的用户态部分
if (atomic_compare_and_swap(&futex_var, 0, 1) == 0) {
    return 0;  // CAS 成功
}

// futex_var 的修改必须用 CAS
原因：
- 多个线程可能同时检查 *futex_var == 0
- 如果都用普通赋值，会同时认为成功
- CAS 保证只有一个线程成功

futex 的优化：
- 不是消除 CAS，而是减少进入内核的次数
- 无竞争时，用户态 CAS 解决
- 有竞争时，才进入内核
```

---

### **误区 3：futex 总是更快**

❌ **错误**：
> "任何场景都用 futex 替代 mutex"

✅ **正确**：
```
适合 futex：
- 低竞争场景（99% 无竞争）
- 短时临界区（持有锁时间短）
- 用户态可解决的场景

不适合 futex：
- 高竞争场景（CAS 持续失败）
- 长时临界区（持有锁时间长）
- 需要复杂同步逻辑（如读写锁）

此时传统 mutex 可能更优：
- 内核优化更好（自适应锁）
- 减少用户态自旋浪费
```

---

## 💡 实践建议

### **1. 性能调试**

**工具**：
```bash
# 查看 futex 系统调用
strace -e futex -p <pid>

# 输出示例：
futex(0x7f1234567000, FUTEX_WAIT_PRIVATE, 1, NULL) = 0
futex(0x7f1234567000, FUTEX_WAKE_PRIVATE, 1) = 1

# 分析 futex 竞争
perf record -e sched:sched_switch -p <pid>
perf report
```

**关键指标**：
- futex_wait 调用次数
- 平均阻塞时间
- CAS 失败率

---

### **2. 优化建议**

**减少 futex 调用**：
```java
// ❌ 不推荐：频繁阻塞
synchronized(lock) {
    // 短临界区
    counter++;
}

// ✅ 推荐：使用原子类
AtomicLong counter = new AtomicLong();
counter.incrementAndGet();  // 用户态 CAS

// ✅ 推荐：使用 LongAdder（高并发）
LongAdder counter = new LongAdder();
counter.increment();  // 分段 CAS
```

**避免虚假唤醒**：
```java
// ❌ 不推荐：if 判断
synchronized(lock) {
    if (!condition) {
        lock.wait();
    }
}

// ✅ 推荐：while 循环
synchronized(lock) {
    while (!condition) {
        lock.wait();
    }
}
```

---

### **3. 设计模式**

**模式 1：自旋 + 阻塞混合**
```c
// 先自旋几次（可能很快释放）
int spin_count = 0;
while (atomic_compare_and_swap(&futex_var, 0, 1) != 0) {
    if (spin_count++ < MAX_SPIN) {
        cpu_pause();  // 暂停指令，减少功耗
    } else {
        // 自旋失败，进入内核阻塞
        futex_wait(&futex_var, 1);
        break;
    }
}
```

**优势**：
- 短等待：自旋更快（无系统调用）
- 长等待：阻塞更优（不浪费 CPU）

---

## 🔗 知识关联

### **上游知识**
- [[CAS 原理]] → 原子指令实现
- [[用户态与内核态]] → 系统调用机制
- [[线程阻塞机制]] → park() 底层实现

### **下游知识**
- [[ObjectMonitor]] → synchronized 实现
- [[pthread 库]] → JVM 如何调用 futex
- [[中断处理]] → 硬件唤醒机制

### **横向关联**
- [[自旋锁]] → 纯用户态锁
- [[读写锁]] → 复杂同步场景
- [[RCU]] → 读多写少场景优化

---

## 📈 性能参考

### **futex 操作耗时**

| 操作 | 耗时 | 说明 |
|------|------|------|
| 用户态 CAS | ~20 纳秒 | 无竞争场景 |
| futex_wait 系统调用 | ~100 纳秒 | 用户态→内核态切换 |
| schedule() | ~1-5 微秒 | 内核调度 |
| context_switch | ~1-10 微秒 | 上下文切换 |
| 总阻塞开销 | ~1-15 微秒 | 不含实际阻塞时间 |

**对比**：
- volatile 读：~5 纳秒
- synchronized（无竞争）：~20 纳秒
- synchronized（有竞争）：~200-800 纳秒
- ReentrantLock：~150-600 纳秒

---

## 🎯 总结

**核心要点**：
1. futex 是用户态 CAS + 内核态阻塞的混合锁
2. 保护的是内核等待队列，而非 CPU 调度
3. 双重检查防止竞态条件
4. 无竞争时极快（纯用户态）
5. 有竞争时与传统 mutex 相当

**设计思想**：
- 用户态优先（能用户态解决就不进内核）
- 双重检查（防止竞态）
- 自旋 + 阻塞混合（短等待自旋，长等待阻塞）

**应用价值**：
- 理解并发库的底层实现
- 优化锁竞争场景
- 设计高性能同步原语

---

> **下一步探索**：[[ObjectMonitor 源码]]、[[pthread 库实现]]、[[自适应锁]]
