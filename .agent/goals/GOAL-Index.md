---
type: goal_index
description: GOAL层总览 - 技术负债MOC索引
created: 2026-05-06
updated: 2026-05-06
---

# GOAL-Index: 技术负债层索引

> 本目录包含基于简历的技术负债GOAL文件
> 每个GOAL对应简历中的一个技能要求
>
> **工程化要求**：每个GOAL必须包含 driver/deadline/incident_ref/exit_conditions/gap_analysis

---

## GOAL文件列表

| 优先级 | 状态 | GOAL文件 | driver | deadline | 关联EMRG |
|--------|------|---------|--------|----------|---------|
| P0 | active | [[GOAL-Java核心深化]] | promotion | 2026-08-06 | EMRG-并发/EMRG-NIO |
| P0 | active | [[GOAL-Redis深入]] | promotion | 2026-07-06 | EMRG-Redis |
| P0 | active | [[GOAL-SpringCloud微服务]] | promotion | 2026-08-06 | - |
| P1 | active | [[GOAL-消息中间件]] | promotion | 2026-09-06 | - |
| P1 | active | [[GOAL-ORM与缓存]] | promotion | 2026-09-06 | - |
| P1 | active | [[GOAL-数据库性能优化]] | promotion | 2026-10-06 | - |
| P1 | active | [[GOAL-容器编排]] | promotion | 2026-10-06 | - |
| P2 | active | [[GOAL-Linux系统管理]] | promotion | 2026-12-06 | - |

---

## P0 GOAL详情

### GOAL-Java核心深化
- **状态**: active
- **驱动**: promotion（简历要求）
- **关键缺口**: epoll机制、线程池原理、反射与注解
- **退出条件**: 手写线程池 + epoll理解 + 2个项目实战

### GOAL-Redis深入
- **状态**: active
- **驱动**: promotion（简历要求）
- **关键缺口**: 数据结构底层实现
- **退出条件**: 6种数据结构底层 + 持久化原理 + 高可用架构

### GOAL-SpringCloud微服务
- **状态**: active
- **驱动**: promotion（简历要求）
- **关键缺口**: 完全空白
- **退出条件**: Spring Boot原理 + Cloud组件 + 微服务项目

---

## 驱动层分布

| 驱动层 | 数量 | 说明 |
|--------|------|------|
| survival（生存层） | 0 | 工作痛点触发 |
| promotion（晋升层） | 8 | 简历要求触发 |
| decision（决策层） | 0 | 架构决策触发 |

---

## 评审周期

- **周Review**: 每周五检查进度
- **GOAL Review Date**: 各GOAL单独设定的review日期
- **里程碑Review**: 每个GOAL达成后触发评估

---

## 关联

- 涌现层: [[EMRG-并发编程]], [[EMRG-NIO网络编程]], [[EMRG-Redis]]
- 映射层: [[META-Index-知识模型]], [[META-Gap-诊断矩阵]]
