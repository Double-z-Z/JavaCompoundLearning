---
type: concept
created: 2026-05-31
updated: 2026-06-01
tags: [JDBC, 事务, 并发, 数据库]
difficulty: advanced
mastery: 70
related_emrg: [EMRG-ORM与持久层]
related_goal: [GOAL-ORM与缓存]
---

# JDBC隔离级别与并发写冲突

> 一句话：SQL标准的4个隔离级别只定义了读的副作用（脏读/不可重复读/幻读），从未定义写的并发行为（Lost Update/Write Skew），导致同一隔离级别名称在不同数据库实现中行为不同。

## 一、SQL标准 vs 数据库实现

| | SQL标准定义 | PG REPEATABLE READ | MySQL REPEATABLE READ |
|---|---|---|---|
| 脏读 | ✓ 禁止 | ✓ 禁止 | ✓ 禁止 |
| 不可重复读 | ✓ 禁止 | ✓ 禁止 | ✓ 禁止 |
| 幻读 | ✓ 禁止 | ✓ 禁止 | ✓ 禁止(Gap Lock) |
| Lost Update | **未定义** | ✓ 防止(写冲突检测) | ✗ 不防止 |

PG的REPEATABLE READ通过MVCC元组可见性（xmin/xmax）实现写冲突检测：UPDATE时检查行在快照时刻后是否被其他事务修改过，检测到则报错。

## 二、三个并发问题的真实场景

**脏读**：读到其他事务未提交的数据 → 事务回滚后数据从未存在 → 报表/决策基于假数据。

**不可重复读**：读-判断-写三步骤中，两次读之间数据被改了 → 判断基于过期值 → 计算写回覆盖别人的修改。问题的本质不是"读的值变了"，而是**基于假值做的计算覆盖了真值**。

**幻读**：循环处理一批数据时，中间插入了新行 → 遗漏处理 → 业务不完整。

## 三、命名批判

"不可重复读"这个名字misleading——即使你能保证每次读到同一快照，如果不限制写，冲突依然存在。正确的命名应该叫"Lost Update Risk"。

## 四、MySQL vs PG的MVCC选择

MySQL REPEATABLE READ防了幻读（Gap Lock）却不防Lost Update。PG REPEATABLE READ防了Lost Update但没Gap Lock。两者都用MVCC，行为差异源于实现选择：
- **PG**：first-committer-wins（提交时检测冲突）
- **MySQL**：first-to-lock-wins（加锁时抢占）

## 五、我的误区

- ❌ 最初认为Lost Update靠REPEATABLE READ解决是标准行为 → 实际是PG特有的
- ❌ 抽象概念（脏读/幻读）在没有真实场景时难以理解 → 需要具体的金融/报表场景才能体会

## 深入思考
💡 MySQL的Gap Lock解决了幻读但引入锁竞争，PG的写冲突检测解决了Lost Update但需应用层重试。两种设计没有绝对优劣，只有场景适配。

> ⚠️ 本笔记mastery 70为概念理解，待多数据库对比实验验证后更新

## 来源
- 项目：[[mybatis-sql-lab]]
- 对话：[[2026-05-31-Spring事务与JDBC隔离级别对话]]
