---
type: goal
description: 深入Redis：数据结构、持久化机制、高可用架构
driver: 晋升层
urgency: high
deadline: 2026-07-06
review_date: 2026-05-13
exit_conditions:
  - 掌握Redis 6种数据结构底层实现（SDS/QuickList/Ziplist等）
  - 深入理解RDB/AOF/混合持久化原理及选型
  - 掌握Redis Sentinel和Cluster原理
  - 能设计高可用缓存架构
evidence: 简历要求"熟悉Redis的使用，包括缓存策略、数据结构、持久化机制与高可用架构"，当前已有Cluster基础(85分)，但数据结构底层原理不足
status: in_progress
related_emrg:
  - EMRG-Redis
---

# GOAL: Redis深入

## 目标技能

| 技能 | 当前水平 | 目标水平 |
|------|---------|---------|
| Redis数据结构底层 | 🌿理解 (50) | 🍎应用 (75) |
| Redis持久化机制 | 🍎应用 (70) | 🌳掌握 (85) |
| Redis高可用架构 | 🍎应用 (85) | 🌳掌握 (90) |

## 学习路径

```
阶段1: 数据结构底层
├── SDS (简单动态字符串)
├── Ziplist / QuickList
├── SkipList
├── Dict (字典)
└── IntSet

阶段2: 持久化机制
├── RDB原理与配置
├── AOF原理与配置
├── 混合持久化
└── 数据恢复流程

阶段3: 高可用架构
├── 主从复制原理
├── Sentinel选举机制
├── Cluster数据分片
└── Gossip协议
```

## 简历要求回顾

> 熟悉Redis的使用，包括缓存策略、数据结构（如字符串、哈希、列表、集合等）的应用，以及Redis的持久化机制与高可用架构。

## 已完成学习

- [x] Redis Cluster模式 (85分)
- [x] Redis主从复制 (70分)
- [x] Redis持久化机制 (70分)
- [x] Redis哨兵模式 (70分)
- [ ] 数据结构底层实现
- [ ] 缓存策略最佳实践

## 关联项目

- [[ansible-redis-cluster]] - Redis Cluster部署
