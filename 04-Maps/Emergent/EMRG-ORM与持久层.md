---
type: emrg
id: EMRG-ORM与持久层
title: ORM与持久层网络
maturity: verified
created: 2026-05-30
updated: 2026-06-04
related_goals:
  - GOAL-ORM与缓存
subtopics:
  - 持久层选型
  - SQL映射与执行
  - MyBatis-Plus使用层
  - 会话与缓存
  - 事务与并发控制
  - 数据安全与架构演进
  - 分布式事务与主键策略
  - 分库分表策略
  - 连接池与调优
  - mybatis-sql-lab实战
---

# EMRG-ORM与持久层

> 成熟度: 🟢 verified

## 一句话定义

ORM与持久层是在Java应用与关系型数据库之间，通过SQL映射、会话管理、缓存策略和动态查询构造，实现数据访问层高效、可控、可维护的工程技术体系。

## 知识拓扑

[ORM与持久层核心认知]
  ├─ 持久层选型
  │   └─ [[Java持久层框架市场格局与选型]]
  ├─ SQL映射与执行
  │   ├─ [[MyBatis关联映射设计哲学]]
  │   ├─ [[MyBatis动态SQL架构与运行时拼接]]
  │   └─ [[MyBatis-Plus使用层总览]]
  ├─ 会话与缓存
  │   ├─ [[MyBatis一级缓存]]
  │   ├─ [[MyBatis二级缓存]]
  │   └─ [[MyBatis缓存机制与生产实践]]
  ├─ 事务与并发控制
  │   ├─ [[JDBC隔离级别与并发写冲突]]
  │   └─ [[Spring事务传播行为与JDBC实现]]
  └─ 数据安全与架构演进
      ├─ [[MyBatis-Plus多租户与数据权限区分]]
      ├─ [[生产环境数据权限实现方案]]
      └─ [[多数据源管理系统行业实践]]

## 关键缺口

### 已完成（2026-06-02 收束）
- [x] MyBatis源码级理解：SqlSession生命周期、Executor体系、插件机制拦截器链
- [x] MyBatis-Plus拦截器链深度分析：分页、多租户、动态表名的实现原理
- [x] Spring Data JPA/Hibernate核心机制 — 实体模型/脏检查/延迟加载对比
- [x] 连接池（HikariCP）原理与调优 — ConcurrentBag/ProxyConnection/后台任务
- [x] 分库分表下的持久层策略 — 分片键选择/ShardingSphere归并/异构索引
- [x] 分布式事务 — XA/Seata AT/TCC/Saga + 业务回避策略
- [x] 分布式主键 — Snowflake/号段模式/UUID v7 + 覆盖索引
- [x] MyBatis-Plus多租户与数据权限区分
- [x] 生产环境数据权限实现方案
- [x] 多数据源管理系统行业实践

### 子缺口（待补）
- [x] 分片配置实操 — ShardingSphere YAML/读写分离/docker-compose `2026-06-03`
- [x] 数据迁移 — 双写灰度切读/Scaling/全量增量 `2026-06-03`
- [x] 分库分表实践项目 — Phase 7.1-7.6 完成 + DDD重构 + AbstractRoutingDataSource `2026-06-04`
- [ ] 数据权限实践项目 — DataPermissionInterceptor 三级数据权限

## 项目实战

| 项目                  | 状态     | 关联笔记                         |
| ------------------- | ------ | ---------------------------- |
| [[mybatis-sql-lab]] | ✅ 完成   | 68 测试，6 个 Phase 全部完成         |
| 大数据分析平台（MyBatis）    | 🟡 进行中 | [[MyBatis缓存机制与生产实践]]         |
| 新冠核酸检测系统（MyBatis）   | 🟡 进行中 | [[MyBatis-Plus使用层总览]] |

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
