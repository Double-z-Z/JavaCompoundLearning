---
type: emrg
id: EMRG-Redis
title: Redis知识网络
maturity: emerging
created: 2026-04-28
updated: 2026-05-15
related_goals: [GOAL-Redis深入,GOAL-Java核心深化]
subtopics:
  - 数据结构底层
  - 高可用架构
  - 性能优化与压测
  - 分布式与扩展
  - 实战场景与策略
---

# Redis 知识网络

> 成熟度: 🟡 emerging

## 一句话定义

Redis 不仅是内存缓存，更是一套围绕高性能数据结构、高可用分布式架构和可观测性能优化组成的完整数据存储与计算系统。

## 知识拓扑


Redis 核心认知
  ├─ 数据结构底层
  │   ├─ [[Redis-数据类型与编码]]
  │   │   └─ 关联 [[跳表-SkipList]]
  │   └─ [[跳表-SkipList]]
  ├─ 通信与协议
  │   └─ [[RESP协议]]
  ├─ 高可用架构
  │   ├─ [[Redis-持久化]]
  │   ├─ [[Redis-主从复制]]
  │   └─ [[Redis-哨兵模式]]
  │       └─ 关联 [[EMRG-Sentinel-核心机制]]
  ├─ 分布式与扩展
  │   ├─ [[Redis-Cluster模式]]
  │   ├─ [[Redis集群]]
  │   └─ [[数据倾斜解决方案]]
  └─ 性能与实战
      ├─ [[redis-benchmark]]
      ├─ [[Redis-性能压测-分层排除法]]
      └─ [[Redis Pipeline在秒杀中的应用]]
          └─ 关联 [[EMRG-Sentinel-核心机制]]


## 关键缺口（待补充）

- [ ] Redis 单线程事件循环模型（待实际学习后创建）
- [ ] SDS / Ziplist / QuickList 源码级理解
- [ ] 系统化的缓存策略文档（淘汰策略、预热、穿透/击穿/雪崩）

## 项目实战

| 项目 | 状态 | 关联笔记 |
|------|------|---------|
| [[redis-counter-service]] | ✅ 完成 | Lua 脚本、Redis Cluster、压测 |
| [[redis-counter-service-webflux]] | ⏳ 进行中 | WebFlux、Sentinel、MQ 削峰、数据倾斜 |
| [[ansible-redis-cluster]] | ✅ 完成 | Redis Cluster 部署、故障转移、Ansible 自动化 |

## 关联领域

- [[EMRG-Sentinel-核心机制]] — 秒杀场景的熔断限流保护
- [[EMRG-Sentinel-高级特性与生态]] — WebFlux 集成与响应式流控制
- [[EMRG-NIO网络编程]] — Netty 与 Redis 通信的底层机制

---

## 🤖 AI 工作区（以下由 Dataview 自动维护，请勿手动编辑）

### 核心成员

```dataviewjs
const emrgId = dv.current().id;
dv.table(
  ["笔记", "mastery", "验证状态"],
  dv.pages()
    .where(p => p.related_emrg && p.related_emrg.includes(emrgId))
    .sort(p => p.mastery, 'desc')
    .map(p => [
      p.file.link,
      p.mastery ?? ' ',
      (p.mastery >= 60) ? '🟢' : '🟡'
    ])
);
```

### 边界声明

#### 边缘关联（链接但不纳入）
- [[秒杀超卖与库存一致性]] → 归属架构/秒杀领域，Redis 是落地技术之一

#### 跨界枢纽（被多个 EMRG 引用）
- 暂无

### 涌现历史

- **2026-04-28**: 因密度溢出创建（涉及 5 篇笔记，3 条链接）
- **2026-05-14**: 纳入所有已学习笔记（12 篇），补充项目实战链接，更新核心成员表格
- **2026-05-15**: 按 EMRG 模板标准化结构，更新成熟度为 emerging，补充边界声明与知识拓扑

### 成熟度说明

7/11 篇笔记 mastery ≥ 60（verified），覆盖持久化、主从复制、哨兵、Cluster、性能压测、跳表、RESP协议等核心主题。剩余4篇待强化：redis-benchmark、Pipeline应用、Redis集群、数据类型与编码。

### 检查点

- [ ] 子主题数: 5（超过 7 则触发裂变）
- [ ] 最后更新: 2026-05-15（超过 90 天则触发归档检查）
