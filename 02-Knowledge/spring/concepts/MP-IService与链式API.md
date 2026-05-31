---
type: atomic-note
id: CONCEPT-mp-iservice
created: 2026-06-01
updated: 2026-06-01
tags: [mybatis, 持久层]
status: 🌿
mastery: 60
related_emrg: [EMRG-ORM与持久层]
related_goal: [GOAL-ORM与缓存]
---

# MyBatis-Plus IService 层

## 一句话定义
IService 是 BaseMapper 上的薄层封装，提供链式 API、批量操作和 saveOrUpdate 语义——不需要 new Wrapper。

## 核心理解

### 继承结构

```java
public interface UserService extends IService<User> {}
@Service
public class UserServiceImpl extends ServiceImpl<MpUserMapper, User> implements UserService {}
```

需要 2 个文件，但 ServiceImpl 通常是空类。

### 链式查询：lambdaQuery()

```java
// 之前：手动 new LambdaQueryWrapper
mapper.selectList(new LambdaQueryWrapper<User>().eq(User::getUsername, "张三"));

// IService：链式 API
userService.lambdaQuery().eq(User::getUsername, "张三").list();
userService.lambdaQuery().eq(User::getEmail, "test").one();     // 期望一条
userService.lambdaQuery().like(User::getEmail, "example").count();
```

### 链式更新：lambdaUpdate()

```java
userService.lambdaUpdate()
    .eq(User::getUsername, "王五")
    .set(User::getPhone, "NEW")
    .update();  // 等价于 UPDATE users SET phone='NEW' WHERE username='王五' AND tenant_id=?
```

### saveOrUpdate

id 为 null → INSERT，id 不为 null → UPDATE。配合分布式 ID（`@TableId(type = IdType.ASSIGN_ID)`）自动判断。

### 批量操作

`saveBatch(users)` 分批次提交（默认 1000 条一批），自动回填 ID。

### @MapperScan 陷阱

`@MapperScan("com.example.order")` 会扫到 `UserService` 接口（extends IService），MyBatis 尝试解析其方法，报 `Invalid bound statement: getBaseMapper`。必须限制扫描范围：`@MapperScan({"com.example.order.mapper", "com.example.order.mp"})`。

## 关键关联

- [[MyBatis与MyBatis-Plus核心差异]] — IService 替代手写 Service CRUD
- [[MP注解增强-TableLogic与自动填充]] — saveOrUpdate 触发 MetaObjectHandler 的 insertFill/updateFill

## 我的误区与疑问

- ❌ 以为 IService 会有很多自动生成的方法 —— 实际是薄层，核心还是 BaseMapper
- ❌ @MapperScan 扫到 IService 导致启动失败 —— 必须精确指定包

## 深入思考
💡 IService 和 Spring Data JPA 的 JpaRepository 很像（都是继承接口获得 CRUD），但 IService 不是 Spring Data 体系，没有 `findByXxx()` 方法名解析。复杂查询仍需 Wrapper 或 XML。

## 来源
- 项目：[[mybatis-sql-lab]]
- 对话：[[2026-06-01-MP注解增强与IService]]
