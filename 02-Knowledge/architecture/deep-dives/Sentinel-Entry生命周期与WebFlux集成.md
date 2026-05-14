---
type: deep-dive
id: DEEPDIVE-sentinel-entry-lifecycle
created: 2026-05-13
tags:
  - sentinel
  - webflux
  - entry
  - reactor
mastery: 80
source: "[[03-Practice/reflections/2026-05-13-Sentinel-Entry生命周期-dialogue.md]]"
related_emrg: [[EMRG-Sentinel]]
related_goal: []
---

# Sentinel Entry 生命周期与 WebFlux 集成

## 一句话定义

Entry 是 Sentinel 流量控制的最小执行单元，代表一次资源访问的"门票+手环"；在 WebFlux 中，Sentinel 通过 `CoreSubscriber` 深度 Hook Reactor 的订阅生命周期，实现对异步流的精确统计与优雅退出。

## Entry 的本质：门票 + 手环

| 阶段 | 游乐园类比 | Sentinel API | 作用 |
|------|-----------|-------------|------|
| 买票检票 | 到门口检查是否限流 | `SphU.entry(resourceName)` | 限流/熔断判决，创建 Entry |
| 游玩 | 玩项目 | 执行业务逻辑 | 无 Sentinel 介入 |
| 交回手环 | 出园时扫描记录 | `entry.exit()` | 更新 RT、success/exception 到 LeapArray |

## 同步 Entry 的生命周期

```java
try (Entry entry = SphU.entry("hello")) {
    // 业务逻辑
    return "Hello";
} catch (BlockException ex) {
    return "Blocked";
}
// try-with-resources 自动调用 entry.exit()
```

## 异步 AsyncEntry 与 WebFlux 集成

在 WebFlux 中，请求可能跨越多个线程（EventLoop -> 业务线程池 -> Redis 线程），同步的 `try-with-resources` 无法工作。Sentinel 通过 `SentinelReactorSubscriber` 深度集成：

```java
class SentinelReactorSubscriber<T> implements CoreSubscriber<T> {
    private AsyncEntry entry;
    
    @Override
    public void onSubscribe(Subscription s) {
        // 从 Reactor Context 恢复或创建 AsyncEntry
    }
    
    @Override
    public void onComplete() {
        entry.exit();  // 统计为 success
    }
    
    @Override
    public void onError(Throwable t) {
        Tracer.traceEntry(t, entry);  // 先标记异常
        entry.exit();                 // 统计为 exception
    }
}
```

**关键设计**：不是简单的 `doFinally`，而是分别 Hook `onComplete` / `onError`，确保业务异常被正确计入熔断统计。

## onCancel 的设计取舍

源码验证发现：`onCancel`（客户端断开）直接调用 `entry.exit()`，**不标记异常**。

### 统计失真

| 信号类型 | Sentinel 统计 | 业务实际 |
|---------|--------------|---------|
| `onComplete` | success | 成功返回 |
| `onError` | exception | 业务异常 |
| `onCancel` | **success** | **客户端断开，业务可能未完成** |

后果：
- 异常率被低估
- 平均 RT 被拉高（用户等待后取消）
- 系统过载时大量 CANCEL 反而让熔断器**迟迟不触发**

### 为什么 Sentinel "装傻"？

1. **取消 != 业务错误**：用户刷新页面不代表服务端 bug
2. **无法判断执行阶段**：CANCEL 可能发生在第 1 个 `map` 或第 20 个 `flatMap`
3. **保守策略**：宁可误算成功，不要误杀正常请求（符合"模糊但稳定"哲学）

## 工程实践：分层监控策略

Sentinel 负责快速判决，Prometheus 负责精确观测：

```java
.doFinally(signalType -> {
    if (signalType == SignalType.CANCEL) {
        metrics.incrementCancelCounter(resourceName); // Prometheus
    }
    entry.exit(); // Sentinel 继续"装傻"
});
```

告警规则：
- **Sentinel 熔断 OPEN** -> 自动保护
- **CANCEL 率 > 30% 持续 1 分钟** -> 人工介入（接口变慢导致用户流失）

## 关键关联

- [[Sentinel-核心架构]] -- 关联原因：Entry 是 Sentinel 流量判决的最小单元，其生命周期设计体现了"本地优先、分层防护"的架构哲学
- [[LeapArray-滑动窗口]] -- 关联原因：`entry.exit()` 最终将 RT、success/exception 写入 LeapArray 的时间窗口，是统计数据的唯一入口
- [[Sentinel-熔断机制]] -- 关联原因：CANCEL 被记为成功会导致熔断器在客户端大量刷新时迟迟不触发，需配合外部监控补偿

## @SentinelResource 在 WebFlux 下的正确使用

### 默认 AOP 的局限性

`SentinelResourceAspect` 基于同步 `SphU.entry()` + `@Around` AOP：

```java
@Around("@annotation(sentinelResource)")
public Object invoke(ProceedingJoinPoint pjp) {
    Entry entry = SphU.entry(resourceName);  // 同步 entry
    try {
        return pjp.proceed();               // 返回 Mono（此时流未执行）
    } finally {
        entry.exit();                        // 方法返回即 exit
    }
}
```

在 WebFlux 下，方法返回 `Mono` 时流**尚未订阅**，`entry.exit()` 已经执行。结果是：
- Sentinel 统计了一个"0ms 完成的假请求"
- 真正的 Redis 查询发生在 `exit()` 之后，完全未被统计

### 正确方案：流事件绑定

使用 `AsyncEntry` + `Mono.transformDeferred`，将 Entry 生命周期绑定到 Reactor 流事件：

```java
public Mono<String> queryWithSentinel(String key) {
    return redisTemplate.opsForValue().get(key)
        .transform(new SentinelReactorTransformer<>("redis-query"));
}
```

内部原理：
- `onSubscribe` 时创建 `AsyncEntry`
- `onComplete` 时 `entry.exit()`（success）
- `onError` 时 `Tracer.traceEntry` + `entry.exit()`（exception）
- `onCancel` 时 `entry.exit()`（Sentinel 记为 success，需配合 Prometheus 补偿）

### 手动绑定模式（无官方 Transformer 时）

```java
return Mono.deferContextual(ctx -> {
    AsyncEntry entry = SphU.asyncEntry("redis-query");
    return redisTemplate.opsForValue().get(key)
        .doOnSuccess(v -> entry.exit())
        .doOnError(e -> {
            Tracer.traceEntry(e, entry);
            entry.exit();
        })
        .doOnCancel(() -> entry.exit());
});
```

**核心原则**：`AsyncEntry` 的判决仍是同步的，但 `exit()` 被延迟到流的终止事件，实现与异步生命周期的对齐。

## 来源

- 对话：[[03-Practice/reflections/2026-05-13-Sentinel-Entry生命周期-dialogue.md]]
