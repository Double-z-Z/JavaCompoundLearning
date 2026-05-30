---
title: MyBatis二级缓存
date: 2026-05-30
tags:
  - orm
  - mybatis
  - cache
mastery: 55
status: budding
related_emrg: [EMRG-ORM与持久层, EMRG-Cache]
related_goals:
  - "[[GOAL-ORM与缓存]]"
related_concepts:
  - "[[MyBatis一级缓存]]"
---

# MyBatis二级缓存

## 核心机制

二级缓存是 **namespace（Mapper）级别**的共享缓存，跨 SqlSession 生效。

### 缓存结构：装饰器链

```
HashMap (PerpetualCache)
  → SynchronizedCache    — 加锁，保证并发安全
  → LoggingCache         — 命中率日志
  → LruCache             — 淘汰策略（可选）
  → TransactionalCache   — 事务边界控制
```

### TransactionalCache 的本质

> [!note] 不是"缓存"
> TransactionalCache 不存储数据，它只是一个**事务边界的缓存代理**：
> - 持有"待清空标记"（clearOnCommit）
> - 暂存待提交条目（entriesToAddOnCommit）
> - commit 时才真正写入共享缓存，rollback 则丢弃

## 设计哲学

> [!important] 简单可用，而非高并发高性能
> MyBatis 原生二级缓存的设计目标是**实用**，而非应对高并发场景。

**实践建议**：
- **Service 层**：引入 Redis/Caffeine 做显式业务缓存
- **Mapper 层**：保持一级缓存开启，优化会话内重复查询
- 二级缓存仅适用于**读多写少、数据变更不频繁**的场景

## 失效策略

与一级缓存相同——**写操作全清整个 namespace 缓存**，不做行级失效追踪。

## 与一级缓存的对比

| 维度 | 一级缓存 | 二级缓存 |
|------|---------|---------|
| 作用域 | SqlSession 独享 | namespace 共享 |
| 线程安全 | 不需要（单线程） | SynchronizedCache 加锁 |
| 生命周期 | 随 SqlSession 关闭销毁 | 随应用运行 |
| 事务感知 | 无（直接读写） | TransactionalCache 延迟提交 |
| 适用场景 | 所有查询 | 读多写少 |

---

## 关联

- [[MyBatis一级缓存]] — 会话级别缓存
