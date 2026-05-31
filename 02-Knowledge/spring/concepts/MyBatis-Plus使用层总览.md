---
type: concept
created: 2026-06-01
updated: 2026-06-01
tags: [mybatis-plus, 持久层, 使用层]
related_emrg: [EMRG-ORM与持久层]
related_goal: [GOAL-ORM与缓存]
difficulty: intermediate
mastery: 60
---

# MyBatis-Plus使用层总览

> 一句话：MyBatis-Plus在MyBatis之上提供三层增强——BaseMapper省去手写CRUD，Wrapper用类型安全API替代XML动态SQL，IService在Service层提供链式操作；注解体系（@TableLogic/@TableField）实现声明式逻辑删除与审计字段填充。

## 一、BaseMapper：省去手写CRUD

继承 `BaseMapper<T>` 即可获得全套通用方法，无需为每个实体写 `insert`/`selectById`/`updateById`。

```java
public interface UserMapper extends BaseMapper<User> {}

userMapper.insert(user);
userMapper.selectById(1L);
userMapper.updateById(user);
userMapper.deleteById(1L);
```

## 二、Wrapper：类型安全的动态条件

用链式API构造WHERE条件，编译期检查字段名，重构时IDE自动追踪。

```java
// LambdaQueryWrapper：类型安全
List<User> list = userMapper.selectList(
    Wrappers.<User>lambdaQuery()
        .eq(User::getStatus, 1)
        .like(User::getName, "zhang")
        .ge(User::getCreateTime, startDate)
);

// 分页
Page<User> page = userMapper.selectPage(
    new Page<>(current, size), 
    queryWrapper
);
```

**与XML动态SQL的关系**：Wrapper最终仍生成带`?`占位符的预编译SQL，由MyBatis执行。SQL结构运行时决定，但注入风险被`#{}`机制隔离。

## 三、IService：Service层链式API

BaseMapper之上的薄层封装，提供`lambdaQuery()`/`lambdaUpdate()`链式操作，无需手动new Wrapper。

```java
// 查询
userService.lambdaQuery()
    .eq(User::getUsername, "张三")
    .list();

// 更新
userService.lambdaUpdate()
    .eq(User::getUsername, "王五")
    .set(User::getPhone, "NEW")
    .update();

// saveOrUpdate：id为null则INSERT，否则UPDATE
userService.saveOrUpdate(user);

// 批量插入，默认1000条一批
userService.saveBatch(users);
```

**继承结构**：
```java
public interface UserService extends IService<User> {}
@Service
public class UserServiceImpl extends ServiceImpl<MpUserMapper, User> implements UserService {}
```

**陷阱**：`@MapperScan("com.example.order")`会扫到IService接口，导致`Invalid bound statement`。必须精确指定包：`@MapperScan({"com.example.order.mapper"})`。

## 四、注解增强：声明式SQL改写

### @TableLogic（逻辑删除）

```java
@TableLogic
private Integer deleted;  // 0=正常, 1=删除
```

SQL自动改写：
- `deleteById(id)` → `UPDATE SET deleted=1 WHERE id=? AND deleted=0`
- `selectById(id)` → `SELECT ... WHERE id=? AND deleted=0`

**限制**：原生XML手写SELECT不受`@TableLogic`影响，需手动加`deleted=0`条件。

### @TableField(fill)（自动填充）

```java
@TableField(fill = FieldFill.INSERT)
private LocalDateTime createTime;

@TableField(fill = FieldFill.INSERT_UPDATE)
private LocalDateTime updateTime;
```

需实现MetaObjectHandler：

```java
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
    public void insertFill(MetaObject metaObject) {
        this.setFieldValByName("createTime", now, metaObject);
    }
    public void updateFill(MetaObject metaObject) {
        this.setFieldValByName("updateTime", now, metaObject);
    }
}
```

**踩坑**：`strictUpdateFill`在UPDATE时若字段已有值（INSERT阶段已填充）会跳过，导致`updateTime`不更新。解决：UPDATE用`setFieldValByName`无条件覆写。

**数据库时间 vs 应用时间**：
```java
@TableField(fill = FieldFill.INSERT_UPDATE, update = "CURRENT_TIMESTAMP")
private LocalDateTime updateTime;
```
`CURRENT_TIMESTAMP`是SQL标准，跨数据库统一用DB时钟；`LocalDateTime.now()`依赖服务器时钟。

## 五、其他增强特性

| 特性 | 说明 |
|------|------|
| **乐观锁** | `@Version`自动CAS更新 |
| **多租户** | `TenantLineInnerInterceptor`自动拼接`tenant_id` |
| **动态表名** | `DynamicTableNameInnerInterceptor`运行时切换表名 |
| **代码生成器** | AutoGenerator根据数据库表一键生成Entity/Mapper/Service/Controller |

## 六、架构位置

```
你的代码
   ↓
IService / BaseMapper（MP提供）
   ↓
MyBatis SqlSession（原生）
   ↓
MP拦截器链（分页、多租户、动态表名...）
   ↓
JDBC
```

**关键**：MP不破坏MyBatis扩展点，复杂SQL仍可手写XML或`@Select`。

## 七、性能与选型

| 维度 | 说明 |
|------|------|
| 运行时开销 | Wrapper条件构造在应用层完成，最终SQL仍由MyBatis执行，无额外DB交互 |
| 启动开销 | 解析实体注解略慢于纯MyBatis，可忽略 |
| 缓存 | 不改动MyBatis一/二级缓存机制 |

**选型**：国内Spring Boot新项目默认选MP，招聘与生态最完善；已有MyBatis项目可渐进引入，两者共存。

> ⚠️ 本笔记由3篇MP相关笔记合并而成，mastery 60为概念理解，待项目实战验证后更新

## 来源
- 项目：[[mybatis-sql-lab]]
- 对话：[[2026-05-28-MyBatis与MyBatis-Plus核心差异]]、[[2026-06-01-MP注解增强与IService]]
