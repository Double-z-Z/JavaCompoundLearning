---
type: reflection
date: 2026-05-14
topic: Reactor 背压机制、WebFlux 多线程生命周期、transform 操作符本质
---

# 对话反思

## 核心收获

### 1. transform vs transformDeferred 的本质区别
- `transform` = Assembly Time 立即执行，所有订阅者共享结果 Publisher（编译期宏替换）
- `transformDeferred` = Subscription Time 延迟执行，每次订阅动态生成新 Publisher（运行时动态代理）
- 核心签名 `Function<Mono<T>, Publisher<V>>` 的输入是**整条管道**，不是管道里的数据

### 2. Reactor Pattern vs Reactive Streams vs Project Reactor 三者辨析
- **Reactor Pattern** = Netty 的网络 IO 架构模式（单线程事件循环）
- **Reactive Streams** = 异步数据流交互规范（4 个接口 + 背压）
- **Project Reactor** = Spring 的 Java 实现库（Mono/Flux/Scheduler）
- WebFlux 中 WebFilter 默认就在 Netty EventLoop 上跑，没有额外线程同步开销

### 3. WebFlux 完整生命周期（四阶段 + 三类线程）
- **Boss 线程**：只 accept 连接
- **Worker EventLoop**：HTTP 协议编解码 + 流水线 Assembly/Subscription + 最终 Channel 写入
- **Scheduler Worker**：只处理显式切出的阻塞/CPU 密集型任务
- Assembly 只创建对象，不触发回调；真正的订阅由 Reactor Netty 隐式触发

### 4. Reactor 背压与 Netty 的协同
- **读方向**：`FluxReceive` 用 `receiverDemand` 控制 Netty `autoRead`，下游慢则 TCP 窗口收缩
- **写方向**：`MonoSendMany` 检查 `channel.isWritable()`，只有 Netty 吃得下时才向上游 `request`
- HTTP 请求头无背压（必须一次性读完），请求体有完整背压
- item 级 `request(n)` 通过 `DataBuffer` 字节数映射到 Netty 字节级缓冲水位

### 5. Tomcat vs Reactor Netty 背压哲学差异
- Tomcat："堵水管"——下游慢 → 线程阻塞/TCP 缓冲满 → 客户端受限（被动、粗粒度、以线程为代价）
- Reactor Netty："拉链条"——下游 `request(n)` → 上游按需生产 → `autoRead`/`isWritable()` 执行（主动、细粒度、无阻塞代价）

## 思维误区纠正

- ❌ 误区："HTTP 字节到达 = Publisher 发布数据"。纠正：冷流是订阅后才生产，请求头到达只是网络层事件。
- ❌ 误区："onSubscribe 是线程切换管理器"。纠正：onSubscribe 是背压契约，线程切换只是 publishOn/subscribeOn 的副作用。
- ❌ 误区："每次请求创建操作符链违背复用原则"。纠正：操作符实例持有请求级状态（如 MonoFlatMap 的内部队列），必须隔离；复用的是函数逻辑和线程池。
- ❌ 误区："WebFlux 中发布-订阅分离不突出"。纠正：框架隐式订阅了，不是不存在。

## 关联笔记

- [[Flux核心概念]]
- [[响应式生命周期信号]]
- [[WebFlux-生命周期与多线程时序]]
- [[Reactor-背压与Netty协调]]

