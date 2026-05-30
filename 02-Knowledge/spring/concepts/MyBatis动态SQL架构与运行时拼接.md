---
type: concept
created: 2026-05-28
updated: 2026-05-31
tags: [mybatis, 持久层, 性能, 架构]
related_emrg: [EMRG-ORM与持久层]
related_goal: [GOAL-ORM与缓存]
mastery: 50
---

# MyBatis动态SQL架构与运行时拼接

> 一句话：MyBatis 每次执行都重新遍历 `SqlNode` 树并求值 OGNL 表达式，这是设计契约上的保守策略（为最坏情况做防护），而非技术不能；MyBatis-Plus 的 Wrapper 同样运行时拼接，但用类型安全 API 替代了 XML。

## 一、MyBatis 动态 SQL 的核心机制

### 启动期：解析 XML 为 SqlNode 树

MyBatis 启动时解析 XML，把动态 SQL 标签（`<if>`、`<foreach>`、`<choose>`）转换成一棵 `SqlNode` 组合树。

### 运行期：每次执行重新遍历

```java
public class DynamicSqlSource implements SqlSource {
    private SqlNode rootSqlNode; // 启动期解析好的 XML 节点树

    @Override
    public BoundSql getBoundSql(Object parameterObject) {
        DynamicContext context = new DynamicContext(configuration, parameterObject);
        // 关键：每次执行都重新遍历整棵树
        rootSqlNode.apply(context); 
        // 解析 #{} 占位符，生成最终 SQL + 参数映射
        SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
        SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());
        return sqlSource.getBoundSql(parameterObject);
    }
}
```

**问题**：`rootSqlNode.apply(context)` 每次执行都会重新走一遍树遍历、OGNL 表达式求值、字符串拼接。即使参数完全一样，也不会缓存上一次的 `BoundSql`。

## 二、为什么不做缓存？OGNL 的不可预测性

MyBatis 不做 SQL 模板编译缓存，根本原因是动态 SQL 依赖 **OGNL 表达式**，而 OGNL 是**图灵完备**的：

```xml
<if test="@java.lang.System@currentTimeMillis() % 2 == 0">
  AND status = 'ACTIVE'
</if>
```

这个 `<if>` 的条件在**运行时每次都会变化**。这意味着：
- 框架**无法在启动期**确定有哪些 SQL 分支可能被执行
- 框架**无法在运行期**安全地缓存某个参数模式对应的 SQL 结构

**MyBatis 的设计假设**：`SqlNode` 树的执行结果是不可预测的，所以必须每次重新求值。这是一种"安全但低效"的保守策略。

## 三、PreparedStatement 的复用限制

即使 SQL 文本缓存了，JDBC 层面的 `PreparedStatement` 复用也受限于架构：

| 层级 | 复用机制 | 限制 |
|---|---|---|
| **MyBatis `ReuseExecutor`** | 在同一个 `SqlSession` 内按 SQL 文本缓存 `PreparedStatement` | `SqlSession` 通常是请求级别，用完即关，缓存几乎无法跨请求复用 |
| **连接池（HikariCP）** | 连接复用，但 `PreparedStatement` 随连接关闭而关闭 | 不跨连接缓存 `PreparedStatement` 对象 |
| **数据库驱动** | 服务器端 Prepared Statement 缓存（如 MySQL `cachePrepStmts`） | 缓存的是执行计划，不是客户端对象；首次仍需网络交互 |

## 四、MyBatis-Plus Wrapper 的本质

MyBatis-Plus 的 Wrapper **仍然是预编译的**，只是 SQL 的"结构"（哪些 WHERE 条件存在）是运行时动态决定的：

```sql
-- MP Wrapper 最终生成的 SQL
SELECT id, name, age FROM user WHERE (age > ? AND name LIKE ?)
```

参数 `?` 通过 `PreparedStatement.setXxx()` 绑定，**SQL 注入风险被 `#{}` 机制完全隔离**，数据库仍然会对这条 SQL 做预编译和执行计划缓存。

**MP 隐藏 XML 的意义**：
- **类型安全**：`User::getName` 是编译期符号，重构时 IDE 自动追踪
- **可组合性**：条件逻辑从 XML 的"声明式片段"变成了 Java 的"可复用函数"
- **SQL 仍然可见**：开启 SQL 日志后，控制台仍然打印原生 SQL，开发者可以复制到数据库客户端 Explain 分析

## 五、已有框架的实现对比

| 框架 | 机制 | 特点 |
|---|---|---|
| **MyBatis-Flex** | APT 编译期生成静态 SQL 模板类 | 编译期分析实体类和查询条件，运行时只是调用已编译好的 Java 方法 |
| **jOOQ** | 从数据库 Schema 生成 Java 代码 | 所有 SQL 结构在编译期完全确定，运行时只做参数绑定 |

## 六、什么时候不应该用运行时拼接？

| 场景 | 问题 | 替代方案 |
|---|---|---|
| **极高 QPS（>5000）的固定查询** | 执行计划缓存抖动，CPU 花在解析上 | 写死静态 SQL，或直接用 JDBC |
| **复杂报表（10+ 表 JOIN）** | 动态拼接的 SQL 可读性差，优化器难以稳定生成好计划 | 手写 SQL + 专用报表服务 |
| **分库分表** | SQL 结构变化导致 Sharding 路由缓存失效 | 静态 SQL + 强制路由键 |

## 关联知识

- [[MyBatis核心机制]] — 一/二级缓存与动态 SQL 的关系（归属 EMRG-ORM与持久层）
- [[MyBatis与MyBatis-Plus核心差异]] — MP Wrapper 的类型安全与可组合性

## 来源

- 本次对话整理自 Kimi 对话记录（2026-05-28）
