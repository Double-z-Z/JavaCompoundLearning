---
type: atomic-note
id: CONCEPT-CAS
created: 2026-05-16
updated: 2026-05-16
tags: [并发, java, jvm]
mastery: 20
related_emrg: [EMRG-并发编程]
related_goal: [GOAL-Java核心深化]
---

# CAS

## 一句话定义
CAS（Compare-And-Swap）是一种**硬件级原子指令**，通过"先比较内存值是否等于预期值，若相等则写入新值"的原子操作，实现无锁并发安全。


## 核心理解

### 三要素
| 要素 | 含义 | 示例 |
|------|------|------|
| 内存值 V | 当前内存地址的实际值 | `volatile int value` |
| 预期值 A | 线程上次读取到的值 | `oldValue` |
| 新值 B | 希望写入的值 | `newValue` |

**伪代码逻辑**：
```java
// 原子操作，不可中断
if (V == A) {
    V = B;   // 成功，返回 true
} else {
    return false;  // 失败，当前值已被其他线程修改
}
```

### 硬件支撑
- x86: `cmpxchg` 指令（Compare and Exchange）
- ARM: `LDREX` / `STREX` 指令对
- 现代 CPU 保证 cmpxchg 的原子性（单核总线锁或多核缓存锁）

### Java 实现路径
```
AtomicLong.incrementAndGet()
    ↓
Unsafe.getAndAddLong()
    ↓
`lock cmpxchg` 汇编指令（x86）
```

### ABA 问题
**问题描述**：值从 A → B → A，CAS 检测不到中间变化。

**解决方案**：
- `AtomicStampedReference`：增加版本号 stamp
- `AtomicMarkableReference`：增加布尔标记

```java
// 带版本号的 CAS
AtomicStampedReference<Integer> ref = 
    new AtomicStampedReference<>(100, 0);

int[] stampHolder = new int[1];
Integer value = ref.get(stampHolder);
ref.compareAndSet(value, 101, stampHolder[0], stampHolder[0] + 1);
```

### 与 volatile 的关系
- **volatile**：保证可见性和有序性，但不保证原子性
- **CAS**：保证原子性，但不保证可见性（通常配合 volatile 使用）
- **Atomic 类**：`volatile` 变量 + `CAS` 操作 = 线程安全的原子操作

```java
// AtomicLong 内部结构
public class AtomicLong extends Number implements java.io.Serializable {
    private volatile long value;  // volatile 保证可见
    
    public final long incrementAndGet() {
        return unsafe.getAndAddLong(this, valueOffset, 1L) + 1L;
        // 内部使用 CAS 保证原子
    }
}
```


## 关键关联

- [[futex]] — 关联原因：futex 的用户态快速路径依赖 CAS 做第一次检查，无竞争时避免系统调用
- [[LongAdder]] — 关联原因：Cell 数组的累加仍使用 CAS，只是通过分段降低了冲突概率
- [[双重检查模式]] — 关联原因：第一次用户态检查依赖 CAS 的原子性，防止竞态条件
- [[缓存行伪共享]] — 关联原因：高并发 CAS 场景下，多核竞争同一缓存行会导致性能暴跌，需用填充或 `@Contended` 隔离
- [[Parker]] — 关联原因：`_counter` 的修改使用 CAS 或原子操作，保证无锁状态下的状态安全


## 我的误区与疑问

- ❌ "CAS 完全不需要锁" → ✅ 实际在硬件层面使用了缓存锁/总线锁，只是比操作系统锁轻量
- ❌ "CAS 不会失败" → ✅ 高并发下 CAS 可能多次失败，导致自旋消耗 CPU（AtomicLong 的瓶颈）
- ❌ "AtomicLong 一定比 synchronized 快" → ✅ 低竞争时 CAS 快，高竞争时大量自旋反而不如重量级锁


## 代码与实践

### 基础 CAS 使用
```java
AtomicLong counter = new AtomicLong(0);

// 自旋直到成功
long expect, update;
do {
    expect = counter.get();
    update = expect + 1;
} while (!counter.compareAndSet(expect, update));
```

### 模拟 CAS（理解原理）
```java
public class SimulatedCAS {
    private volatile int value;
    
    public synchronized boolean compareAndSwap(int expected, int newValue) {
        if (value == expected) {
            value = newValue;
            return true;
        }
        return false;
    }
}
```


## 深入思考

💡 为什么 CAS 比 synchronized 快？什么情况下 synchronized 反而更优？
- CAS：无竞争时 ~20ns（纯用户态），高竞争时自旋消耗 CPU
- synchronized：无竞争时偏向锁 ~0ns（无操作），高竞争时进入内核态但线程让出 CPU 不消耗资源

💡 LongAdder 用分段降低 CAS 冲突，那如果分段数 > CPU 核心数，收益会下降吗？
- 哈希分散的线程可能少于核心数，空分段带来内存浪费
- 但汇总时的遍历成本也会上升


## 来源

- 项目：[[project-并发-test]]
- GOAL：[[GOAL-Java核心深化]]（退出条件明确要求"掌握 CAS 底层实现"）


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌱初识
- 更新记录：
  - 2026-05-16: mastery=20 (新建笔记，理解 CAS 三要素和基本使用)

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #并发
SORT mastery DESC
```
