---
type: atomic-note
id: CONCEPT-spring-tx-propagation
created: 2026-05-31
updated: 2026-05-31
tags: [spring, 事务, JDBC, mybatis]
status: 🌿
mastery: 75
related_emrg: [EMRG-ORM与持久层]
related_goal: [GOAL-ORM与缓存]
---

# Spring事务传播行为与JDBC实现

## 一句话定义
`@Transactional(propagation = ...)` 的 7 种行为，在 JDBC 层面就是对 Connection 的获取、挂起、恢复、提交策略的不同组合。

## 核心理解

### JDBC 层面统一模型

所有传播行为在 JDBC 层面只做 4 件事：
1. **获取 Connection**（从连接池拿 / 复用 ThreadLocal 中的）
2. **挂起 Connection**（从 ThreadLocal 解绑，暂存到 SuspendedResourcesHolder）
3. **恢复 Connection**（把暂存的 Connection 重新绑回 ThreadLocal）
4. **提交/回滚**（commit / rollback / savepoint）

### 源码证据

`AbstractPlatformTransactionManager.handleExistingTransaction()`：

```java
// REQUIRES_NEW — 挂起 + 拿新连接 + begin
Object suspendedResources = suspend(transaction);
return startTransaction(definition, transaction, ...);
// → doBegin() → getConnection() → setAutoCommit(false)

// NOT_SUPPORTED — 挂起 + 不 begin（第二个参数 null = 无事务对象）
Object suspendedResources = suspend(transaction);
return prepareTransactionStatus(def, null, ...);

// NESTED — 不挂起，同一个 conn 上打 savepoint
```

`DataSourceTransactionManager.doSuspend()`：
```java
txObject.setConnectionHolder(null);
return TransactionSynchronizationManager.unbindResource(obtainDataSource());
// 只解绑，不调 setAutoCommit(true)！Spring 刻意避免切换 autoCommit
```

### 4 种常用传播行为对比

| 行为 | Connection | 提交 | 回滚影响 |
|------|-----------|------|---------|
| REQUIRED | 复用外层 conn | 统一 commit | 抛异常 → 全回滚 |
| REQUIRES_NEW | 新拿 connB | 独立 commit | 外层回滚不影响已提交的内层 |
| NESTED | 同一 conn + savepoint | 统一 commit | savepoint 回滚 ≠ conn 回滚 |
| NOT_SUPPORTED | 临时连接(autoCommit) | 即发即交 | 完全不受外层影响 |

### 关键误区

- "NOT_SUPPORTED 会切换 autoCommit" → 错。Spring 不切，而是完全不拿连接，让 Mapper 每次从连接池取新的（默认 autoCommit=true）
- "NESTED 和 REQUIRES_NEW 差不多" → 错。NESTED 在同一 conn 上打 savepoint，外层回滚全丢。REQUIRES_NEW 是独立 conn，外层回滚不受影响
- "REQUIRES_NEW 是嵌套事务" → 名不符实。是并行独立连接，不是嵌套

## 关键关联

- [[MyBatis与MyBatis-Plus核心差异]] — MP 拦截器在 Executor 层，不受 Spring AOP this 调用影响
- [[MyBatis缓存机制与生产实践]] — 事务提交后才刷入二级缓存
- [[DataSourceTransactionManager]] — doBegin/doSuspend/doResume 的完整源码链路

## 我的误区与疑问

- ❌ 最初以为 NOT_SUPPORTED 是切换 autoCommit，实际是换新连接
- ❌ REQUIRES_NEW 取名 misleading，实际是"新连接上的并行事务"而非"嵌套事务"

## 深入思考
💡 如果连接池只剩一个连接，REQUIRES_NEW 拿不到第二个连接会怎样？阻塞等待还是抛异常？

## 来源
- 项目：[[mybatis-sql-lab]]
- 对话：[[2026-05-31-Spring事务与JDBC隔离级别对话]]
