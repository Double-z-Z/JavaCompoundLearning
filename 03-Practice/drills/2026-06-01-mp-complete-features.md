---
created: 2026-06-01
type: drill
tags: [mybatis, spring, 事务]
difficulty: 🌿
related_concepts:
  - [[Spring事务传播行为与JDBC实现]]
  - [[JDBC隔离级别与并发写冲突]]
  - [[MP注解增强-TableLogic与自动填充]]
  - [[MP-IService与链式API]]
  - [[MyBatis与MyBatis-Plus核心差异]]
---

# MP完整特性验证 + Spring事务实战

> 🎯 目标：将 MP 全部特性落地到项目中并编写验证测试

## 练习内容

### 已实现的功能清单

| 特性 | 测试文件 | 验证内容 |
|------|---------|---------|
| BaseMapper CRUD | MpCrudTest | 零代码单表操作 |
| QueryWrapper | MpWrapperTest | 条件构造器全能力 |
| 分页插件 | MpPaginationTest | 物理分页 + COUNT 改写 |
| 乐观锁 | MpOptimisticLockTest | @Version 并发冲突检测 |
| 多租户 | MpMultiTenantTest | 自动 SQL 注入 tenant_id |
| 逻辑删除 | MpLogicDeleteAndAutoFillTest | DELETE→UPDATE, select 过滤 |
| 自动填充 | MpLogicDeleteAndAutoFillTest | createTime/updateTime |
| IService | MpIServiceTest | 链式 API + 批量操作 |
| 事务传播 | MpTransactionTest | REQUIRED/NESTED/REQUIRES_NEW |
| FOR UPDATE | MpTransactionTest | 悲观锁行锁验证 |
| AOP 代理陷阱 | MpTransactionTest | this 调用绕过 @Transactional |

### MP 完整知识图谱

```
MyBatis-Plus
├── 实体映射: @TableName @TableId @TableField
├── SqlInjector: SQL 自动注入（selectById/insert/updateById/deleteById）
├── 条件构造: QueryWrapper / LambdaQueryWrapper / UpdateWrapper
├── 分页插件: PaginationInnerInterceptor
├── 乐观锁:   OptimisticLockerInnerInterceptor + @Version
├── 多租户:   TenantLineInnerInterceptor + TenantLineHandler
├── 逻辑删除: @TableLogic
├── 自动填充: @TableField(fill) + MetaObjectHandler
├── IService:  lambdaQuery / lambdaUpdate / saveBatch / saveOrUpdate
└── 代码生成: AutoGenerator（未落地）
```

### 遇到的困难

- NativeDynamicSqlTest 测试数据被其他非事务性测试污染 → 给所有修改数据的测试类加 @Transactional
- @MapperScan 扫到 IService 接口导致 MyBatis 解析失败 → 精确指定 Mapper 包
- strictUpdateFill 跳过已有值 → 改用 setFieldValByName
- Spring Boot 3.2 与 mybatis-spring 2.1.2 不兼容 → 降级到 3.1.12
- TxDemoService 自注入循环依赖 → @Lazy
- Spring SqlSessionTemplate 无一级缓存 → assertSame 改为字段级 assertEquals

## 验证与测试

### 测试结果

```
MpCrudTest:           11/11  ✓
MpOptimisticLockTest:  4/4   ✓
MpMultiTenantTest:     8/8   ✓
MpPaginationTest:      2/2   ✓
MpWrapperTest:        21/21  ✓
NativeCrudTest:        9/9   ✓
NativeDynamicSqlTest: 17/17  ✓
NativeResultMapTest:   7/7   ✓
MpTransactionTest:     6/6   ✓
MpLogicDelete...Test:  5/5   ✓
MpIServiceTest:        9/9   ✓
─────────────────────────────
Total:                99/99  ✓
```

## 复盘总结

### 学到的

- MP 的增强建立在 MyBatis 的 Plugin 机制上（Interceptor），不破坏原有扩展点
- 注解驱动（@Version/@TableLogic/@TableField）的本质：声明意图 → 拦截器执行 → SQL 改写
- Spring AOP 和 MyBatis Plugin 是两套完全不同的拦截：AOP 在 Bean 边界，Plugin 在 Executor 内部
- 写冲突问题的本质不是"读"，是"读写事务的写冲突"

### 待深化

- 多数据源下的 MP 配置
- ShardingSphere 分库分表与 MP 的集成
- Response式事务（R2DBC）与 MP 的兼容性
