---
type: atomic-note
id: CONCEPT-reactor-subscriber-chain
created: 2026-05-14
updated: 2026-05-14
tags: [reactor, subscriber, publisher, 响应式]
status: 🌿
mastery: 75
related_emrg: [EMRG-Reactive响应式编程, EMRG-Sentinel-高级特性与生态]
related_goal: [GOAL-Java核心深化]
---

# Reactor Subscriber 链式传递机制

## 一句话定义

Reactor 的 `subscribe()` 不是递归调用，而是沿着操作符链**从尾到头**传递订阅请求，建立 Subscriber 链和 Subscription 链的**两阶段协议**，实现异步非阻塞的数据流动。


## 核心理解

### 1. subscribe 的"伪递归"本质

```
订阅阶段（从尾到头）：
flatMap.subscribe(actual)
    → map.subscribe(wrapper1)
        → just.subscribe(wrapper2)
            → 数据源开始发射数据！

数据流动阶段（从头到尾）：
just.onNext("A")
    → mapSubscriber.onNext("A")
        → flatMapSubscriber.onNext("A!")
            → terminal.onNext("A!")
```

这不是递归，而是**对象引用链**的依次调用。每个操作符的 `subscribeActual()` 方法创建对应的 Subscriber 包装器，然后向上游传递。

### 2. 两阶段协议

| 阶段 | 方法 | 传递内容 | 方向 |
|------|------|---------|------|
| 订阅阶段 | `subscribe(subscriber)` | Subscriber 对象 | 下游 → 上游 |
| 协议建立阶段 | `onSubscribe(subscription)` | Subscription 对象 | 上游 → 下游 |

- **Subscriber 链**：在 `subscribe()` 阶段建立，每个 Subscriber 持有下游引用
- **Subscription 链**：在 `onSubscribe()` 阶段建立，通过包装传递

### 3. Publisher vs Subscriber 职责分离

| 角色 | 类名示例 | 是否持有 actual | 职责 |
|------|----------|----------------|------|
| Publisher | `MonoMap` | ❌ 不持有 | 定义操作符逻辑，创建 Subscriber |
| Subscriber | `MapSubscriber` | ✅ 持有 | 执行数据处理和传递 |

Publisher 是"做什么"的静态定义，Subscriber 是"怎么做"的动态执行。

### 4. onSubscribe 为什么是 Consumer（无返回值）

链条不是通过返回值传递，而是通过**对象引用**建立。`onSubscribe(Subscription s)` 的调用本身就是契约建立的信号，不需要返回值。


## 关键关联

- [[Flux核心概念]] -- 关联原因：Mono/Flux 的惰性求值依赖 Subscriber 链式传递机制，Assembly 阶段只创建 Publisher，Subscription 阶段才建立执行链条
- [[响应式生命周期信号]] -- 关联原因：Subscriber 的 onNext/onError/onComplete 是响应式生命周期的核心信号，理解链式传递才能理解信号如何在操作符间传播
- [[Sentinel-Entry生命周期与WebFlux集成]] -- 关联原因：Sentinel 通过 `SentinelReactorSubscriber` 装饰器模式插入到 Subscriber 链中，在 onComplete/onError 时完成 Entry 的 exit 统计
- [[WebFlux-生命周期与多线程时序]] -- 关联原因：理解 Assembly/Subscription/onSubscribe/Emission 四阶段中 Subscriber 的角色，以及 WebFlux 框架如何隐式触发 subscribe()


## 我的误区与疑问

- ❌ 误区："subscribe 是递归调用" → 不是递归，是链式传递订阅请求
- ❌ 误区："onSubscribe 需要返回值建立链条" → 链条通过对象引用建立，不需要返回值
- ❌ 误区："MonoMap 应该持有 actual" → MonoMap 是 Publisher，不持有运行时状态；actual 在内部 Subscriber 中
- ❌ 误区："WebFlux 开发需要手动 subscribe" → 框架在 `HttpServerOperations` 层自动隐式订阅


## 代码与实践

### Sentinel 与 WebFlux 的两种集成方式

```java
// 方式1：Filter 级别（全局限流）
// SentinelWebFluxFilter 在 WebFilter 链中创建 Entry

// 方式2：业务代码中（多级熔断）
public Mono<String> query(String key) {
    return redisTemplate.opsForValue().get(key)
        .transform(new SentinelReactorTransformer<>("redis-query"));
}
```

内部原理：通过装饰器模式包装下游订阅者，在 `onSubscribe` 时创建 `AsyncEntry`，在 `onComplete/onError` 时调用 `entry.exit()`。


## 深入思考

💡 为什么 Reactive Streams 规范要求 `subscribe()` 和 `onSubscribe()` 分开成两个阶段，而不是一步完成？


## 来源

- 对话：[[03-Practice/reflections/2026-05-14-Reactor-Subscriber链式传递-dialogue.md]]


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿 理解
- 更新记录：
  - 2026-05-14: mastery=75 (从对话中提炼出 subscribe 链式传递本质、两阶段协议、Publisher/Subscriber 职责分离)
