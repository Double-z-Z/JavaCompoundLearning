---
type: emrg
id: EMRG-NIO网络编程
title: NIO网络编程
maturity: verified
created: 2026-04-21
updated: 2026-05-15
related_goals: [GOAL-Java核心深化]
subtopics:
  - "NIO核心机制"
  - "Netty框架"
  - "服务架构设计"
---

# EMRG-NIO网络编程

> 成熟度: 🟢 verified

## 一句话定义

Java NIO 与网络编程知识体系，涵盖从 BIO 阻塞模型到 NIO 多路复用、Netty 事件驱动框架，以及基于 Reactor 思想的响应式编程范式的完整网络 I/O 认知网络。

## 知识拓扑

[NIO 核心机制]
  ├─ [[BIO-BlockingIO]] — 阻塞模型对比基线
  ├─ [[NIO-Buffer]] — 带状态管理的智能数据容器
  ├─ [[NIO-Channel]] — 双向非阻塞 I/O 通道
  ├─ [[NIO-Selector]] — 多路复用器，单线程管理多连接
  ├─ [[NIO优雅关闭模式]] — 三阶段关闭协议
  └─ [[Socket-EOF-Semantics]] — TCP 流终止语义

[Netty 框架]
  ├─ [[Netty]] — 异步事件驱动网络框架总览
  ├─ [[Netty-Pipeline-事件机制]] — 事件传播与处理链
  └─ [[ChannelPromise]] — 异步操作结果承诺

[服务架构设计]
  ├─ [[服务启停设计模式]] — 生命周期管理
  ├─ [[状态机模式-服务生命周期]] — 状态驱动设计
  └─ [[线程池风格接口设计]] — 资源管理抽象

## 关键缺口（待补充）

- [ ] 粘包拆包 — TCP 字节流无消息边界的应用层处理（曾出现 [[MISTAKE-003-NIO-ByteBuffer-Mode]]）
- [ ] Boss-Worker 模型 — 主从 Reactor 线程分工
- [ ] OP_WRITE 事件处理 — 写半包与可写性管理
- [ ] 跨线程通信 — Selector wakeup 与队列协作
- [ ] Reactor 模式 — 高性能网络服务器核心架构模式
- [ ] epoll 机制 — Linux 多路复用底层实现

## 项目实战

| 项目 | 状态 | 关联笔记 |
|------|------|---------|
| [[bio-chatroom]] | ✅ 完成 | [[BIO-BlockingIO]] |
| [[nio-chatroom]] | ✅ 完成 | [[NIO-Buffer]], [[NIO-Selector]], [[NIO优雅关闭模式]] |
| netty-chatroom | ✅ 完成 | [[Netty]], [[Netty-Pipeline-事件机制]] |
| webflux-project | ✅ 完成 | [[Flux核心概念]], [[WebFlux-生命周期与多线程时序]] |

## 关联领域

- [[EMRG-Reactive响应式编程]] — Reactive 子领域已分裂为次级 EMRG（Flux/Reactor/WebFlux/背压）
- [[EMRG-并发编程]] — 线程模型、CompletableFuture、跨线程通信、Boss-Worker 架构
- [[EMRG-Sentinel-高级特性与生态]] — Reactor 流控与 Sentinel 背压协同
- [[EMRG-Redis]] — Netty 驱动的 Redis 客户端底层通信

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
- [[CompletableFuture]] → 归属 [[EMRG-并发编程]]，仅在 Netty 异步场景中被引用
- [[Maven-Surefire-Plugin]] → 归属工程工具，非 NIO 核心知识
- [[测试设计]] → 归属工程实践，非 NIO 核心知识
- [[2026-04-23-test-organization]] → 测试组织反思，非网络编程核心

#### 跨界枢纽（被多个 EMRG 引用）
- [[Netty]] — 同时被 [[EMRG-NIO网络编程]] 和 [[EMRG-Reactive响应式编程]] 引用（底层传输层）

### 涌现历史
- 2026-04-21: 作为早期 MOC 创建，覆盖 NIO 基础到 Netty 框架
- 2026-05-09~14: Reactive 系列笔记密集产出（6篇），关联至本 EMRG

### 成熟度说明

NIO 核心机制（Buffer/Channel/Selector）已通过 [[nio-chatroom]] 项目实战验证；Netty 框架（Pipeline/ByteBuf）已通过 netty-chatroom 项目验证；Reactive 编程（Flux/Reactor/WebFlux）已通过 webflux 实战验证。多篇文章 mastery ≥ 60，整体达到 verified。

### 检查点
- [ ] 子主题数: 4（frontmatter 记录），但实际知识节点 16+，超过 7 个概念阈值
- [x] 裂变执行: 2026-05-15 Reactive 响应式编程已分裂为 [[EMRG-Reactive响应式编程]]，本 EMRG 保留 NIO 核心与 Netty
- [x] 缺失链接: 02-Knowledge/nio/ 核心笔记已补充 `related_emrg: [EMRG-NIO网络编程]`
- [ ] 最后更新: 2026-05-15（在 90 天窗口内）
