---
type: concept
created: 2026-05-31
updated: 2026-06-01
tags: [spring, 事务, JDBC, mybatis]
difficulty: advanced
mastery: 75
related_emrg: [EMRG-ORM与持久层]
related_goal: [GOAL-ORM与缓存]
---

# Spring事务传播行为与JDBC实现

> 一句话：`@Transactional(propagation = ...)`的7种行为，在JDBC层面就是对Connection的获取、挂起、恢复、提交策略的不同组合。

## 一、JDBC层面统一模型

所有传播行为在JDBC层面只做4件事：
1. **获取Connection**（从连接池拿 / 复用ThreadLocal中的）
2. **挂起Connection**（从ThreadLocal解绑，暂存到SuspendedResourcesHolder）
3. **恢复Connection**（把暂存的Connection重新绑回ThreadLocal）
4. **提交/回滚**（commit / rollback / savepoint）

## 二、源码证据

`AbstractPlatformTransactionManager.handleExistingTransaction()`：

```java
// REQUIRES_NEW — 挂起 + 拿新连接 + begin
Object suspendedResources = suspend(transaction);
return startTransaction(definition, transaction, ...);
// → doBegin() → getConnection() → setAutoCommit(false)

// NOT_SUPPORTED — 挂起 + 不begin（第二个参数null = 无事务对象）
Object suspendedResources = suspend(transaction);
return prepareTransactionStatus(def, null, ...);

// NESTED — 不挂起，同一个conn上打savepoint
```

`DataSourceTransactionManager.doSuspend()`：
```java
txObject.setConnectionHolder(null);
return TransactionSynchronizationManager.unbindResource(obtainDataSource());
// 只解绑，不调setAutoCommit(true)！Spring刻意避免切换autoCommit
```

## 三、4种常用传播行为对比

| 行为 | Connection | 提交 | 回滚影响 |
|------|-----------|------|---------|
| REQUIRED | 复用外层conn | 统一commit | 抛异常 → 全回滚 |
| REQUIRES_NEW | 新拿connB | 独立commit | 外层回滚不影响已提交的内层 |
| NESTED | 同一conn + savepoint | 统一commit | savepoint回滚 ≠ conn回滚 |
| NOT_SUPPORTED | 临时连接(autoCommit) | 即发即交 | 完全不受外层影响 |

## 四、关键误区

- "NOT_SUPPORTED会切换autoCommit" → 错。Spring不切，而是完全不拿连接，让Mapper每次从连接池取新的（默认autoCommit=true）
- "NESTED和REQUIRES_NEW差不多" → 错。NESTED在同一conn上打savepoint，外层回滚全丢。REQUIRES_NEW是独立conn，外层回滚不受影响
- "REQUIRES_NEW是嵌套事务" → 名不符实。是并行独立连接，不是嵌套

## 五、我的误区

- ❌ 最初以为NOT_SUPPORTED是切换autoCommit，实际是换新连接
- ❌ REQUIRES_NEW取名misleading，实际是"新连接上的并行事务"而非"嵌套事务"

## 深入思考
💡 如果连接池只剩一个连接，REQUIRES_NEW拿不到第二个连接会怎样？阻塞等待还是抛异常？

> ⚠️ 本笔记mastery 75为源码级理解，待连接池耗尽场景实验验证后更新

## 来源
- 项目：[[mybatis-sql-lab]]
- 对话：[[2026-05-31-Spring事务与JDBC隔离级别对话]]
