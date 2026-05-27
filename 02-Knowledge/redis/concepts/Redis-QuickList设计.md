---
type: atomic-note
id: CONCEPT-Redis-QuickList
created: 2026-05-27
updated: 2026-05-27
tags: [redis, 数据结构, 内存管理]
status: 🌿
mastery: 70
related_emrg: [EMRG-Redis]
related_goal: [GOAL-Redis深入]
---

# Redis QuickList（快速列表）

## 一句话定义

QuickList 是 Redis 3.2+ 中 List 类型的唯一内部编码，将链表和 Ziplist 组合：多个 4KB 的 Ziplist 节点用双向链表串联，把连锁更新的爆炸半径锁在单个节点内，同时在中间节点应用 LZF 压缩以节省内存。

## 核心理解

### 结构：链表 + Ziplist

```
head → [ziplist A] ↔ [ziplist B(压缩)] ↔ [ziplist C(压缩)] ↔ [ziplist D] ← tail
           ↑                                               ↑
      两端不压缩（高频读写）                         中间 LZF 压缩（低频访问）
```

每个节点是一个 `quicklistNode`，内嵌一个 Ziplist。节点的 Ziplist 大小由 `list-max-ziplist-size` 控制。

### 三个配置参数

| 配置 | 默认 | 含义 |
|------|------|------|
| `list-max-ziplist-size` | -2 (8KB) | 单个 Ziplist 节点多大 |
| `list-compress-depth` | 0 (不压缩) | 两端几个节点不压缩 |

**list-max-ziplist-size 取值**：

| 值 | 含义 |
|----|------|
| -1 | 4KB（一个 OS 内存页） |
| -2 | 8KB（默认） |
| -3 | 16KB |
| -4 | 32KB |
| -5 | 64KB |
| 正数 | 精确控制 entry 数量 |

### 为什么是 4KB？

Linux 内存页大小 = 4KB。一个 Ziplist 节点不超过一页，保证节点内数据不会跨页导致额外的缺页中断。一次遍历最多触发一次缺页，延迟可控。

Redis 默认选了 8KB（-2），是 4KB 和内存效率之间的折中。

### LZF 压缩：中间节点省内存

List 的读写 90% 发生在两端（LPUSH/RPUSH/LPOP/RPOP）。中间节点很少被访问，压了也不影响性能。LZF 是轻量压缩算法，压缩率不如 gzip 但速度极快，不拖累 Redis 主线程。

`list-compress-depth` 控制两端不压缩的节点数：设为 1 则头尾各 1 个不压缩，其余全部 LZF 压缩。

### 连锁更新的解决方案

QuickList 没有消除连锁更新——当插入触发 prevlen 膨胀时，**仍然会在单个 Ziplist 节点内发生级联**。但它把爆炸半径从"整个数据结构"缩小到"一个 4KB 节点"：

```
纯 Ziplist:  连锁更新 → 整个 List 全量重写 → 延迟不可控
QuickList:   连锁更新 → 一个 4KB 节点内重写 → 延迟可预测
```

## 演进路径

```
Redis 3.0:  小List→Ziplist, 大List→LinkedList(双端链表)
            ↓ 问题：LinkedList 每个节点存一个元素，指针开销大
Redis 3.2:  统一为 QuickList
            ↓ 核心洞察：不用在"纯紧凑"和"纯灵活"之间二选一，各取所长
QuickList = Ziplist(紧凑) + LinkedList(灵活) + LZF(省内存)
```

## 关键关联

- [[Redis-Ziplist设计]] - 关联原因：QuickList 的每个节点就是一个 Ziplist，连锁更新问题被限制在单节点内
- [[Redis-SDS设计]] - 关联原因：三者共享"连续内存 + 变长编码"的设计理念
- [[Redis-数据类型与编码]] - 关联原因：3.2+ 后 List 唯一编码，替代了旧的 linkedlist/ziplist 双编码
- [[操作系统-内存页]] - 关联原因：4KB 阈值直接对应 Linux page size
- [[Java-LinkedList]] - 关联原因：同样是双向链表，但 Java 每个节点一个元素，QuickList 每个节点一批元素

## 深入思考

💡 QuickList 节点跨页的问题：如果 Ziplist 大小设为 8KB 或更大，跨两个页，一次遍历可能两次缺页。Redis 默认 8KB 是对"少缺页"和"少节点数"的折中——你能接受这个折中吗？
💡 为什么 ZSet 没有引入类似 QuickList 的结构？（提示：ZSet 大数据的编码是 skiplist，它遇到连锁更新吗？）

## 来源

- 对话：2026-05-27 Ziplist → QuickList 苏格拉底式对话
- 相关笔记：[[Redis-Ziplist设计]]、[[Redis-SDS设计]]、[[Redis-数据类型与编码]]

---

## 更新记录

| 日期 | 操作 |
|------|------|
| 2026-05-27 | 创建，mastery=70（从 Ziplist 对话自然延伸，理解了分块策略、OS 页对齐、LZF 压缩的设计动机） |
