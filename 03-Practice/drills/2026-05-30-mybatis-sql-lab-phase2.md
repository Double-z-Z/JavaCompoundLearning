---
title: mybatis-sql-lab-phase2
date: 2026-05-30
tags:
  - orm
  - mybatis
  - dynamic-sql
  - drill
mastery: 🍎 应用 (60)
related_project: "[[mybatis-sql-lab]]"
related_goal: "[[GOAL-ORM与缓存]]"
related_emrg: [EMRG-ORM与持久层]
---

# Phase 2: 动态 SQL 全覆盖

## 验证结果

**27 测试，0 失败**（UserMapper 9 + OrderMapper 18）

## 覆盖的动态 SQL 标签

### `<sql>` + `<include>` — 列名复用

```xml
<sql id="orderColumns">
    o.id, o.user_id, o.order_no, o.total_amount, o.status, o.created_at
</sql>

<select id="selectById" resultType="Order">
    SELECT <include refid="orderColumns"/> FROM orders o WHERE o.id = #{id}
</select>
```

价值: 修改列名只需改一处，避免 N 个查询里重复写。

### `<where>` + `<if>` — 条件组合

替代 `WHERE 1=1` 的做法。`<where>` 自动去除第一个多余的 `AND`/`OR`。

```xml
<where>
    <if test="status != null and status != ''">
        AND o.status = #{status}
    </if>
    <if test="minAmount != null">
        AND o.total_amount >= #{minAmount}
    </if>
</where>
```

### `<choose>/<when>/<otherwise>` — 互斥分支

安全排序白名单的关键标签:

```xml
<choose>
    <when test="orderBy == 'price'">price</when>
    <when test="orderBy == 'price_desc'">price DESC</when>
    <otherwise>id</otherwise>  <!-- 兜底，防注入 -->
</choose>
```

> [!important] 安全排序
> `${}` 有 SQL 注入风险，必须用 `<choose>` 做白名单校验，不用传入的字符串直接拼接。

### `<foreach>` — 批量操作

IN 查询:

```xml
WHERE o.id IN
<foreach collection="ids" item="id" open="(" separator="," close=")">
    #{id}
</foreach>
```

批量插入:

```xml
INSERT INTO orders (...) VALUES
<foreach collection="orders" item="order" separator=",">
    (#{order.userId}, #{order.orderNo}, ...)
</foreach>
```

> [!warning] 空集合陷阱
> `<foreach>` 在空集合时生成 `IN ()` 导致 SQL 语法错误，调用方必须提前检查。

### `<set>` + `<if>` — 动态更新

只更新非 null 字段，自动处理尾部逗号:

```xml
UPDATE orders
<set>
    <if test="status != null">status = #{status},</if>
    <if test="totalAmount != null">total_amount = #{totalAmount},</if>
</set>
WHERE id = #{id}
```

### `<trim>` — 自定义裁剪

功能与 `<where>` 等价，但更灵活:

```xml
<trim prefix="WHERE" prefixOverrides="AND |OR ">
    <!-- 条件 -->
</trim>
```

### `<bind>` — OGNL 变量绑定

OGNL 中 `+` 对字符串拼接的支持因版本而异，本项目中改用 SQL 的 `||` 拼接。

### 窗口函数 + 聚合 — 直接透传

MyBatis 不解析 SQL 语义，任何数据库原生能力直接可用:

```sql
SELECT u.username, COUNT(o.id) AS order_count,
       ROW_NUMBER() OVER (ORDER BY SUM(o.total_amount) DESC) AS rank
FROM users u LEFT JOIN orders o ...
GROUP BY u.id
```

### LIMIT/OFFSET 分页

```xml
SELECT ... LIMIT #{limit} OFFSET #{offset}
```

## 关键发现

1. **动态 SQL 是在运行时解析的**，每次调用才生成 BoundSql
2. **`<where>` 和 `<trim>` 本质上等价**，区别在于灵活性
3. **`${}` 唯一的合法用途是动态排序/分组**，但必须加 `<choose>` 白名单
4. **MyBatis 对 SQL 特性的支持边界 = 数据库支持边界**，因为它不解析语义
