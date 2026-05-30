---
title: mybatis-sql-lab-phase3
date: 2026-05-30
tags:
  - orm
  - mybatis
  - resultmap
  - stored-procedure
  - drill
mastery: 🌿 理解 (55)
related_project: "[[mybatis-sql-lab]]"
related_goal: "[[GOAL-ORM与缓存]]"
related_emrg: [EMRG-ORM与持久层]
---

# Phase 3: ResultMap 关联映射 + 存储过程

## 验证结果

**7/7 测试通过**

## ResultMap 基础

### `<id>` vs `<result>`

| 元素 | 用途 |
|------|------|
| `<id>` | 主键列。MyBatis 用它判断两行是否属于同一个对象（`<collection>` 分组依赖此字段） |
| `<result>` | 普通列映射 |

### `<resultMap extends="...">`

继承已有的 resultMap，避免重复写列映射：

```xml
<resultMap id="orderBase" type="Order">
    <id property="id" column="id"/>
    <result property="orderNo" column="order_no"/>
    <!-- ... -->
</resultMap>

<resultMap id="orderWithUser" type="Order" extends="orderBase">
    <association .../>
</resultMap>
```

## 关联映射

### `<association>` — 一对一

一条 SQL 做 JOIN，用列别名区分子表列：

```xml
<association property="user" javaType="User" resultMap="userResult"/>
```

对应 SQL：

```sql
SELECT o.*, u.id AS u_id, u.username AS u_username, ...
FROM orders o LEFT JOIN users u ON o.user_id = u.id
```

### `<collection>` — 一对多

```xml
<collection property="items" ofType="OrderItem" resultMap="orderItemResult"/>
```

关键：ORDER BY 子表排序 + `<id>` 字段用于分组。

### 嵌套查询 (Nested Select) — N+1 问题

```xml
<association property="user" javaType="User"
             column="user_id" select="selectUserById"/>
```

1 个 Order → 1 条 SQL 查 Order + 1 条 SQL 查 User = 2 条；
N 个 Order → 1 + N 条 = N+1 问题。

## 存储过程

PG 的存储过程用原生 `CALL` 语法（不用 JDBC `{CALL}` 转义）：

```xml
<update id="callUpdateOrderStatus" statementType="CALLABLE">
    CALL sp_update_order_status(#{orderId, mode=IN}, #{newStatus, mode=IN})
</update>
```

PG 函数 vs 过程：
- FUNCTION: 有返回值，可嵌入 SELECT
- PROCEDURE (PG 11+): 无返回值，用 CALL 调用，支持事务控制

## 踩坑

1. PG 存储过程不能用 `{CALL ...}` 包裹 → 用原生 `CALL`
2. `<id>` 列值为 NULL 时 MyBatis 不创建该对象（跳过空行，正确处理 LEFT JOIN 无匹配）
