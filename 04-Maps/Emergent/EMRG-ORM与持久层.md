---
type: emrg
id: EMRG-ORM与持久层
title: ORM与持久层网络
maturity: verified
created: 2026-05-30
updated: 2026-05-30
related_goals:
  - GOAL-ORM与缓存
subtopics:
  - 框架选型与市场格局
  - MyBatis核心机制
  - MyBatis-Plus增强
  - 缓存策略与生产实践
  - SQL映射与设计哲学
  - mybatis-sql-lab实战
---

# EMRG-ORM与持久层

> 成熟度: 🟢 verified

## 一句话定义

ORM与持久层是在Java应用与关系型数据库之间，通过SQL映射、会话管理、缓存策略和动态查询构造，实现数据访问层高效、可控、可维护的工程技术体系。

## 知识拓扑

[ORM与持久层核心认知]
  ├─ 框架选型与市场格局
  │   └─ [[Java持久层框架市场格局与选型]]
  ├─ MyBatis核心机制
  │   ├─ [[MyBatis一级缓存]]
  │   ├─ [[MyBatis二级缓存]]
  │   ├─ [[MyBatis动态SQL架构与运行时拼接]]
  │   ├─ [[MyBatis关联映射设计哲学]]
  │   └─ [[MyBatis缓存机制与生产实践]]
  └─ MyBatis-Plus增强
      └─ [[MyBatis与MyBatis-Plus核心差异]]

## 关键缺口（待补充）

- [ ] MyBatis源码级理解：SqlSession生命周期、Executor体系、插件机制拦截器链
- [ ] MyBatis-Plus拦截器链深度分析：分页、多租户、动态表名的实现原理
- [ ] Spring Data JPA/Hibernate核心机制（与MyBatis形成对比认知）
- [ ] 连接池（HikariCP）原理与调优
- [ ] 分库分表下的持久层策略（ShardingSphere等）

## 项目实战

| 项目                  | 状态     | 关联笔记                         |
| ------------------- | ------ | ---------------------------- |
| [[mybatis-sql-lab]] | ✅ 完成   | 68 测试，6 个 Phase 全部完成         |
| 大数据分析平台（MyBatis）    | 🟡 进行中 | [[MyBatis缓存机制与生产实践]]         |
| 新冠核酸检测系统（MyBatis）   | 🟡 进行中 | [[MyBatis与MyBatis-Plus核心差异]] |

### mybatis-sql-lab 实战成果

| Phase | 主题 | 掌握度 |
|------|------|--------|
| Phase 1-3 | SQL 映射原理 | 🍎 70 |
| Phase 2 | 动态 SQL | 🍎 70 |
| Phase 3 | ResultMap | 🌿 60 |
| Phase 4 | MyBatis Plus 功能层 | 🍎 65 |
| Phase 5 | MyBatis Plus 原理层 | 🌿 55 |
| Phase 6 | QueryWrapper 能力边界 | 🌿 55 |

## 关联领域

- [[EMRG-Cache]] — Service层缓存策略（Caffeine/Redis）是MyBatis原生缓存的生产替代方案
- [[EMRG-Redis]] — Redis作为L2分布式缓存层
- [[EMRG-SpringCloud微服务]] — Spring生态中的持久层集成与选型
- [[EMRG-分布式策略]] — 分布式环境下的数据一致性、关联查询策略

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
      p.mastery ?? '-',
      (p.mastery >= 60) ? '🟢' : '🟡'
    ])
);
```

### 边界声明

#### 边缘关联（链接但不纳入）
- [[Spring配置管理]] → 归属 [[EMRG-SpringCloud微服务]]，Spring生态配置与持久层无直接关联
- [[Redis缓存策略]] → 归属 [[EMRG-Redis]]，Redis是缓存实现技术而非ORM专属
- [[CPU三级缓存类比]] → 归属 [[EMRG-Cache]]，硬件缓存与软件缓存的类比知识

#### 跨界枢纽（被多个 EMRG 引用）
- [[MyBatis缓存机制与生产实践]] — 同时关联 [[EMRG-ORM与持久层]] 和 [[EMRG-Cache]]（Service层缓存替代方案）
- [[MyBatis二级缓存]] — 同时关联 [[EMRG-ORM与持久层]] 和 [[EMRG-Cache]]（二级缓存的替代架构）

### 涌现历史

- **2026-05-30**: 因用户主动整理创建。从Kimi对话提取5篇原子笔记，结合仓库中已有的2篇缓存笔记（MyBatis一级缓存、MyBatis二级缓存），统一归并为EMRG-ORM与持久层。

### 成熟度说明

mybatis-sql-lab 项目已完成 68 测试、6 个 Phase，覆盖 SQL 映射原理、动态 SQL、ResultMap、MyBatis Plus 功能层与原理层、QueryWrapper 能力边界。7 篇原子笔记中，MyBatis一级缓存(65)/二级缓存(55)已有项目实战基础，其余 5 篇为对话整理所得，待项目验证后升级。

### 检查点

- [ ] 子主题数: 5（健康）
- [ ] 最后更新: 2026-05-30（超过 90 天则触发归档检查）
