---
type: atomic-note
id: CONCEPT-mp-annotation-enhance
created: 2026-06-01
updated: 2026-06-01
tags: [mybatis, 持久层]
status: 🌿
mastery: 60
related_emrg: [EMRG-ORM与持久层]
related_goal: [GOAL-ORM与缓存]
---

# MP注解增强：@TableLogic + @TableField(fill)

## 一句话定义
`@TableLogic` 把 DELETE 变为 UPDATE，`@TableField(fill)` 通过 MetaObjectHandler 在 INSERT/UPDATE 时自动填充审计字段——两者都是注解声明意图 + 框架静默执行。

## 核心理解

### @TableLogic

```java
@TableLogic
private Integer deleted;  // 0=正常, 1=删除
```

SQL 自动改写：
- `deleteById(id)` → `UPDATE SET deleted=1 WHERE id=? AND deleted=0`
- `selectById(id)` → `SELECT ... WHERE id=? AND deleted=0`
- `updateById(entity)` → `UPDATE ... WHERE id=? AND deleted=0`

原生 XML Mapper 不受影响——如果在 XML 中手写了 SELECT，不会有 `deleted=0` 条件。逻辑删除和乐观锁（@Version）可以共存——两者的 AND 条件叠加在 WHERE 子句中。

### @TableField(fill)

```java
@TableField(fill = FieldFill.INSERT)        // 仅 INSERT 时填充
private LocalDateTime createTime;

@TableField(fill = FieldFill.INSERT_UPDATE) // INSERT + UPDATE 都填充
private LocalDateTime updateTime;
```

MetaObjectHandler 需要手动实现：

```java
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
    public void insertFill(MetaObject metaObject) {
        this.setFieldValByName("createTime", now, metaObject);  // 直接覆写
    }
    public void updateFill(MetaObject metaObject) {
        this.setFieldValByName("updateTime", now, metaObject);
    }
}
```

### 踩坑：strict vs setFieldValByName

- `strictInsertFill` / `strictUpdateFill`：字段已有值时跳过（尊重手动设值）
- `setFieldValByName`：无条件覆写

UPDATE 时 updateTime 在 INSERT 阶段已有值，strict 模式会跳过 → 时间不更新。解决：UPDATE 用 `setFieldValByName`。

### @TableField(fill) 不止时间戳

典型用途：从 SecurityContext 取当前用户填充 `createdBy`/`updatedBy`。每处手写 `setCreatedBy()` 容易遗漏，MetaObjectHandler 一处定义全局生效。

### 数据库时间 vs 应用时间

用 `CURRENT_TIMESTAMP`（SQL 标准，跨数据库）替代 `now()`（PG/MySQL 方言）：

```java
@TableField(fill = FieldFill.INSERT_UPDATE, update = "CURRENT_TIMESTAMP")
private LocalDateTime updateTime;
```

应用层 `LocalDateTime.now()` 依赖服务器时钟，DB 函数 `CURRENT_TIMESTAMP` 统一用数据库时钟。

## 关键关联

- [[MyBatis与MyBatis-Plus核心差异]] — @TableLogic 和 @Version 都是注解驱动的静默 SQL 改写
- [[Spring事务传播行为与JDBC实现]] — MetaObjectHandler 在事务内触发，fill 值随事务提交

## 我的误区与疑问

- ❌ 最初用 strictUpdateFill → updateTime 不更新 → 改用 setFieldValByName
- ❌ `@MapperScan("com.example.order")` 扫到 IService 接口 → MyBatis 把它当 Mapper 处理 → 报 Invalid bound statement

## 深入思考
💡 逻辑删除的 deleted=0 过滤对性能影响多大？如果 deleted=0 有 10 万条、deleted=1 有 500 万条，全表扫描会很慢。解决办法：部分索引（PG `CREATE INDEX ON users(id) WHERE deleted=0`）。

## 来源
- 项目：[[mybatis-sql-lab]]
- 对话：[[2026-06-01-MP注解增强与IService]]
