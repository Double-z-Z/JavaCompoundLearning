这两个问题触及了迁移工程的核心实现细节。基于去哪儿、vivo、美团的生产实践，我直接给出结论：

**1. 双写路由：MyBatis 插件是更优解，AOP + SqlSessionFactory 更适合单写灰度路由。**
**2. 同步需求：JdbcTemplate 既不"足够"也不"正确"，数据同步应使用 CDC 工具，业务双写必须复用 MyBatis。**

---

## 一、AOP + SqlSessionFactory 路由 vs MyBatis 插件拦截

### 核心区别：拦截层次与事务边界

| 维度 | AOP + SqlSessionFactory / AbstractRoutingDataSource | MyBatis 插件（Interceptor） |
|------|-----------------------------------------------------|---------------------------|
| **拦截位置** | Spring 方法调用层（Mapper 接口或 Service 方法） | MyBatis 内部 Executor/StatementHandler |
| **拦截粒度** | **方法级**：一次方法调用只能路由到一个 DataSource | **SQL 级**：每个 SQL 语句独立可拦截 |
| **双写能力** | ❌ **无法原生双写**。AbstractRoutingDataSource 一次 SQL 只能选一个 DataSource；手动双写需自己管理第二次调用 | ✅ **天然支持**。可在同一个拦截点内顺序/异步执行两次 SQL |
| **事务绑定** | 与 Spring `TransactionSynchronizationManager` 强耦合。如果 `@Transactional` 已开启，DataSource 已绑定到 ThreadLocal，中途切换无效 | 在 JDBC Connection 获取层操作，可绕过 Spring 事务绑定，为第二次写入创建独立 SqlSession |
| **灰度能力** | ✅ **极强**。可直接获取方法参数（如 `userId`）做路由决策 | ⚠️ 需从 `MappedStatement` 和 parameter 对象反射解析，较繁琐 |
| **代码侵入** | 需定义切面、代理、路由逻辑；Service 层可能感知 | 零侵入。只需在 `mybatis-config.xml` 或 Spring Boot 配置中注册 |
| **生产案例** | 美团早期 DAO Proxy（`xxProxyDAO`）类似此思路，但用于**单写开关**而非双写 | 去哪儿网明确采用："mybatis 外部进行双写，改动太多其负面影响也不好预估，所以才在 mybatis 插件内部做了双写"；vivo 也采用插件"有效降低改造成本" |

### 关键细节：为什么 AOP 不适合做双写？

**陷阱 1：Spring 事务的绑定机制**
```java
@Transactional
public void createOrder(Order order) {
    orderMapper.insert(order); // 事务已开启，DataSource 已绑定
}
```
如果 AOP 拦截 `createOrder`，在方法内部尝试切换 DataSource 写入新库：
- Spring 的 `DataSourceTransactionManager` 在事务开启时就已经从 `oldDataSource` 获取了 `Connection` 并绑定到 `TransactionSynchronizationManager`
- 你切换 DataSource 后，MyBatis 仍然使用事务绑定的旧 Connection
- **结果**：第二次写入仍然落到旧库，或者因 Connection 隔离导致无法写入新库

**陷阱 2：方法级粒度的粗陋**
AOP 拦截 `createOrder` 方法时，如果方法内部包含：
1. 插入订单主表
2. 插入订单明细表
3. 更新库存表

AOP 只能在方法入口做一次决策，无法针对**每个 SQL 语句**做精细化控制。而 MyBatis 插件拦截的是 `Executor.update()`，每个 SQL 都会被独立拦截。

**陷阱 3：双写异常处理复杂**
如果在 AOP 中手动双写：
```java
@Around("createOrderPointcut()")
public Object around(ProceedingJoinPoint pjp) {
    Object result = pjp.proceed(); // 写旧库（在事务中）
    // 手动写新库：需要新开 SqlSession，处理异常，不能影响主事务
}
```
你需要自己处理新库的 SqlSession 创建、事务提交、异常回滚，这与 MyBatis 插件相比没有任何优势，反而更复杂。

### 正确的分工

| 场景 | 推荐方案 |
|------|---------|
| **单写灰度路由**（读流量按 userId 切百分比） | AOP + AbstractRoutingDataSource。在事务开启前设置 ThreadLocal 路由键，简单高效 |
| **双写**（旧库+新库同时写入） | **MyBatis 插件**。在 Executor 层拦截，第一次 `proceed()` 写旧库，第二次创建独立 SqlSession 写新库 |
| **存量/增量同步** | CDC 工具（Canal/Debezium），与业务代码完全无关 |

---

## 二、同步需求用 JdbcTemplate：不够，且问题很多

你需要先明确"同步"指的是哪个层次：

### 情况 A：数据迁移同步（存量迁移 + 增量追赶）
**JdbcTemplate 完全不够。**

数据同步不是简单的 `INSERT INTO ... SELECT * FROM ...`，它要求：
- **Binlog 解析与位点管理**：增量同步需要订阅 MySQL Binlog，JdbcTemplate 没有解析能力
- **断点续传**：全量同步失败后可从断点恢复，而非全量重来
- **分片路由**：如果目标端是 ShardingSphere 分片集群，JdbcTemplate 无法自动计算分片键和路由规则
- **数据一致性校验**：同步完成后需要比对 checksum，JdbcTemplate 无内置机制
- **性能优化**：批量插入、并行分区、流式读取（避免内存溢出）

**正确工具**：DataX（全量）、Canal/Debezium/阿里精卫（增量）、ShardingSphere Scaling（一体化）。

### 情况 B：业务双写中的新库写入
**JdbcTemplate 能用，但强烈不推荐。**

如果你用 JdbcTemplate 实现双写，而业务层用 MyBatis，你会遇到：

| 问题 | 具体表现 |
|------|---------|
| **SQL 逻辑分叉** | MyBatis 有动态 SQL（`<if>`、`<foreach>`）、SQL 片段复用；JdbcTemplate 需要手动拼接，维护两套逻辑 |
| **类型转换不一致** | MyBatis 的 TypeHandler（如 JSON 映射、枚举映射、自定义类型）在 JdbcTemplate 中需要重新实现 |
| **自增主键回写** | MyBatis `useGeneratedKeys` 自动回填 ID；JdbcTemplate 需手动处理 `Statement.RETURN_GENERATED_KEYS`，且批量插入时行为复杂（vivo 明确提到这是坑） |
| **批量处理差异** | MyBatis `BatchExecutor` 与 JdbcTemplate `batchUpdate` 在内存管理、 JDBC 批量大小的行为不同 |
| **结果映射缺失** | 无法复用 MyBatis 的 `<resultMap>`，字段别名、嵌套映射、延迟加载全部失效 |
| **分页/插件生态** | MyBatis 分页插件（PageHelper）、监控插件（如 p6spy）对 JdbcTemplate 无效 |

**生产案例的共识**：
- 去哪儿网明确拒绝在 MyBatis 外部双写，因为"改动太多其负面影响也不好预估"
- vivo 使用 MyBatis 插件双写，核心目的就是复用同一套 SQL 逻辑和映射配置

---

## 三、推荐的架构组合

```text
┌──────────────────────────────────────────────┐
│  Service 层（@Transactional，零改动）         │
├──────────────────────────────────────────────┤
│  Mapper 层（MyBatis XML，零改动）              │
├──────────────────────────────────────────────┤
│  路由/双写层（唯一侵入点）                      │
│  ├─ 读灰度：AOP + AbstractRoutingDataSource   │
│  │   （按 userId 路由到旧库或新库）             │
│  └─ 写双写：MyBatis Interceptor               │
│      （同一 MappedStatement，两次执行）        │
│      ├─ 第一次：oldSqlSession（旧库）           │
│      └─ 第二次：newSqlSession（ShardingSphere）│
├──────────────────────────────────────────────┤
│  Spring DataSource 配置                       │
│  ├─ oldDataSource（直连旧库）                  │
│  └─ shardingDataSource（ShardingSphere 代理）  │
├──────────────────────────────────────────────┤
│  数据同步层（独立进程，与业务无关）              │
│  ├─ 全量：DataX / ShardingSphere Scaling      │
│  └─ 增量：Canal / Debezium                    │
└──────────────────────────────────────────────┘
```

---

## 四、总结

| 问题 | 结论 |
|------|------|
| **AOP 路由 vs MyBatis 插件** | **单写路由用 AOP，双写必须用 MyBatis 插件**。AOP 受限于 Spring 事务绑定和粗粒度，无法安全实现 SQL 级双写 |
| **同步用 JdbcTemplate？** | **数据同步用 CDC 工具，业务双写复用 MyBatis**。JdbcTemplate 既无法替代专业同步工具，也无法安全复用业务 SQL 逻辑 |
| **核心原则** | **双写必须在同一套 SQL 语义下执行**，任何独立实现（JdbcTemplate、手动拼接 SQL）都会导致数据语义漂移 |