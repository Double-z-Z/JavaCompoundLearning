---
type: atomic-note
id: CONCEPT-jdbc-isolation-lostupdate
created: 2026-05-31
updated: 2026-05-31
tags: [JDBC, 事务, 并发, 数据库]
status: 🌿
mastery: 70
related_emrg: [EMRG-ORM与持久层]
related_goal: [GOAL-ORM与缓存]
---

# JDBC隔离级别与并发写冲突

## 一句话定义
SQL 标准的 4 个隔离级别只定义了**读的副作用**（脏读/不可重复读/幻读），从未定义**写的并发行为**（Lost Update / Write Skew），导致同一隔离级别名称在不同数据库实现中行为不同。

## 核心理解

### SQL 标准 vs 数据库实现

| | SQL 标准定义 | PG REPEATABLE READ | MySQL REPEATABLE READ |
|---|---|---|---|
| 脏读 | ✓ 禁止 | ✓ 禁止 | ✓ 禁止 |
| 不可重复读 | ✓ 禁止 | ✓ 禁止 | ✓ 禁止 |
| 幻读 | ✓ 禁止 | ✓ 禁止 | ✓ 禁止(Gap Lock) |
| Lost Update | **未定义** | ✓ 防止(写冲突检测) | ✗ 不防止 |

PG 的 REPEATABLE READ 通过 MVCC 元组可见性（xmin/xmax）实现写冲突检测：UPDATE 时检查行在快照时刻后是否被其他事务修改过，检测到则报错。

### 三个并发问题的真实场景

**脏读**：读到其他事务未提交的数据 → 事务回滚后数据从未存在 → 报表/决策基于假数据。

**不可重复读**：读-判断-写三步骤中，两次读之间数据被改了 → 判断基于过期值 → 计算写回覆盖别人的修改。这才是真正的危害：不是"读了两次不相同的值"，而是"基于假值做的计算覆盖了真值"。

**幻读**：循环处理一批数据时，中间插入了新行 → 遗漏处理 → 业务不完整。

### 命名批判

"不可重复读"这个名字 misleading —— 问题的本质不是"读的值变了"，而是**读-写事务的写冲突**。即使你能保证每次读到的都是同一快照，如果不限制写，冲突依然存在。正确的命名应该叫 "Lost Update Risk"。

## 关键关联

- [[Spring事务传播行为与JDBC实现]] — 传播行为控制 Connection 获取策略
- [[MyBatis缓存机制与生产实践]] — 事务隔离级别影响缓存一致性

## 我的误区与疑问

- ❌ 最初认为 Lost Update 靠 REPEATABLE READ 解决是标准行为 → 实际是 PG 特有的
- ❌ 抽象概念（脏读/幻读）在没有真实场景时难以理解 → 需要具体的金融/报表场景才能体会

## 深入思考
💡 MySQL REPEATABLE READ 防了幻读（Gap Lock）却不防 Lost Update。PG REPEATABLE READ 防了 Lost Update 但没 Gap Lock。两者都用 MVCC，为什么行为差异这么大？因为 MVCC 的实现选择不同——PG 选择 first-committer-wins，MySQL 选择 first-to-lock-wins。

## 来源
- 项目：[[mybatis-sql-lab]]
- 对话：[[2026-05-31-Spring事务与JDBC隔离级别对话]]
