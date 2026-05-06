---
type: goal
description: 深入消息中间件：Kafka/RabbitMQ/RocketMQ原理
driver: 晋升层
urgency: medium
deadline: 2026-09-06
review_date: 2026-06-01
exit_conditions:
  - 理解Kafka高吞吐量原理（页缓存、零拷贝、顺序写）
  - 掌握RabbitMQ交换机、队列、绑定机制
  - 理解消息可靠性保证（确认机制、重试、死信）
  - 掌握消息顺序性保障方案
evidence: 简历要求"熟悉消息中间件的使用与底层原理"，项目中有使用经验但原理不清晰
status: pending
related_emrg: []
---

# GOAL: 消息中间件

## 目标技能

| 技能 | 当前水平 | 目标水平 |
|------|---------|---------|
| Kafka原理 | 项目使用 | 🌿理解 (60) |
| RabbitMQ原理 | 项目使用 | 🌿理解 (60) |
| 消息队列选型 | 有经验 | 🍎应用 (70) |

## 学习路径

```
阶段1: Kafka深入
├── 高吞吐量原理
│   ├── 页缓存(Page Cache)
│   ├── 零拷贝(Zero-Copy)
│   └── 顺序写磁盘
├── 分区与副本
├── 消费者组与Rebalance
└── Exactly-Once语义

阶段2: RabbitMQ深入
├── 交换机类型与路由
├── 队列属性与消息分发
├── 确认机制与重试
├── 死信队列
└── 集群架构

阶段3: 消息队列对比
├── Kafka vs RabbitMQ vs RocketMQ
├── 选型决策框架
└── 常见问题处理
```

## 简历要求回顾

> 熟悉消息中间件（如Kafka、RabbitMQ、RocketMQ）的使用与底层原理，包括消息队列的高可用性、消息持久化、消息消费模型等。

## 关联项目

- 大数据分析平台（Kafka）
- 新冠核酸检测系统（RabbitMQ）
- 市监综合监管系统（RabbitMQ）
