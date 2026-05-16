---
type: emrg
id: EMRG-Reactive响应式编程
title: Reactive响应式编程
maturity: verified
created: 2026-05-15
updated: 2026-05-15
related_goals: [GOAL-Java核心深化]
parent_emrg: EMRG-NIO网络编程
subtopics:
  - "Reactor核心概念"
  - "响应式流规范"
  - "WebFlux集成"
  - "背压机制"
---

# EMRG-Reactive响应式编程

> 成熟度: 🟢 verified
> 父 EMRG: [[EMRG-NIO网络编程]]

## 一句话定义

基于 Reactive Streams 规范的异步数据流编程范式，通过 Publisher-Subscriber 协议、操作符链式组合和背压机制，实现端到端的非阻塞、事件驱动计算模型。

## 知识拓扑

[Reactive Streams 规范层]
  ├─ [[响应式生命周期信号]] — Publisher/Subscriber/Subscription/Processor 四接口协议
  └─ [[Reactor-Pattern-vs-ReactiveStreams-vs-ProjectReactor]] — 规范 vs 实现 vs 架构模式辨析

[Project Reactor API 层]
  ├─ [[Flux核心概念]] — Mono/Flux 抽象、惰性求值与操作符链
  └─ [[Reactor-Subscriber链式传递机制]] — 订阅链式传递与两阶段协议

[WebFlux + Netty 集成层]
  ├─ [[WebFlux-生命周期与多线程时序]] — Boss/Worker/Scheduler 三类线程端到端协作
  └─ [[Reactor-背压与Netty协调]] — item 级 request(n) 到字节级水位线的桥接

## 关键缺口（待补充）

- [ ] Reactor 操作符深度 — transform / compose / lift 等高级操作符原理
- [ ] Scheduler 线程模型 — elastic / parallel / single / immediate 区别与适用场景
- [ ] RSocket 协议 — 双向流、恢复续传的应用层协议
- [ ] Reactive Streams 规范源码 — Subscription / Publisher / Subscriber 接口契约实现

## 项目实战

| 项目 | 状态 | 关联笔记 |
|------|------|---------|
| webflux-project | ✅ 完成 | [[Flux核心概念]], [[WebFlux-生命周期与多线程时序]] |
| reactive-counter-service | ✅ 完成 | [[响应式生命周期信号]], [[Reactor-背压与Netty协调]] |

## 关联领域

- [[EMRG-NIO网络编程]] — 父 EMRG，提供 Netty 底层网络传输支撑
- [[EMRG-Sentinel-高级特性与生态]] — Reactor 流控与 Sentinel 背压协同、自适应限流
- [[EMRG-并发编程]] — Scheduler 线程池模型、CompletableFuture 与 Mono/Flux 对比

---

## 🤖 AI 工作区（以下由 Dataview 自动维护，请勿手动编辑）

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
- [[NIO-Selector]] — 归属 [[EMRG-NIO网络编程]]，仅作为 Reactive 底层多路复用实现
- [[Netty]] — 归属 [[EMRG-NIO网络编程]]，仅作为 WebFlux/Reactor-Netty 传输层
- [[NIO-Buffer]] — 归属 [[EMRG-NIO网络编程]]，ByteBuf 替代了 NIO ByteBuffer

#### 跨界枢纽（被多个 EMRG 引用）
- [[Reactor-Subscriber链式传递机制]] — 同时被 [[EMRG-Reactive响应式编程]] 和 [[EMRG-Sentinel-高级特性与生态]] 引用
- [[Reactor-Pattern-vs-ReactiveStreams-vs-ProjectReactor]] — 同时被 [[EMRG-Reactive响应式编程]] 和 [[EMRG-Sentinel-高级特性与生态]] 引用

### 涌现历史
- 2026-05-15: 从 [[EMRG-NIO网络编程]] 分裂而出，收纳 Reactive 系列 6 篇笔记（mastery 75~85）

### 成熟度说明

全部 6 篇核心笔记均已通过 webflux-project 和 reactive-counter-service 项目实战验证，mastery 范围 75~85，无纯理论笔记。背压机制已在生产级流量控制场景中验证。

### 检查点
- [ ] 子主题数: 4（在 7 阈值内）
- [ ] 最后更新: 2026-05-15（在 90 天窗口内）
