---
type: atomic-note
id: CONCEPT-reactor-pattern-vs-reactivestreams
created: 2026-05-14
updated: 2026-05-14
tags: [reactor, reactive-streams, netty, webflux, 架构模式]
status: 🌿
mastery: 75
related_emrg: [EMRG-Reactive响应式编程, EMRG-Sentinel-高级特性与生态]
related_goal: [GOAL-Java核心深化]
---

# Reactor Pattern vs Reactive Streams vs Project Reactor

## 一句话定义

**Reactor Pattern** 是 Netty 的网络 IO 架构模式（单线程事件循环），**Reactive Streams** 是异步数据流交互规范（4 个接口 + 背压），**Project Reactor** 是 Spring 的 Java 实现库（Mono/Flux/Scheduler）；WebFlux 同时依赖三者协同工作。


## 核心理解

### 1. 三者辨析

| 维度 | Reactor Pattern | Reactive Streams | Project Reactor |
|------|----------------|------------------|-----------------|
| **本质** | 架构设计模式 | 异步数据流交互规范 | Java 实现库 |
| **层次** | 网络 IO 层 | 应用协议层 | 编程 API 层 |
| **核心组件** | EventLoop、Selector、Channel | Publisher、Subscriber、Subscription、Processor | Mono、Flux、Scheduler、Operators |
| **背压** | 无（靠 TCP 窗口） | 显式协议：`request(n)` | 实现：`Flux.receive()` / `MonoSendMany` |
| **代表实现** | Netty、Node.js | JDK 9 Flow API | Spring WebFlux、R2DBC |

### 2. 在 WebFlux 中的角色

```
HTTP 请求到达
    ↓
[Reactor Pattern] Netty EventLoop 处理 IO 事件（Boss/Worker）
    ↓
[Reactive Streams] WebFlux 通过 Publisher/Subscriber 协议处理请求体/响应体
    ↓
[Project Reactor] 业务代码用 Mono/Flux 组装操作符链，Scheduler 切换线程
    ↓
HTTP 响应写出
```

- **WebFilter 默认在 Netty EventLoop 上执行**，没有额外线程同步开销
- 只有显式调用 `publishOn`/`subscribeOn` 时，才会切换到 Scheduler Worker

### 3. 常见混淆点

- **"Reactor 是 Netty 的一部分"** → 错误。Project Reactor 是独立库，只是被 Spring 选为默认实现。
- **"Reactive Streams 就是 WebFlux"** → 错误。Reactive Streams 是规范，WebFlux 是框架，Project Reactor 是具体实现。
- **"用了 WebFlux 就等于用了 Reactor Pattern"** → 错误。WebFlux 可以用 Tomcat（Servlet 3.1 非阻塞 IO）作为底层，不一定用 Netty。


## 关键关联

- [[WebFlux-生命周期与多线程时序]] -- 关联原因：理解三类线程（Boss/Worker/Scheduler）的分工需要同时理解 Reactor Pattern（EventLoop）和 Project Reactor（Scheduler）的边界
- [[NIO-Selector]] -- 关联原因：Reactor Pattern 的核心就是 Selector + EventLoop，NIO 是 Netty 实现 Reactor Pattern 的基础
- [[Flux核心概念]] -- 关联原因：Project Reactor 的 Mono/Flux 是 Reactive Streams 规范的实现，理解规范接口才能理解操作符设计
- [[Reactor-背压与Netty协调]] -- 关联原因：Reactive Streams 的 `request(n)` 背压语义需要通过 Netty 的 `autoRead` / `isWritable()` 桥接到 TCP 层


## 我的误区与疑问

- ❌ 误区："Reactor Pattern = Reactive Streams = Project Reactor" → 三者是完全不同层次的概念
- ❌ 误区："HTTP 字节到达 = Publisher 发布数据" → 冷流是订阅后才生产，请求头到达只是网络层事件
- ❌ 误区："WebFlux 中发布-订阅分离不突出" → 框架隐式订阅了，不是不存在


## 深入思考

💡 如果 Tomcat 也实现了 Servlet 3.1 非阻塞 IO，为什么 WebFlux on Tomcat 仍然达不到 WebFlux on Netty 的性能？差距到底在哪里？


## 来源

- 对话：[[03-Practice/reflections/2026-05-14-Reactor-背压与WebFlux多线程-dialogue.md]]


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿 理解
- 更新记录：
  - 2026-05-14: mastery=75 (从对话中明确区分了三者的层次和职责，纠正了概念混淆)
