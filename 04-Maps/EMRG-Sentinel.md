---
type: emrg
id: EMRG-Sentinel
title: Sentinel流量治理网络
maturity: theoretical
maturity_evidence: "核心机制（架构/滑动窗口/熔断/热点/流量效果/自适应/恢复/上下文）已通过 redis-counter-service-webflux 和 spike-protection 项目验证；高级特性（注解/授权规则/集群限流）待探索"
created: 2026-05-11
updated: 2026-05-14
related_goals:
  - [[GOAL-Java核心深化]]
  - [[GOAL-SpringCloud微服务]]
subtopics:
  - 架构定位与设计哲学
  - Cache Line 性能优化
  - LeapArray 滑动窗口
  - 限流算法
  - 熔断机制
  - 热点参数限流
  - 流量控制效果
  - 系统自适应限流
  - 熔断恢复机制
  - 上下文传播
  - "@SentinelResource 注解"
  - 授权规则
  - 集群限流细节
---

# EMRG-Sentinel

> 成熟度: 🟡 theoretical
> 关联 GOAL: [[GOAL-Java核心深化]]、[[GOAL-SpringCloud微服务]]

## 一句话定义

Sentinel 是面向分布式服务架构的轻量级流量控制组件，以"本地优先、分层防护、优雅降级"为设计哲学，通过 LeapArray 滑动窗口、三层混合计数器及多策略流量控制实现纳秒级熔断与限流决策。

## 核心成员（原子笔记）

| 笔记 | mastery | 验证状态 | 关键链接 |
|-----|---------|---------|---------|
| [[Sentinel-核心架构]] | 80 | 🟢 | [[LeapArray-滑动窗口]]、[[Sentinel-熔断机制]] |
| [[LeapArray-滑动窗口]] | 60 | 🟢 | [[Sentinel-核心架构]]、[[Sentinel-流量控制效果]] |
| [[Sentinel-熔断机制]] | 55 | 🟡 | [[Sentinel-核心架构]]、[[Sentinel-自适应限流]] |
| [[Sentinel-热点参数限流]] | 55 | 🟡 | [[Count-Min-Sketch]]、[[W-TinyLFU]] |
| [[Sentinel-流量控制效果]] | 60 | 🟢 | [[LeapArray-滑动窗口]]、[[Sentinel-自适应限流]] |
| [[Sentinel-自适应限流]] | 60 | 🟢 | [[Sentinel-熔断机制]]、[[Sentinel-流量控制效果]] |
| [[Count-Min-Sketch]] | 50 | 🟡 | [[Sentinel-热点参数限流]] |
| [[W-TinyLFU]] | 55 | 🟡 | [[Sentinel-热点参数限流]] |

> 验证状态规则：mastery ≥ 60 → 🟢 verified；< 60 → 🟡 theoretical

## 边界声明

### 核心成员（纳入本 EMRG）
- [[Sentinel-核心架构]] — 架构定位、部署模式、监控机制
- [[LeapArray-滑动窗口]] — 核心数据结构、时间映射、Cache Line 优化
- [[Sentinel-熔断机制]] — 三种策略、悬挂请求、状态机
- [[Sentinel-热点参数限流]] — 三层混合架构、CMS、升降级
- [[Sentinel-流量控制效果]] — 直接拒绝、Warm Up、匀速排队
- [[Sentinel-自适应限流]] — 四大指标、滞后效应、状态机
- [[Count-Min-Sketch]] — CMS 数据结构、误差分析、空间效率
- [[W-TinyLFU]] — Doorkeeper 机制、衰减策略

### 边缘关联（链接但不纳入）
- `LongAdder` / `AtomicLong` — 归属 [[EMRG-并发编程]]
- `epoll` / `Netty` — 归属 [[EMRG-NIO网络编程]]

### 跨界枢纽（被多个 EMRG 引用）
- [[Count-Min-Sketch]] — 同时被缓存/Caffeine 知识域引用（GOAL-ORM与缓存）
- [[W-TinyLFU]] — 同时被缓存/Caffeine 知识域引用（GOAL-ORM与缓存）

## 知识拓扑

```
[Sentinel 流量治理]
  ├─ [[Sentinel-核心架构]]
  │   ├─ 本地优先 > 远程依赖
  │   ├─ 分层防护 > 单点保障
  │   └─ 优雅降级策略
  ├─ [[LeapArray-滑动窗口]]
  │   ├─ 环形数组 + 时间映射
  │   ├─ Cache Line 隔离（@Contended）
  │   └─ 窗口四态处理
  │   └─ 滑动窗口计数器（统计 + if 判断）
  ├─ [[Sentinel-熔断机制]]
  │   ├─ 慢调用比例 / 异常比例 / 异常数
  │   ├─ 悬挂请求问题
  │   ├─ CLOSED → OPEN → HALF-OPEN → CLOSED/OPEN
  │   └─ N=1 探测恢复策略
  ├─ [[Sentinel-热点参数限流]]
  │   ├─ Doorkeeper（布隆过滤器）
  │   ├─ Count-Min Sketch（概率统计）
  │   └─ Precise Counter（Top-K 精确限流）
  ├─ [[Sentinel-流量控制效果]]
  │   ├─ 直接拒绝（Default）
  │   ├─ Warm Up（预热，抛物线）
  │   └─ 匀速排队（RateLimiter，削峰填谷）
  ├─ [[Sentinel-自适应限流]]
  │   ├─ CPU / RT / 并发 / Load（OR 关系）
  │   └─ 高低双阈值防震荡
  └─ [[Sentinel-上下文传播]]
      └─ 入口资源名 / 调用来源 / 调用链父子关系
```

## 涌现历史

- **2026-05-11**: 因 Sentinel 对话密度溢出创建学习地图，覆盖 10 个核心话题、8 篇原子笔记，内部链接密度高
- **2026-05-13**: 核心机制已通过 `redis-counter-service-webflux` 与 `spike-protection` 项目实战验证，晋升为正式 EMRG
- **2026-05-14**: 完成 @SentinelResource 与 Reactor 集成深度探索，Sentinel-核心架构 mastery 提升至 80

## 检查点

- [ ] 子主题数: 13（超过 7，建议待高级特性完成后裂变为 `EMRG-Sentinel-基础机制` 与 `EMRG-Sentinel-高级特性`）
- [ ] 最后更新: 2026-05-14（超过 90 天则触发归档检查）
