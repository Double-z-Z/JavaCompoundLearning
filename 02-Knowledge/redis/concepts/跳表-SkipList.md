---
created: 2026-04-29
tags: [redis, 数据结构, 算法]
status: 🌿
mastery: 60
---

# 跳表（Skip List）

## 一句话定义
跳表是一种概率性平衡数据结构，通过为每个节点随机生成层数（索引），实现期望 O(log n) 的查询、插入和删除性能，同时支持高效的范围查询和排名查询。


## 核心理解

### 设计思想

跳表的核心洞察：**用概率换简单，用期望换高效**。

- 不需要复杂的旋转平衡（如红黑树）
- 通过随机层数达到期望平衡
- 实现简单（~500行代码 vs 红黑树~2000行）

### 结构组成

```
Level 3: [1] ----------------------------> [9]
Level 2: [1] ----------> [5] ------------> [9]
Level 1: [1] -> [3] -> [5] -> [7] -> [9]
Level 0: [1] -> [3] -> [5] -> [7] -> [9] -> [11] (底层链表，全数据)
```

每个节点包含：
- `ele`: 成员值（SDS字符串）
- `score`: 分数（排序依据）
- `backward`: 后退指针（支持倒序遍历）
- `level[]`: 柔性数组，每层有 `forward` 指针和 `span` 跨度

### 层数随机生成

```c
int zslRandomLevel(void) {
    int level = 1;
    // 每次有 25% 概率升级
    while ((random() & 0xFFFF) < (0.25 * 0xFFFF))
        level++;
    return level < 32 ? level : 32;
}
```

**概率分析**：
- 期望层数 E[level] = 1/(1-p) = 4/3 ≈ 1.33（当 p=0.25）
- 最大层数 O(log n)，以高概率成立

### 时间复杂度严格证明

**查询复杂度 O(log n)**：

1. 层数 L = O(log n)（证明：n·p^(k-1) ≈ 1 时，k ≈ log_{1/p} n）
2. 每层期望常数步（几何分布）
3. 总复杂度 = 层数 × 每层步数 = O(log n)

**插入/删除复杂度 O(log n)**：
- 查询位置 O(log n)
- 修改指针 O(1)（期望层数常数）

### 跨度（span）的作用

`span` 记录当前节点到 forward 节点之间的节点数，支持 O(log n) 排名查询：

```c
// 获取排名
rank = 0;
for (i = max_level-1; i >= 0; i--) {
    while (x->level[i].forward && x->level[i].forward->score < target) {
        rank += x->level[i].span;  // 累加跨度
        x = x->level[i].forward;
    }
}
```


## 关键关联

- [[红黑树]] - 关联原因：跳表是红黑树的替代方案，实现更简单
- [[二分查找]] - 关联原因：跳表的多层结构类似二分查找的思想
- [[几何分布]] - 关联原因：层数生成服从几何分布，期望分析的基础
- [[Redis-ZSet]] - 关联原因：Redis ZSet 使用跳表实现有序集合
- [[链表]] - 关联原因：跳表基于链表，通过多层索引加速


## 我的误区与疑问

- ❌ 误区：以为跳表层数是固定的（实际是随机生成）
- ❌ 误区：以为随机层数会导致最坏情况性能很差（实际概率极低）
- ❓ 疑问：为什么 Redis 选择 p=0.25 而不是其他值？（平衡内存和速度）
- ❓ 疑问：跳表能否实现无锁并发？（可以，比红黑树更容易）


## 代码与实践

```c
// 跳表节点结构（Redis）
typedef struct zskiplistNode {
    sds ele;                    // 成员
    double score;               // 分数
    struct zskiplistNode *backward;  // 后退指针
    struct zskiplistLevel {
        struct zskiplistNode *forward;   // 前进指针
        unsigned int span;               // 跨度
    } level[];                  // 柔性数组
} zskiplistNode;

// 查询操作
zskiplistNode *zslQuery(zskiplist *zsl, double score) {
    zskiplistNode *x = zsl->header;
    // 从最高层开始
    for (int i = zsl->level-1; i >= 0; i--) {
        // 在当前层前进，直到超过目标
        while (x->level[i].forward && 
               x->level[i].forward->score < score)
            x = x->level[i].forward;
    }
    // 下降到第0层
    x = x->level[0].forward;
    return (x && x->score == score) ? x : NULL;
}
```


## 深入思考

💡 跳表 vs 红黑树：为什么 Redis 选择跳表？（实现简单、范围查询友好、支持倒序）
💡 概率平衡的可靠性：随机层数如何保证 99.9% 的情况下性能不会退化？
💡 内存权衡：跳表的多级指针增加了内存开销，这个 trade-off 在什么场景下值得？
💡 并发优化：如何利用跳表的分层结构实现无锁并发？


## 来源
- 论文：William Pugh, "Skip Lists: A Probabilistic Alternative to Balanced Trees" (1990)
- 项目：[[netty-chatroom]]
- 对话：[[2026-04-29-Redis数据结构对话]]


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-04-29: mastery=60 (深入理解跳表结构、随机层数算法和复杂度证明)

### 建议下一步
1. 实现一个简单的跳表（Java/C）
2. 对比跳表和红黑树的性能测试
3. 研究无锁跳表的并发实现

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #数据结构
SORT mastery DESC
```
