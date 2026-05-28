---
type: atomic-note
id: CONCEPT-Redis-Intset
created: 2026-05-28
tags: [redis, 数据结构, 内存管理]
status: 🌿
mastery: 60
related_emrg: [EMRG-Redis]
related_goal: [GOAL-Redis深入]
---

# Redis Intset（整数集合）

## 一句话定义

Intset 是 Set 类型小数据时的内部编码，将整数紧凑排放为有序数组，以二分查找实现 O(log n) 的 SISMEMBER，代价是插入时的 O(n) memmove。超过 512 元素自动切换为 hashtable。

## 核心理解

### 结构设计

```c
typedef struct intset {
    uint32_t encoding;  // INTSET_ENC_INT16 / INT32 / INT64
    uint32_t length;    // 元素个数
    int8_t contents[];  // 柔性数组，实际类型由 encoding 决定
} intset;
```

`contents[]` 是柔性数组，声明为 `int8_t` 但实际按 encoding 解释。所有元素共享同一个编码，不存在 per-element 变长——这和 Ziplist 的 prevlen 变长编码是两种不同的设计。

### 编码升级（Encoding Upgrade）

新插入元素的类型超过当前编码 → 全量升级并扩容：

```
[3, 5, 8]  encoding=INT16, 每个元素 2 字节
SADD 65536  → 超过 int16 范围 → 全数组升级为 INT32
[3, 5, 8, 65536]  每个元素 4 字节，之前的数据全部复制
```

升级不可逆——插入一个 `int64` 后，删掉它，数组仍是 INT64。**只升不降。**

### 操作对比

| 操作 | Intset | Hashtable |
|------|--------|-----------|
| SISMEMBER | O(log n) 二分查找 | O(1) 哈希 |
| SADD (头部/中部) | O(n) memmove + O(log n) 查找 | O(1) |
| SADD (尾部) | O(1) + O(log n) 查找 | O(1) |
| 内存 (500元素) | ~1KB 连续 | ~12KB 分散 |
| 缓存命中 | 高（连续内存，一个 cache line 32 元素） | 低（指针跳转） |

### 为什么 Intset 没有类似 QuickList 的"切分"版本

QuickList 能成功是因为 List 操作集中在两端（LPUSH/RPOP）。Set 的 SADD/SREM/SISMEMBER 在任意位置，切碎后 SISMEMBER 要搜遍 N 个片段——N × O(log m) 退化为 O(n)，不如直接 hashtable。

### 512 阈值的权衡

```
intset 插入 = 二分查找 O(log n) + memmove O(n)
n < 512:  memmove 在 CPU 缓存页内很快，内存优势盖过 O(n)
n ≥ 512:  memmove 代价开始凸显，换 hashtable
```

和 Ziplist → hashtable 的判断逻辑一致。分界线是工程经验，不是定理。

### Intset 与 Ziplist 的设计对比

| 维度 | Ziplist | Intset |
|------|---------|--------|
| 变长编码 | per-entry prevlen（1B 或 5B） | 全局 encoding（int16/32/64） |
| 升级规则 | prevlen 按需变长，仅影响后续 entry | encoding 只升不降，全量复制 |
| 排序 | 插入顺序 | 始终有序（为二分查找） |
| 连锁风险 | 连锁更新（prevlen 膨胀多米诺） | 编码升级（全量复制，无多米诺） |
| 大后编码 | 各自 hashtable / skiplist | 统一 hashtable |
| "打补丁"方案 | QuickList（分块锁风险） | 无——直接升 hashtable |

## 关键关联

- [[Redis-数据类型与编码]] - 关联原因：Intset 是 Set 小数据 + 全整数时的内部编码，和 Ziplist(小Hash/ZSet) 地位相同
- [[Redis-Ziplist设计]] - 关联原因：同为连续内存 + 变长设计思路（但变长策略不同），同有 O(n) memmove 代价
- [[Redis-QuickList设计]] - 关联原因：QuickList 给 Ziplist 打了分块补丁，Intset 不需要——操作模式的差异决定了优化方向
- [[Redis-SDS设计]] - 关联原因：三者共享柔性数组 + 连续内存的底层模式

## 我的误区与疑问

- ❌ 误区：以为 Intset 和 Ziplist 一样使用 per-element 变长编码（实际：全局 encoding，所有元素等长，二分查找才可能）
- ❓ 疑问：如果 Set 里 500 个 int16 + 1 个 int64，升级后 500 个 small int 都占 8 字节——为什么不设计成 hybrid（大值单独放，小值维持 int16）？

## 深入思考

💡 Intset 只升不降的 encoding——删掉那个大值后，空间浪费了。Redis 为什么不支持降级？增加降级的实现成本 vs 浪费的内存，哪个更大？
💡 如果 Set 全是 int16 但数量 600 个，转成 hashtable 后每个 dictEntry 比 int16 大 10 倍。这种"数量阈值触发编码切换"是否合理？是否有更精细的判断条件（如总字节数而非元素数）？

## 来源

- 对话：2026-05-28 Intset 苏格拉底式分析（从"Set 为什么需要有序"到"为什么没有切分方案"）
- 相关笔记：[[Redis-Ziplist设计]]、[[Redis-QuickList设计]]、[[Redis-数据类型与编码]]

---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-05-28: mastery=60（从"Set 为什么有序"切入，经二分查找→memmove→切分不可行→编码升级，完整覆盖 Intset 设计权衡）
