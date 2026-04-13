---
created: 2026-04-12
tags: [concurrency, os, lock]
status: 🌿
source: [[futex-deep-dive]]
---

# futex

## 一句话定义
futex（Fast User-space muTEX）是一种**用户态CAS + 内核态阻塞**的混合锁机制，仅在真正需要阻塞时才进入内核态。


## 核心机制
1. **双重检查模式**：用户态先CAS检查，失败后再进入内核二次检查
2. **用户态优先**：无竞争时纯用户态操作（~20纳秒），有竞争时才系统调用
3. **内核等待队列**：通过哈希表管理阻塞线程的 futex_q 结构


## 为什么重要
- 比传统mutex快5-10倍（无竞争场景）
- Java的 `LockSupport.park()` 底层通过pthread调用futex
- 理解synchronized、ReentrantLock的底层基础


## 与已学知识的关联
- [[CAS]] → futex的用户态部分依赖原子指令
- [[线程状态]] → futex_wait导致线程从RUNNABLE变为WAITING
- [[ObjectMonitor]] → synchronized底层通过pthread间接使用futex
- [[用户态与内核态]] → 理解系统调用开销


## 常见误区
- ❌ "futex保护CPU调度" → 实际保护的是**内核等待队列**
- ❌ "futex消除了CAS" → 实际**依赖CAS**做第一次检查
- ❌ "futex总是更快" → 高竞争场景下优势不明显


## 代码示例
```c
// 用户态部分
int futex_lock(int *futex_var) {
    // 第一次检查：用户态CAS
    if (atomic_compare_and_swap(futex_var, 0, 1) == 0) {
        return 0;  // 成功，无需系统调用
    }
    // 失败才进入内核
    return futex_wait(futex_var, 1);
}
```


## 深入问题（苏格拉底式）
💡 为什么需要"双重检查"？如果只检查一次会有什么问题？


## 来源
- 项目：[[project-concurrency-test]]
- 对话：[[2026-04-11-futex-dialogue]]
- 原文档：[[futex-deep-dive]]


---
- [x] 是否建立了至少2个知识关联？
- [x] 是否记录了可能的误区？
- [x] 是否留下了深入思考的问题？
