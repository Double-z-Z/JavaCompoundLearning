---
type: atomic-note
id: CONCEPT-count-min-sketch
created: 2026-05-02
tags:
  - cache
  - 算法
  - probabilistic-data-structure
mastery: 50
source: "[[03-Practice/reflections/2026-05-02-本地缓存-Caffeine-对话.md]]"
related_emrg: [[EMRG-Sentinel]]
related_goal:
  - GOAL-ORM与缓存
---

# Count-Min Sketch 概率计数器

## 一句话定义
Count-Min Sketch 是一种概率数据结构，用多个哈希函数将 key 映射到计数器数组，通过取最小值估算频率，以可控误差换取极大空间压缩。


## 核心理解

### 结构设计
```
        哈希1    哈希2    哈希3    哈希4
         ↓        ↓        ↓        ↓
      ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐
      │  3  │  │  0  │  │  5  │  │  2  │   ← 计数器数组
      └─────┘  └─────┘  └─────┘  └─────┘
        ↑        ↑        ↑        ↑
       key "user:123" 经过4个哈希函数，定位到4个位置
```

**操作：**
- **增加**：4个位置都 +1
- **查询**：取4个位置的最小值

**为什么取最小值？**
- 哈希冲突会导致计数偏高
- 最小值最接近真实频率（只要有一个位置没冲突）

### 空间效率
| key 大小 | HashMap | Count-Min Sketch |
|---------|---------|------------------|
| 20字节 × 100万 | ~40MB | 4KB（1024×4bit）|
| 压缩比 | 1× | ~10000× |

### 误差控制
- 多哈希函数降低冲突概率
- 4-bit 计数器最大15，定期衰减防止饱和


## 关键关联

- [[布隆过滤器]] - 关联原因：同属于概率数据结构，用哈希+数组实现空间压缩，布隆判断存在性，CMS估算频率
- [[LongAdder]] - 关联原因：都是分段降低竞争的思想，LongAdder 分段计数，CMS 多哈希分散冲突
- [[哈希表]] - 关联原因：传统哈希表存 key-value，CMS 只存"指纹"计数，不存原始 key


## 我的误区与疑问

- ❌ 误区：曾认为"取模会丢失精度导致无法比较"，实际上比较的是相对顺序，不是绝对值
- ❌ 误区：曾认为"计数器饱和后缓存彻底失效"，实际上衰减机制会拉开差距
- ❓ 疑问：4-bit 计数器在极端场景（所有 key 访问都>15）下如何保持区分度？


## 代码与实践

```java
// Caffeine 内部使用，用户不直接操作
// 计数器大小可配置，默认4-bit

// 论文中的参数示例：
// 1K-item cache + 9K-item frequency histogram
// 3-bit counters + Doorkeeper = 可计数到 8+1=9
```


## 深入思考

💡 Count-Min Sketch 的误差是上偏的（总是>=真实值），在某些场景（如限流计数）是否需要下偏估计？

💡 如果业务需要精确计数（如计费），CMS 是否适用？什么场景下概率估算可接受？


## 来源
- 对话：[[2026-05-02-本地缓存-Caffeine-对话]]
- 论文：[TinyLFU: A Highly Efficient Cache Admission Policy](http://arxiv.org/pdf/1512.00727)


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-05-02: mastery=50 (理解原理、空间效率、误差来源，但实现细节需进一步验证)

### 建议下一步
1. 阅读 Caffeine 的 FrequencySketch 源码实现
2. 实现一个简单的 Count-Min Sketch 验证误差特性
3. 对比其他概率数据结构（HyperLogLog、布隆过滤器）的适用场景

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #cache OR #算法 OR #probabilistic-data-structure
SORT mastery DESC
```
