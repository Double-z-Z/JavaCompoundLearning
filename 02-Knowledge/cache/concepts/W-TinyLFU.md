---
type: atomic-note
id: CONCEPT-w-tinylfu
created: 2026-05-02
tags: [cache, caffeine]
mastery: 55
source: "[[03-Practice/reflections/2026-05-02-本地缓存-Caffeine-对话.md]]"
related_emrg: [[EMRG-Sentinel]]
related_goal: [GOAL-ORM与缓存]
---

# W-TinyLFU 缓存淘汰算法

## 一句话定义
W-TinyLFU 是一种缓存淘汰策略，通过 Window Cache（5% LRU）保护新数据，Main Cache（95% W-TinyLFU）保留长期热点，实现频率与近期性的平衡。


## 核心理解

### 双区设计
```
┌─────────────────────────────────────────┐
│  Window Cache (5%)  │  Main Cache (95%) │
│      LRU 算法       │   W-TinyLFU 算法   │
│   给新数据机会      │   保留真正热点     │
└─────────────────────────────────────────┘
```

**晋升机制：**
1. 新数据先进入 Window Cache
2. Window 满时，LRU 尾部被淘汰
3. 被淘汰者与 Main Cache 候选者 PK 频率
4. 频率高者留下/晋升，败者淘汰

### TinyLFU 频率统计
- 使用 Count-Min Sketch 概率计数
- 4-bit 计数器（最大15），定期衰减（halving）
- Doorkeeper 过滤低频首次访问

### 衰减机制
```
定期执行 Reset：
    1. 清空 Doorkeeper
    2. 所有计数器 >>= 1（减半）
```
实现时间敏感性：老热点频率衰减，新热点有机会胜出。


## 关键关联

- [[Count-Min-Sketch]] - 关联原因：W-TinyLFU 使用 Count-Min Sketch 进行概率频率统计，是空间效率与精度权衡的经典案例
- [[LRU]] - 关联原因：Window Cache 使用纯 LRU，与 Main Cache 的 LFU 形成互补，解决突发热点问题
- [[NIO-Selector]] - 关联原因：Window/Main 双区设计类似于 NIO 的 Boss/Worker 分工，新连接先注册到 Boss，再分发到 Worker
- [[JVM分代垃圾回收]] - 关联原因：Window=新生代（快速淘汰/晋升），Main=老年代（长期存活），同样的分代思想


## 我的误区与疑问

- ❌ 误区：曾认为"频率统计饱和后缓存彻底失效"，实际上衰减机制会拉开差距
- ❌ 误区：曾混淆"淘汰"（空间管理）与"失效"（时间管理）的概念
- ❓ 疑问：极端高并发下 Window Cache 容量不足，新热点无法进入，是否有动态扩容机制？


## 代码与实践

```java
// Caffeine 配置 W-TinyLFU（默认）
Caffeine.newBuilder()
    .maximumSize(10000)
    .build();

// 显式指定（通常不需要）
Caffeine.newBuilder()
    .maximumSize(10000)
    .executor(executor)  // 自定义刷新线程池
    .build();
```


## 深入思考

💡 W-TinyLFU 的 Window 比例（默认5%）是否可配置？不同业务场景（突发热点 vs 稳定热点）最优比例是否不同？

💡 如果访问模式完全随机（无热点），W-TinyLFU 是否退化为 LRU？此时性能 overhead 是否值得？


## 来源
- 项目：[[netty-chatroom]]（可应用缓存优化）
- 对话：[[2026-05-02-本地缓存-Caffeine-对话]]
- 论文：[TinyLFU: A Highly Efficient Cache Admission Policy](http://arxiv.org/pdf/1512.00727)


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-05-02: mastery=55 (深入理解算法原理，能解释 Window/Main 双区设计、晋升机制、衰减策略)

### 建议下一步
1. 阅读 Caffeine 源码，理解具体实现细节
2. 在实际项目中应用，观察命中率变化
3. 对比纯 LRU 和 W-TinyLFU 在不同访问模式下的表现

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #cache
SORT mastery DESC
```
