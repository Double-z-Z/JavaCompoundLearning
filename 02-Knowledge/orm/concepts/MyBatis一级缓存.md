---
title: MyBatis一级缓存
date: 2026-05-30
tags:
  - orm
  - mybatis
  - cache
mastery: 65
status: budding
related_emrg: [EMRG-ORM与持久层]
related_goals:
  - "[[GOAL-ORM与缓存]]"
related_concepts:
  - "[[MyBatis二级缓存]]"
---

# MyBatis一级缓存

## 核心机制

MyBatis 一级缓存是 **SqlSession 级别**的查询缓存，生命周期与 SqlSession 绑定。

### 缓存结构

- 内部使用 `HashMap` 存储
- Key: `statementId + SQL参数 + rowBounds（分页偏移）`
- 只缓存 **SELECT** 语句的结果
- INSERT/UPDATE/DELETE 不缓存，且执行后**清空整个缓存**

### 失效策略

> [!warning] 全清策略
> 任何写操作（update/insert/delete）执行后，**整个 namespace 的缓存全部清空**，不做细粒度的行级失效追踪。

这是 MyBatis "工具而非框架"定位的体现——不代替开发者做精细化的缓存管理。

## SqlSession 的本质

SqlSession 是**一次数据库交互的会话窗口**：

- 底层绑定一个 JDBC Connection（TCP 长连接）
- 一个连接可执行多条 SQL（多次网络往返）
- 生命周期 = 一个事务 = 一个业务请求
- **单线程独占**，无需考虑并发控制

## 为什么能不做精细化失效

| 特性 | 说明 |
|------|------|
| 作用域小 | 仅一次会话，不跨线程共享 |
| 生命周期短 | 请求结束即销毁，脏数据不会扩散 |
| 定位清晰 | "本次会话的便签纸"，用完即丢 |
| 设计哲学 | MyBatis 是 SQL 优先的工具，缓存是辅助能力 |

## 控制维度

开发者可通过以下配置精确控制一级缓存行为：

| 控制项 | 作用 | 作用域 |
|--------|------|--------|
| `flushCache="true"` | 该 SQL 执行前清空一级缓存 | statement 级别 |
| `useCache="false"` | 该 SELECT 不写入缓存 | select 语句 |
| `localCacheScope=STATEMENT` | 每条 SQL 执行后立即清空（自废一级缓存） | 全局配置 |
| `sqlSession.clearCache()` | 手动清空 | 编程式 |

## 与二级缓存的关系

```
一级缓存 (SqlSession)  →  线程独享，短命，自动
二级缓存 (namespace)   →  跨 SqlSession 共享，需失效策略
数据库                 →  最终数据源
```

类比 CPU 缓存层级：一级缓存如 L1（每核独享），二级缓存如 L3（多核共享）。

## 设计启示

MyBatis 的一级缓存默认"全缓存所有 SELECT"，但可通过 `useCache` 逐条关闭。默认值只是初始配置，关键在于框架提供了**可控的维度**——开发者可以针对大查询、报表查询关闭缓存，而不影响其他 SELECT。

---

## 关联

- [[MyBatis二级缓存]] — 跨 SqlSession 的共享缓存
