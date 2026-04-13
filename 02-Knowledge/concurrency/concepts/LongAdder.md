---
created: 2026-04-12
tags: [concurrency, atomic, performance]
status: 🌿
source: [[longadder-design]]
---

# LongAdder

## 一句话定义
LongAdder是JDK8引入的高并发计数器，通过**分段思想**将单个计数器拆分为多个Cell，不同线程操作不同分段，从而减少CAS冲突。


## 核心机制
1. **base字段**：低并发时直接使用，避免创建Cell数组的开销
2. **Cell数组**：高并发时通过哈希分散线程到不同分段
3. **@Contended注解**：每个Cell独占缓存行，避免伪共享


## 为什么重要
- 高并发场景下吞吐量比AtomicLong提升5-10倍
- 体现了"空间换时间"的经典设计思想
- 与ConcurrentHashMap的分段锁设计异曲同工


## 与已学知识的关联
- [[CAS]] → LongAdder仍然使用CAS，只是减少了冲突概率
- [[分段锁思想]] → 与ConcurrentHashMap相同的分而治之策略
- [[缓存行伪共享]] → @Contended注解解决的核心问题
- [[futex]] → 减少阻塞是两者共同的设计目标


## 常见误区
- ❌ "LongAdder不用CAS" → 实际仍然使用CAS，只是分散了冲突
- ❌ "LongAdder总是更快" → 低并发时AtomicLong更优（无分段开销）
- ❌ "sum()返回精确值" → 实际是遍历累加的近似值，非强一致


## 代码示例
```java
// 高并发计数场景
LongAdder counter = new LongAdder();
counter.increment();  // 分散到不同Cell
long sum = counter.sum();  // 遍历累加所有Cell
```


## 深入问题（苏格拉底式）
💡 为什么Cell数组长度必须是2的幂？这和HashMap的设计有什么联系？


## 来源
- 项目：[[project-concurrency-test]]
- 原文档：[[longadder-design]]


---
- [x] 是否建立了至少2个知识关联？
- [x] 是否记录了可能的误区？
- [x] 是否留下了深入思考的问题？
