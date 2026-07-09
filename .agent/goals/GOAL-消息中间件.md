---
type: goal
status: active
driver: promotion
urgency: medium
deadline: 2026-07-29
review_date: 2026-07-08
incident_ref: [[goal-java简历]]
exit_conditions:
  - 理解Kafka高吞吐量原理（页缓存、零拷贝、顺序写）
  - 掌握RabbitMQ交换机、队列、绑定机制
  - 理解消息可靠性保证（确认机制、重试、死信）
gap_analysis:
  - EMRG现状: 无消息队列相关内容
  - GOAL目标: 理解消息中间件原理，能进行技术选型
  - 缺口: 项目使用过但原理不清晰
related_emrg: []
created: 2026-05-06
updated: 2026-05-29
---

# GOAL: 消息中间件

## 驱动信息

| 字段 | 值 |
|------|-----|
| driver | promotion（晋升层） |
| urgency | medium |
| deadline | 2026-09-06 |
| incident_ref | [[goal-java简历]] - 简历要求"熟悉消息中间件的底层原理" |

## 退出条件

- [ ] Kafka高吞吐量原理（页缓存、零拷贝、顺序写）
- [ ] RabbitMQ交换机、队列、绑定机制
- [ ] 消息可靠性保证

## 目标技能

| 技能 | 当前水平 | 目标水平 |
|------|---------|---------|
| Kafka原理 | 🌱 0 | 🌿 60 |
| RabbitMQ原理 | 🌱 0 | 🌿 60 |

## 学习路径

```
Kafka: 高吞吐量原理 → 分区与副本 → 消费者组
RabbitMQ: 交换机路由 → 队列机制 → 确认与重试
```

## 关联项目

- 大数据分析平台（Kafka）
- 新冠核酸检测系统（RabbitMQ）
- 市监综合监管系统（RabbitMQ）

---

## 更新记录

| 日期 | 更新内容 | 操作者 |
|------|---------|--------|
| 2026-05-06 | 重建为工程化GOAL | AI |
