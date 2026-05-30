---
type: project
id: PROJECT-mybatis-sql-lab
aliases:
  - PROJECT-mybatis-sql-lab
tags:
  - orm
  - mybatis
  - postgresql
status: active
mastery: 0
created: 2026-05-30
related_emrg:
  - "[[EMRG-ORM与持久层]]"
related_goal:
  - "[[GOAL-ORM与缓存]]"
---

# mybatis-sql-lab

> 项目目标：通过订单管理系统，覆盖 MyBatis 全部 SQL 编写方式（静态/动态/关联映射/存储过程），建立"看一眼业务需求就能写出对应 MyBatis SQL"的肌肉记忆
> 项目类型：学习验证型


## 涉及知识点

| 知识点 | 在项目中的角色 | 相关练习 |
|-------|--------------|---------|
| [[MyBatis一级缓存]] | 会话内重复查询优化 | [[练习记录-Phase1]] |
| [[MyBatis二级缓存]] | Mapper 级共享缓存 | [[练习记录-Phase2]] |
| 动态 SQL（if/where/foreach/choose/set/trim） | 条件查询核心 | [[练习记录-Phase2]] |
| ResultMap 关联映射（一对一/一对多） | 多表查询结果组装 | [[练习记录-Phase3]] |
| #{} 与 ${} 区别 | SQL 注入安全边界 | [[练习记录-Phase1]] |
| 存储过程调用 | 复杂业务逻辑封装 | [[练习记录-Phase3]] |


## 架构设计

### 项目结构
```
mybatis-sql-lab/
├── pom.xml
├── src/main/java/com/example/order/
│   ├── model/                    # POJO: User, Product, Order, OrderItem
│   ├── mapper/                   # Mapper 接口（UserMapper, ProductMapper）
│   └── util/
│       └── SqlSessionUtil.java   # SqlSessionFactory 单例
├── src/main/resources/
│   ├── mybatis-config.xml        # 全局配置（最小化）
│   ├── logback.xml               # SQL 日志（DEBUG 级别）
│   ├── db/migration/             # 建表 + 种子数据
│   └── com/example/order/mapper/ # Mapper XML（必须与接口同路径！）
│       ├── UserMapper.xml
│       └── ProductMapper.xml
└── src/test/java/com/example/order/
    └── UserMapperTest.java       # Phase 1: 9 个测试，全部通过
```

### 领域模型
```
User  1 ──< N Order
Order 1 ──< N OrderItem
OrderItem N >── 1 Product
```

### 关键设计决策
- **PostgreSQL** 而非 H2 内存库：模拟真实环境，触发网络往返，验证缓存生效
- **Maven 项目**：匹配学习者环境
- **无 Spring**：纯 MyBatis SqlSession，聚焦 MyBatis 本身，干扰最小
- **Docker 部署 PG**：快速启停，数据隔离


## 实现阶段

### Phase 1: 环境搭建 + 静态 CRUD ✅
**目标**: MyBatis 跑通 SELECT/INSERT/UPDATE/DELETE，理解 #{} 参数绑定
**验证结果**: ✅ 9/9 测试通过
**关联练习**: [[2026-05-30-mybatis-sql-lab-phase1]]
**踩坑**: XML 放在 `resources/mapper/` 与 `<package>` 扫描路径不匹配 → 移到 `resources/com/example/order/mapper/`
**覆盖**: insert(自增ID回填), selectById/All, selectByUsername(单参数), selectByEmailAndPhone(@Param), selectByMap(Map传参), update, delete, ${}排序, L1缓存验证

### Phase 2: 动态 SQL 全覆盖 ✅
**目标**: 掌握 `<if>`, `<where>`, `<foreach>`, `<choose>`, `<set>`, `<trim>`，能用动态 SQL 写出查询条件组合
**验证结果**: ✅ 18/18 测试通过（1 个 BATCH 演示跳过）
**关联练习**: [[2026-05-30-mybatis-sql-lab-phase2]]
**覆盖**: `<sql>/<include>`(列名复用), `<where>/<if>`(多条件), `<choose>/<when>/<otherwise>`(安全排序白名单), `<foreach>`(IN+批量插入), `<set>`(动态更新), `<trim>`(自定义裁剪), `<bind>`(LIKE模式), 窗口函数, LIMIT/OFFSET 分页
**踩坑**: `<bind>` 的 OGNL 字符串拼接因版本而异 → 改用 SQL `||` 拼接

### Phase 3: ResultMap 关联映射 + 存储过程 ✅
**目标**: 掌握一对一/一对多映射，理解 N+1 问题，会写简单存储过程
**验证结果**: ✅ 7/7 测试通过
**关联练习**: [[2026-05-30-mybatis-sql-lab-phase3]]
**覆盖**: `<association>`(一对一嵌套结果), `<collection>`(一对多嵌套结果), 完整嵌套(association+collection), 嵌套查询(N+1演示), `<id>`分组机制, 存储过程(CALLABLE)
**踩坑**: PG 存储过程用原生 `CALL` 而非 JDBC `{CALL}` 转义语法；`<id>` 字段 NULL 时 MyBatis 不创建对象（跳过空行）


## 项目特有的坑与解决方案

### 坑1: Mapper XML 路径与 `<package>` 扫描不匹配
**现象**: `Invalid bound statement (not found)`，全部测试报错
**根因**: XML 放在 `resources/mapper/`，但 `<package name="com.example.order.mapper"/>` 要求 XML 与接口在同一 classpath 路径
**解决**: 将 XML 移到 `resources/com/example/order/mapper/`
**预防**: Maven 项目下，resources 中的文件路径会映射到 classpath 根目录；`<package>` 扫描时按接口全限定名找同名 XML

---

---

## Phase 4-6: MyBatis Plus 深入学习

> 项目新增 `com.example.order.mp` 包，与原生 MyBatis 代码并行共存，方便对比。

### 学习路径总览

```
                        ┌─ 对比总结（Phase 6）─┐
                        ↓                      ↓
功能层（Phase 4）  →  原理层（Phase 5）  →  完整实践（Phase 6）
"能用什么"            "为什么这样设计"       "原生 vs MP 同一场景"
```

### Phase 4: MP 功能层 ✅
**目标**: 用 MP API 替代单表 CRUD 和条件查询
**验证结果**: ✅ 11/11 测试通过
**覆盖**: BaseMapper(insert/selectById/selectList/updateById/deleteById), QueryWrapper(eq/like/between/orderBy), LambdaQueryWrapper, 分页插件(Page+selectPage), 原生 vs MP 对比
**踩坑**: `mybatis-plus` 完整包绑定了 Spring → 拆成 `mybatis-plus-core`+`mybatis-plus-extension`+`spring-core`；非 Spring 项目必须用 `MybatisSqlSessionFactoryBuilder`

### Phase 5: MP 原理层 ✅
**目标**: 验证 SqlInjector、Wrapper SQL 生成、分页拦截、共存机制
**验证结果**: ✅ 9/9 测试通过
**覆盖**: SqlInjector 注入验证(无 XML 执行 selectById/insert/selectCount), Wrapper 生成 LIKE/嵌套/空条件, 分页拦截(COUNT+LIMIT), MP 与原生 XML 共存

### Phase 6: MP 对比实践 ✅
**目标**: 明确 QueryWrapper 能力边界（什么能替代、什么不能）
**验证结果**: ✅ 14/14 测试通过
**覆盖**: 比较运算/模糊查询/IN/GROUP BY+HAVING/多列排序, 嵌套条件(and/or/nested), 条件式 eq, UpdateWrapper, 子查询(inSql/exists/apply), @Select 注解做简单 JOIN

**核心认知**: QueryWrapper 覆盖范围 = WHERE 子句全部逻辑。不能替代的是 `<resultMap>`/`<association>`/`<collection>`（嵌套对象映射必须回退到 XML）、`<sql>`/`<include>`（列名复用）、`<choose>`/`<when>`/`<otherwise>`（互斥分支用 Java if-else）

## 跨概念综合洞察

### MyBatis 设计哲学的反复验证
整个项目过程反复验证了一个核心判断：**MyBatis 是 SQL 优先的工具，不是自动化框架**。
- 一级缓存默认全缓存但给了 `useCache`/`flushCache` 精确控制
- `autoMappingBehavior=PARTIAL` 在嵌套场景关自动映射——不是技术限制，是安全选择
- ResultMap 的 `<association>`/`<collection>` 要求显式声明映射规则
- MyBatis Plus 的 QueryWrapper 覆盖 WHERE 但不碰 ResultMap

### MP vs 原生的分层
```
BaseMapper    — 替 XML 单表 CRUD
QueryWrapper  — 替 XML <where>+<if>
UpdateWrapper — 替 XML <set>+<if>
分页插件      — 替 手写 LIMIT + COUNT
XML ResultMap — MP 不碰，依然是嵌套对象映射的唯一方式
```

### 非 Spring 环境使用 MP
`mybatis-plus` 完整包假定 Spring Boot 存在。拆成 core+extension+spring-core 三个依赖，配合 `MybatisSqlSessionFactoryBuilder` 即可。


## 相关链接
- 主题地图: [[EMRG-ORM与持久层]]
- 学习目标: [[GOAL-ORM与缓存]]


---
📊 **项目完成度**: 100% (6/6 Phase)
🎯 **核心收获**: 从零搭建纯 MyBatis 项目 → 动态 SQL → ResultMap → MyBatis Plus 功能/原理/对比，68 测试 0 失败
🔗 **关联练习数**: 6（Phase1-6）
📈 **涉及知识点掌握度提升**: ORM → 75
