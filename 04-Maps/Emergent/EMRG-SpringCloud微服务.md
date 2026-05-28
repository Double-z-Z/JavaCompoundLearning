---
type: emrg
id: EMRG-SpringCloud微服务
title: Spring Cloud 微服务网络
maturity: theoretical
created: 2026-05-16
updated: 2026-05-16
related_goals: [GOAL-SpringCloud微服务]
subtopics:
  - "服务注册与发现"
  - "Nacos 架构与协议"
  - "负载均衡"
  - "配置管理"
  - "网关路由"
  - "熔断限流（Sentinel）"
  - "响应式编程（WebFlux）"
---

# EMRG-SpringCloud微服务

> 成熟度: 🟡 theoretical  
> 状态：概念框架建立中，待项目实战验证

## 一句话定义
Spring Cloud 微服务体系的核心认知模型——从服务注册发现到熔断限流的完整调用链路设计。

## 知识拓扑

[核心架构]
  ├─ [[服务注册与发现]]
  │   ├─ 关联 [[Sentinel-核心机制]] — 客户端侧熔断兜底
  │   ├─ 关联 [[多级缓存一致性]] — Push+Pull 混合模式
  │   └─ 关联 [[Spring配置管理]] — 自动配置触发链路
  ├─ [[Nacos架构与Distro协议]]
  │   ├─ 关联 [[Redis-Cluster模式]] — AP vs CP 对比学习
  │   ├─ 关联 [[Redis-主从复制]] — Raft 协议类比
  │   └─ 关联 [[Sentinel-核心机制]] — 兜底保护机制
  └─ [[客户端负载均衡]]
      ├─ 关联 [[Sentinel-核心机制]] — 客户端侧限流熔断
      └─ 关联 [[服务注册与发现]] — 依赖服务实例列表

[已有知识基础]
  ├─ [[SpringBoot自动配置原理]] — 自动配置完整推导（需求→设计→实现）
  ├─ [[Spring配置管理]] — 配置刷新与热加载机制
  ├─ [[WebFlux响应式编程]] — 响应式编程模型
  ├─ [[函数式路由vs注解式路由]] — 路由设计对比
  └─ [[Spring-MVC性能瓶颈]] — 性能优化经验

[关联领域]
  ├─ [[EMRG-Sentinel-核心机制]] — 熔断限流（已 verified ✅）
  ├─ [[EMRG-Reactive响应式编程]] — WebFlux 响应式体系
  └─ [[EMRG-Cache]] — 多级缓存策略

## 关键缺口（待补充）
- [x] ~~Spring Boot 自动配置原理~~ → 2026-05-29 [[SpringBoot自动配置原理]](65)
- [ ] Spring Cloud Gateway 网关层设计与实现
- [ ] OpenFeign 声明式服务调用
- [ ] 分布式事务（Seata）
- [ ] 链路追踪（Sleuth + Zipkin）
- [ ] 消息驱动（Stream + RocketMQ/Kafka）

## 项目实战
| 项目 | 状态 | 关联笔记 |
|------|------|---------|
| [[redis-counter-service-webflux]] | ✅ 完成 | WebFlux、Sentinel、RabbitMQ |
| [[redis-counter-service]] | ✅ 完成 | Spring Boot 基础 |

## 关联领域
- [[EMRG-Sentinel-核心机制]] — 熔断限流是微服务的安全屏障
- [[EMRG-Reactive响应式编程]] — WebFlux 是 Spring Cloud 响应式微服务的基础
- [[EMRG-Redis]] — Redis 作为分布式 Session 和缓存存储
- [[EMRG-NIO网络编程]] — Netty 是 WebFlux 的底层通信框架

---

## 🤖 AI 工作区

### 核心成员
```dataviewjs
const emrgId = dv.current().id;
dv.table(
  ["笔记", "mastery", "验证状态"],
  dv.pages()
    .where(p => p.related_emrg && p.related_emrg.includes(emrgId))
    .sort(p => p.mastery, 'desc')
    .map(p => [
      p.file.link,
      p.mastery ?? ' ',
      (p.mastery >= 60) ? '🟢' : '🟡'
    ])
);
```

### 边界声明

#### 边缘关联（链接但不纳入）
- [[Sentinel-核心机制]] → 归属 [[EMRG-Sentinel-核心机制]]（独立 EMRG，已 verified）
- [[WebFlux响应式编程]] → 归属 [[EMRG-Reactive响应式编程]]（独立 EMRG）
- [[Redis-数据类型与编码]] → 归属 [[EMRG-Redis]]

#### 跨界枢纽（被多个 EMRG 引用）
- [[Sentinel-核心机制]] — 同时被本 EMRG 和 [[EMRG-Sentinel-高级特性与生态]] 引用
- [[多级缓存一致性]] — 同时被本 EMRG 和 [[EMRG-Cache]] 引用

### 涌现历史
- 2026-05-16: 因 Gap 矩阵 G-SPR-02/03 缺口创建（涉及 3 篇新笔记，12 条链接）
- 2026-05-29: 补齐 G-SPR-01 缺口。新建 [[SpringBoot自动配置原理]](65)，覆盖需求→设计→实现完整推导链。修复 [[2026-05-15-SpringBoot自动配置与Starter-dialogue]] 孤儿引用。

### 成熟度说明
**当前状态：theoretical（理论阶段）**
- 已建立：服务注册发现、Nacos 架构、客户端负载均衡、Spring Boot 自动配置的完整概念框架
- 待验证：实际项目中使用 Nacos + Gateway + OpenFeign 的完整链路
- 已有实战基础：redis-counter-service-webflux 项目（WebFlux + Sentinel + RabbitMQ）
- 下一步目标：搭建包含注册中心、网关、多个微服务的 Demo 项目
- 4 篇 SpringCloud 专项笔记（服务发现55, Nacos50, 客户端LB50, 自动配置65），最高 mastery 65

### 检查点
- [x] 子主题数: 7（未超过 7，暂不触发裂变）
- [x] 最后更新: 2026-05-29（活跃）
- [ ] 成熟度升级条件：
  - [ ] 完成 Nacos 注册中心 Demo 项目
  - [ ] 掌握 Spring Cloud Gateway 配置
  - [ ] 至少 3 篇笔记 mastery ≥ 70
