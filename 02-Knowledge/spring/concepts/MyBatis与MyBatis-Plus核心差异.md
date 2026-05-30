---
type: concept
created: 2026-05-28
updated: 2026-05-31
tags: [mybatis, 持久层, spring]
related_emrg: [EMRG-ORM与持久层]
related_goal: [GOAL-ORM与缓存]
mastery: 60
---

# MyBatis 与 MyBatis-Plus 核心差异

> 一句话：MyBatis 是引擎，MyBatis-Plus 是自动挡变速箱 —— 前者给你完全的控制权，后者让你在日常通勤（CRUD）时不用频繁换挡，但遇到赛道（复杂 SQL）时，你依然可以切回手动模式。

## 一、定位关系

| 维度 | MyBatis | MyBatis-Plus |
|---|---|---|
| **本质** | 持久层框架，专注 SQL 映射 | MyBatis 的增强工具包 |
| **依赖关系** | 独立框架 | **必须依赖 MyBatis**，在其生命周期内介入 |
| **设计哲学** | SQL 由开发者完全控制 | 常规 CRUD 零 SQL，复杂查询仍回归手写 |
| **适用场景** | 任何需要精细控制 SQL 的项目 | 互联网业务系统、管理后台、CRUD 密集型应用 |

## 二、核心功能差异

### 1. 通用 CRUD：BaseMapper

**MyBatis**：每个实体都需要手写 `insert`、`selectById`、`updateById` 等方法，或在 XML 中重复编写。

**MyBatis-Plus**：继承 `BaseMapper<T>` 即可获得全套通用方法。

```java
public interface UserMapper extends BaseMapper<User> {}

userMapper.insert(user);
userMapper.selectById(1L);
userMapper.updateById(user);
userMapper.deleteById(1L);
```

### 2. 条件构造器：Wrapper

**MyBatis**：动态 SQL 通过 `<if>` 标签或注解 `@SelectProvider` 拼接，XML 冗长。

**MyBatis-Plus**：提供链式 API 构造动态条件，无需 XML。

```java
List<User> list = userMapper.selectList(
    Wrappers.<User>lambdaQuery()
        .eq(User::getStatus, 1)
        .like(User::getName, "zhang")
        .ge(User::getCreateTime, startDate)
);
```

### 3. 分页插件

**MyBatis**：分页需手写 `LIMIT` 或引入 PageHelper 等第三方插件。

**MyBatis-Plus**：内置分页插件，物理分页自动完成。

```java
Page<User> page = userMapper.selectPage(
    new Page<>(current, size), 
    queryWrapper
);
```

### 4. 代码生成器

**MyBatis**：需借助 MyBatis Generator（MBG）或手写。

**MyBatis-Plus**：提供 AutoGenerator，根据数据库表一键生成 Entity、Mapper、Service、Controller。

### 5. 其他增强特性

| 特性 | MyBatis-Plus 支持 | MyBatis 原生 |
|---|---|---|
| **逻辑删除** | 注解 `@TableLogic`，自动转换 DELETE 为 UPDATE | 需手写 |
| **自动填充** | 注解 `@TableField(fill = FieldFill.INSERT)`，自动写入 create_time | 需手写 |
| **多租户** | 内置 TenantLineInnerInterceptor，自动拼接租户 ID | 需手写拦截器 |
| **动态表名** | 内置 DynamicTableNameInnerInterceptor | 需手写 |
| **乐观锁** | 注解 `@Version`，自动 CAS 更新 | 需手写 |
| **SQL 注入器** | 自定义全局方法（如 `insertIgnore`）注入所有 Mapper | 无 |

## 三、架构介入方式

MyBatis-Plus 通过 **MyBatis 插件机制（Interceptor）** 介入执行流程：

```
你的代码
   ↓
BaseMapper / IService（MP 提供）
   ↓
MyBatis SqlSession（原生）
   ↓
MP 拦截器链（分页、多租户、动态表名...）
   ↓
JDBC
```

**关键**：
- 你**仍然可以使用原生 MyBatis 的全部能力**（手写 XML、`@Select`、自定义 TypeHandler 等）。
- MP 只是在 Mapper 层和 SQL 执行层做了增强，**没有破坏 MyBatis 的扩展点**。

## 四、性能差异

| 维度 | 说明 |
|---|---|
| **运行时开销** | MP 的 Wrapper 条件构造在应用层完成，最终生成的 SQL 仍由 MyBatis 执行，**无额外数据库交互开销**。 |
| **启动时开销** | MP 会在启动时解析实体注解（`@TableName`、`@TableField` 等），比纯 MyBatis 略慢，但可忽略。 |
| **缓存** | MP 不改动 MyBatis 一/二级缓存机制，缓存问题在 MP 中同样存在。 |

## 五、选型建议

| 场景 | 建议 |
|---|---|
| **新项目（中国互联网）** | **直接用 MyBatis-Plus**。它已成为国内 Spring Boot 新项目的事实标准，招聘、社区、生态最完善。 |
| **已有 MyBatis 项目** | **可渐进式引入**。先让新表使用 MP，旧 XML 逐步迁移，两者可共存。 |
| **需要极致 SQL 优化** | MP 负责 80% 常规 CRUD，剩余 20% 复杂查询仍手写 XML 或 `@Select`。 |
| **跨数据库可移植性要求高** | MP 的 Wrapper 对分页语法做了多数据库适配（MySQL、PG、Oracle、SQLServer），比手写 `LIMIT` 更可移植。 |

## 关联知识

- [[Java持久层框架市场格局与选型]] — 市场格局与选型背景
- [[MyBatis核心机制]] — 缓存与动态 SQL 机制（归属 EMRG-ORM与持久层）
- [[MyBatis缓存机制与生产实践]] — 缓存机制在 MP 中同样存在

## 来源

- 本次对话整理自 Kimi 对话记录（2026-05-28）
