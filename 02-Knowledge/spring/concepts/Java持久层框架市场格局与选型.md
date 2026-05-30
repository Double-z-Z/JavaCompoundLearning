---
type: concept
created: 2026-05-28
updated: 2026-05-28
tags: [mybatis, 持久层, 架构, 选型]
related_emrg: [EMRG-ORM与持久层]
related_goal: [GOAL-ORM与缓存]
mastery: 60
---

# Java持久层框架市场格局与选型

> 一句话：中国 ~85-90% 新项目选 MyBatis-Plus，全球仍是 JPA/Hibernate 主导，选型应基于地域、团队、性能需求而非单纯流行度。

## 一、中国市场格局（2024-2025）

| 技术 | 市场占比（估算） | 典型场景 |
|---|---|---|
| **MyBatis / MyBatis-Plus** | **~85–90%** | 互联网业务系统、Spring Boot 新项目、CRUD 密集型应用 |
| **Spring Data JPA (Hibernate)** | **~5–8%** | 外企项目、快速原型、个人项目、部分传统企业 |
| **Hibernate 原生** | **<<3%** | 银行/政府等 10 年以上存量系统 |
| **JDBC / JDBC Template** | **~5%** | 性能极致敏感模块、简单工具类、遗留系统 |
| **jOOQ / QueryDSL** | **<<2%** | 需要类型安全 SQL 的复杂查询场景 |

> 注：国内部分技术博客称"新项目 99.9% 选 MyBatis-Plus"，该数据缺乏权威调研支撑，但 ~85-90% 的估算与主流招聘 JD 和社区讨论基本一致。

## 二、全球市场格局

**2014 年 RebelLabs 报告**（历史参考，已严重过时）：

| 技术 | 市场份额 |
|---|---|
| Hibernate | **67.5%** |
| Plain JDBC | **22%** |
| JDBC Template | **19.5%** |
| EclipseLink | **13%** |
| MyBatis | **6.5%** |
| jOOQ | **1.5%** |

**2023–2025 年全球趋势**：
- **Hibernate / Spring Data JPA**：在欧美企业级应用、Spring 生态中仍占主导，但份额较 2014 年有所下降。
- **MyBatis**：亚洲（尤其中国）主导，全球份额因中国开发者基数大而上升。
- **Spring Data JDBC**：作为"轻量版 JPA"在增长，适合不喜欢 Hibernate 复杂会话管理的开发者。
- **jOOQ**：在需要编译期类型安全 SQL 的项目中认可度提升，但总体仍属小众。

## 三、地域差异的根本原因

| 维度 | 中国/亚洲偏好 MyBatis | 欧美偏好 JPA/Hibernate |
|---|---|---|
| **SQL 控制** | 互联网高并发场景要求对 SQL 极致优化，手写 SQL 是刚需 | 企业级应用更关注开发效率和可移植性 |
| **数据库种类** | MySQL 绝对主导，无需担心多数据库迁移 | PostgreSQL、Oracle、SQL Server 多元，需要抽象层 |
| **团队规模** | 业务迭代快，MyBatis-Plus 的代码生成器能快速交付 | 长期维护的大型系统，偏好领域模型驱动 |
| **性能敏感** | 大流量下对 N+1、懒加载等 ORM 黑盒行为容忍度低 | 硬件资源相对充裕，更接受 ORM 的自动化权衡 |

## 四、选型决策框架

```
新项目（中国互联网场景）
  └─ 默认选择：MyBatis-Plus
      ├─ 招聘、社区资源、第三方插件生态最丰富
      └─ 已成为 Spring Boot 3 新项目的事实标准

跨地域协作或外企项目
  └─ 更安全的选择：Spring Data JPA
      └─ 全球开发者熟悉度更高

性能关键路径
  └─ 考虑 JDBC Template 或 jOOQ
      └─ 复杂查询场景 jOOQ 的类型安全优势显著

存量系统维护
  └─ Hibernate 仍大量存在于银行、政府等长周期项目中
```

## 五、关键洞察

1. **流行度 ≠ 适用性**：技术选型应结合具体行业（金融/互联网/政企）的招聘 JD 技术栈分布，这比全局统计更具参考价值。
2. **双极格局**：中国 MyBatis 主导，全球 JPA/Hibernate 仍是最大单一派系，两者形成"双极"格局。
3. **JDBC 原生不会消失**：在简单工具、批处理、极致性能场景仍有固定份额，但多数项目会至少使用 JdbcTemplate 或 MyBatis 做轻量封装。

## 关联知识

- [[MyBatis核心机制]] — MyBatis 缓存与动态 SQL 的完整机制（归属 EMRG-ORM与持久层）
- [[MyBatis与MyBatis-Plus核心差异]] — 两者定位关系与功能对比
- [[MyBatis缓存机制与生产实践]] — MyBatis 原生缓存的性能瓶颈与替代方案

## 来源

- 本次对话整理自 Kimi 对话记录（2026-05-28）
- 数据参考：国内技术社区 2024-2025 观察、RebelLabs 2014 报告、Josh Long Twitter 投票
