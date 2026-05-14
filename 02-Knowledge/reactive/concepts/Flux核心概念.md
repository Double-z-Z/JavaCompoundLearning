---
type: atomic-note
id: CONCEPT-flux-mono-core
created: 2026-05-07
updated: 2026-05-09
tags: [reactor, publisher, 操作符, 生命周期]
related_emrg: [EMRG-Spring性能优化]
related_goal: [GOAL-Java核心深化]
updated: 2026-05-14
mastery: 80
source: "[[03-Practice/reflections/2026-05-09-webflux-operators-dialogue.md]]"
---

# Flux/Mono 核心概念

## 一句话定义

**Flux = 一个可以发射 0 个、1 个或多个元素的"数据流管道"**
**Mono = 一个可以发射 0 或 1 个元素的"数据流管道"**

## 核心理解

### 生活类比

想象一个 **自动售货机出货口**：

```
传统方式（同步/命令式）：
你投币 → 等待 → 拿到商品（一次只能处理一个顾客，线程被占用）

Flux 方式（响应式/声明式）：
你投币 → 售货机开始工作 → 商品一个个出来（可以同时服务多个顾客）
```

| 场景 | 传统方式 | Flux |
|------|---------|------|
| 取商品 | 等待全部完成 | 商品出来一个就给你一个 |
| 出错 | 整个流程中断 | 可以继续处理后续的 |
| 多人使用 | 排队等待 | 各自独立通道 |

### 三种结果类型

Reactor 有两个核心类型：

| 类型 | 发射数量 | 类比 | 典型场景 |
|------|----------|------|----------|
| **Mono\<T\>** | 0 或 1 个 | **快递包裹**（要么有，要么没有） | 扣减库存返回单个结果、查询详情 |
| **Flux\<T\>** | 0 到 N 个 | **流水线产品**（一批批出来） | 查询列表、事件流、实时数据推送 |

### 核心：信号（Signals）

Flux 通过三种"信号"与订阅者通信：

```
Flux 数据流：
    ┌─── onNext("A") ──┐
    │                   │
    ├─── onNext("B") ──┤  ← 正常数据（可以有 0~N 个）
    │                   │
    ├─── onNext("C") ──┘
    │
    └─── onComplete()     ← 完成（可选，表示正常结束）
    
    或者
    
    ┌─── onNext("A") ──┐
    │                   │
    └─── onError(e) ───┘  ← 错误（一旦出错就终止）
```

**规则**：
- `onNext` 可以调用 0 次或多次
- `onComplete` 和 `onError` 只能调用其中一个（互斥）
- 一旦发出完成或错误信号，流就结束了

### 关键特性：惰性求值

```java
// 这行代码不会立即执行！只是定义了"怎么做"
Flux<Integer> numbers = Flux.range(1, 100)
    .map(n -> n * 2)
    .filter(n -> n > 50);

// 只有调用 subscribe() 时才开始执行
numbers.subscribe(System.out::println);
```

**好处**：
- **声明式编程**：先定义"做什么"，再决定"什么时候做"
- **支持组合和复用**：可以在不同场景复用同一个流定义
- **按需消耗资源**：只有真正需要时才执行计算

## 代码示例

### 基本 Flux 操作

```java
// 1. 创建 Flux（发射 3 个元素）
Flux<String> flux = Flux.just("A", "B", "C");

// 2. 订阅并消费（触发执行）
flux.subscribe(
    item -> System.out.println("收到: " + item),   // onNext: 处理每个元素
    error -> System.out.println("错误: " + error), // onError: 处理错误
    () -> System.out.println("完成!")              // onComplete: 完成时回调
);

// 输出:
// 收到: A
// 收到: B
// 收到: C
// 完成!
```

### 实际业务示例

```java
// 3. 查询所有库存（返回 Flux）
public Flux<StockInfo> listAllStocks() {
    return Flux.fromIterable(stockService.getAll())
            .map(this::convertToDto);  // 转换每个元素
}

// 4. 过滤和转换
stockService.listAll()
    .filter(s -> s.getQuantity() > 0)      // 只要库存 > 0 的
    .map(StockResponse::from)              // 转换格式
    .take(10)                               // 只取前 10 个
    .subscribe(System.out::println);        // 订阅消费
```

### Mono 示例

```java
// 5. 扣减库存（返回 Mono）
public Mono<DecrementResult> decrementStock(String sku, long quantity) {
    return redisTemplate.opsForValue()
        .decrement(sku + ":stock", quantity)
        .map(newQuantity -> new DecrementResult(sku, newQuantity))
        .onErrorResume(e -> {
            log.error("扣减失败", e);
            return Mono.just(new DecrementResult(sku, -1));
        });
}
```

## 关键关联

- [[WebFlux响应式编程]]: Flux/Mono 是 WebFlux 的核心数据类型
- [[Spring-MVC性能瓶颈]]: 从同步阻塞到异步非阻塞的关键转变点
- [[JVM预热效应]]: 响应式编程的冷热流概念与 JVM 预热相关
- [[Redis性能压测]]: 在高并发 Redis 场景中应用 Flux/Mono
- [[WebFlux-生命周期与多线程时序]]: Assembly 阶段创建的操作符链是 Flux/Mono 的实例化过程，理解操作符的惰性求值是理解生命周期的前提
- [[Reactor-背压与Netty协调]]: 冷流 "订阅后才生产" 的特性是背压生效的前提

**为什么需要这些关联**：
- WebFlux：Flux/Mono 是 WebFlax 的基础，不理解它们就无法使用 WebFlux
- Spring MVC 性能瓶颈：理解为什么需要从同步转向异步
- JVM 预热：响应式流的惰性求值与 JIT 编译的预热效应类似
- Redis 压测：实际应用场景，redis-counter-service 改造的目标

## 常见误区

| 误区 | 正确理解 |
|------|----------|
| "Flux 就是 List 的替代品" | Flux 是**数据流**，支持异步、背压、错误处理；List 是内存中的集合 |
| "subscribe() 后立即返回结果" | subscribe() 是异步的，立即返回；结果通过回调传递 |
| "Flux 一定比 for 循环快" | 对于简单操作，for 循环更快；Flux 的优势在 IO 密集型场景 |
| "Mono 和 Flux 不能互相转换" | 可以用 `.single()` / `.collectList()` 等方法转换 |
| "必须手动管理线程" | Reactor 自动调度线程，开发者只需关注业务逻辑 |

## 最佳实践

### 何时用 Mono vs Flux？

```java
// ✅ 用 Mono：明确知道返回 0 或 1 个结果
@GetMapping("/stock/{sku}")
public Mono<StockResponse> getStock(@PathVariable String sku) { ... }

// ✅ 用 Flux：可能返回 0 到 N 个结果
@GetMapping("/stocks")
public Flux<StockResponse> listStocks() { ... }

// ❌ 错误：明明只有一个却用 Flux
public Flux<StockResponse> getSingleStock() { ... }  // 应该用 Mono
```

### 操作符链式调用

```java
// 推荐风格：链式调用，清晰表达数据流转
stockService.getStock(sku)
    .filter(stock -> stock.getQuantity() > 0)      // 过滤
    .map(StockResponse::from)                       // 转换
    .switchIfEmpty(Mono.error(new NotFoundException()))  // 空值处理
    .doOnNext(resp -> log.info("返回库存: {}", resp))   // 副作用
    .subscribe(resp -> sendToClient(resp));           // 订阅
```

---

## 核心操作符详解

### 三大转换操作符

| 操作符 | 类型 | 特点 | 适用场景 |
|--------|------|------|----------|
| **`map`** | 同步 1:1 | 需要 onNext 触发 | 数据格式转换 |
| **`flatMap`** | 异步 1:N | 合并子流，返回 Publisher | IO操作、并行处理 |
| **`thenReturn`** | 无输入转换 | 不依赖上游值 | Mono<Void) 后返回结果 |
| **`transform`** | 管道级 1:1 | Assembly Time 立即执行 | 静态管道改装 |
| **`transformDeferred`** | 管道级 1:1 | Subscription Time 延迟执行 | 动态管道改装 |

#### map vs flatMap 的本质区别

```java
// map：同步转换（输入1个→输出1个）
.map(result -> {
    long remaining = result.longValue();
    if (remaining == -1) {
        failedMap.get().put(sku, qty);
    } else {
        successMap.get().put(sku, remaining);
    }
    return result;  // 返回普通对象
})

// flatMap：异步转换（输入1个→输出Publisher）
.flatMap(item -> {
    return redisTemplate.execute(decrementScript, ...)  // 返回 Mono/Flux
        .next()
        .map(result -> { ... });
})
```

**关键理解**：
- `map` = "给我苹果，我削皮"（需要输入，同步执行）
- `flatMap` = "给我苹果，我去果园再摘几个"（异步，可能返回多个）
- `thenReturn` = "不管你给不给我，我都自己拿一个"（不需要输入）

#### transform vs transformDeferred：管道级改装

```java
// transform: Assembly Time 立即执行，所有订阅者共享结果
Mono<String> transformed = source.transform(mono -> {
    int c = counter.incrementAndGet();  // 只执行一次！
    return mono.map(x -> x + "-" + c);
});
transformed.subscribe(System.out::println); // data-1
transformed.subscribe(System.out::println); // data-1（共享同一个结果）

// transformDeferred: Subscription Time 每次订阅才执行
Mono<String> deferred = source.transformDeferred(mono -> {
    int c = counter.incrementAndGet();  // 每次订阅都执行
    return mono.map(x -> x + "-" + c);
});
deferred.subscribe(System.out::println); // data-2
deferred.subscribe(System.out::println); // data-3（每次都重新执行函数）
```

| 操作符 | 执行时机 | 类比 |
|--------|----------|------|
| **`transform`** | Assembly Time（组装流水线时） | 编译期宏替换，一次成型，人人共享 |
| **`transformDeferred`** | Subscription Time（每次订阅时） | 运行时动态代理，按需构造，各取所需 |

**适用场景**：
- `transform`：静态策略封装（固定超时、固定重试次数）
- `transformDeferred`：动态参数注入（每次请求重新读取当前配置的超时时间）、有状态中间操作（每次订阅创建新的 Retry 实例）

### Flux → Mono 转换

```java
// .next()：取第一个元素
redisTemplate.execute(...)   // 返回 Flux<Long>
    .next()                  // → Mono<Long>（取第一个）
    .map(result -> ...);     // 有值才执行

// 边界情况：
// - Flux.just(1).next() → Mono(1)
// - Flux.empty().next() → Mono.empty()（后续 map 不执行！）
```

### 空值传播机制

```java
// 空信号会一直传递下去
Flux.empty()
    .next()           // Mono.empty()
    .map(x -> x * 2)  // ❌ 永远不执行（短路）
    .subscribe();     // 直接 onComplete

// 类似 Optional 的行为
Optional.empty().map(x -> x * 2)  // Optional.empty()
```

---

## 掌握度评估

- 当前等级：🌳 掌握
- 更新记录：
  - 2026-05-07: mastery=50 (初建笔记，了解基本概念)
  - 2026-05-09: mastery=70 (+20, 深入学习操作符、空值传播、实际项目应用)
  - 2026-05-14: mastery=80 (+10, 深入理解 transform/transformDeferred 的管道级改装语义，能区分 Assembly Time 与 Subscription Time)
- 已理解：
  - ✅ Flux/Mono 的基本概念和区别
  - ✅ 三种信号（onNext/onError/onComplete）
  - ✅ 惰性求值的原理和好处
  - ✅ 基本的创建和订阅操作
  - ✅ **核心操作符**：map/flatMap/thenReturn/.next() 的区别和使用场景
  - ✅ **空值传播机制**：Mono.empty() 导致后续操作符短路
  - ✅ **实际应用**：redis-counter-service-webflux 项目中的使用
  - ✅ **管道级改装**：transform（编译期宏替换）vs transformDeferred（运行时动态代理）
- 下一步：深入学习 Scheduler 线程调度、Project Reactor 内部实现（如 MonoCreate.subscribeActual）
