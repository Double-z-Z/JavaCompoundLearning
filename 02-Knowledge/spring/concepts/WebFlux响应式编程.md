---
type: atomic-note
id: CONCEPT-webflux-reactive
created: 2026-05-07
updated: 2026-05-07
tags: [spring, webflux, reactive, non-blocking, reactor]
related_emrg: [EMRG-Spring性能优化]
related_goal: [GOAL-Java核心深化]
mastery: 60
---

# WebFlux 响应式编程

## 一句话定义

WebFlux 是 Spring Framework 5 引入的响应式 Web 框架，基于 Reactor 实现，采用非阻塞 IO 模型，能够用更少的线程处理更高的并发，是高吞吐量场景下 Spring MVC 的替代方案。

## 核心理解

### WebFlux 的诞生史

```
时间线：
1996年 Servlet 规范诞生（一个请求一个线程）
    ↓
2003年 Spring MVC 发布（基于 Servlet，同步阻塞模型）
    ↓
2013年 Node.js 兴起（事件驱动 + 非阻塞 IO，单线程处理高并发）
    ↓
2014年 Reactive Streams 规范提出（JDK 9 Flow API，统一响应式标准）
    ↓
2017年 Spring Framework 5.0 发布 WebFlux（基于 Reactor 库）
```

**驱动力**：
- 云原生兴起：微服务需要更高并发能力
- IO 密集型应用增多：大量调用外部服务（数据库/缓存/API）
- Node.js 成功证明：事件驱动模型可行

**技术演进本质**：从"同步阻塞"到"异步非阻塞"的范式转移

### 为什么需要 WebFlux？

**传统 Spring MVC 的问题**：
```
每个请求占用一个线程 → 高并发时线程池耗尽 → 性能瓶颈
├─ 线程上下文切换开销大
├─ 阻塞等待 IO（如数据库、Redis）时线程空闲
└─ 无法充分利用多核 CPU
```

**WebFlux 的解决方案**：
```
少量事件循环线程处理所有请求 → 非阻塞 IO → 更高吞吐量
├─ Reactor 模式（单线程处理多路复用）
├─ 回调式编程，IO 等待时释放线程
└─ 充分利用 CPU，支持更高并发
```

### 性能对比

| 特性 | Spring MVC | WebFlux |
|------|-----------|---------|
| 线程模型 | 阻塞式（每个请求一个线程） | 非阻塞式（事件循环） |
| 线程数 | 与并发数相当 | 固定少量（CPU核心数） |
| 高并发表现 | 线程池耗尽 | 保持稳定 |
| 适用场景 | 中等并发、CPU密集 | 高并发、IO密集 |

### 核心组件

```
┌─────────────────────────────────────────────────────────┐
│                      WebFlux                           │
├─────────────────────────────────────────────────────────┤
│  RouterFunction / @Controller                          │
│       ↓                                                │
│  HandlerFunction / @RequestMapping                     │
│       ↓                                                │
│  Mono<T> / Flux<T>  (Reactor 核心类型)                │
│       ↓                                                │
│  WebClient (非阻塞 HTTP 客户端)                        │
│       ↓                                                │
│  Netty (默认底层服务器)                                │
└─────────────────────────────────────────────────────────┘
```

### Mono vs Flux

| 类型 | 含义 | 场景 |
|------|------|------|
| `Mono<T>` | 0 或 1 个元素 | 返回单个对象、空结果 |
| `Flux<T>` | 0 到 N 个元素 | 返回列表、流式数据 |

## 关键关联

- [[Spring-MVC性能瓶颈]]: WebFlux 是解决 Spring MVC 线程模型瓶颈的方案
- [[Reactor框架]]: WebFlux 的底层响应式库
- [[Netty]]: WebFlux 默认使用的非阻塞服务器
- [[Flux核心概念]]: WebFlux 的数据类型基础，理解响应式流的关键
- [[函数式路由vs注解式路由]]: WebFlux 支持两种路由风格的选择依据

### Servlet vs WebFlux：统一分类

**它们统称为：Web 编程模型（HTTP 处理层）**

```
┌─────────────────────────────────────────────────────┐
│                   你的应用程序                        │
│   (业务逻辑：库存扣减、订单处理...)                    │
├─────────────────────────────────────────────────────┤
│              Web 编程模型 (这里!)                     │
│                                                     │
│   ┌─────────────────┐    ┌─────────────────┐       │
│   │   Servlet API   │    │ Reactive Streams│       │
│   │   (命令式)       │    │   (响应式)       │       │
│   └────────┬────────┘    └────────┬────────┘       │
│            │                      │                 │
│   ┌────────▼────────┐    ┌───────▼────────┐        │
│   │  Spring MVC     │    │   WebFlux      │        │
│   └─────────────────┘    └────────────────┘        │
├─────────────────────────────────────────────────────┤
│              HTTP 服务器                              │
│   Tomcat / Jetty / Netty / Undertow                  │
└─────────────────────────────────────────────────────┘
```

**共同点**：
- 都解决"如何接收和处理 HTTP 请求"
- 都定义了行为模式、工具、生态和使用规范
- 行为边界：只处理服务端逻辑、管理连接和响应、提供业务层接口

**本质区别**：
- **Servlet/MVC**: 命令式（线程被占用直到完成）
- **WebFlux**: 声明式（注册回调后释放线程，数据来了再处理）

### MVC 本质：架构模式而非网络模型

**重要洞察**：MVC 是架构模式，与底层网络模型无关！

| MVC 组件 | Spring MVC | WebFlux |
|---------|-----------|---------|
| Model | `Map<String, Object>` | `Mono<T>` / `Flux<T>` |
| View | Thymeleaf/JSP | ServerResponse |
| Controller | `@RestController` | `@RestController` 或 `RouterFunction` |

**WebFlux 也支持 MVC！** 只是底层变成了响应式：

```java
// 注解式 MVC（传统风格）
@RestController
@RequestMapping("/api")
public class StockController {
    
    @GetMapping("/stock/{sku}")
    public Mono<StockResponse> getStock(@PathVariable String sku) {
        return stockService.getStock(sku).map(StockResponse::from);
    }
}

// 函数式风格（声明式）
@Bean
RouterFunction<ServerResponse> stockRoutes(Handler handler) {
    return route()
        .GET("/api/stock/{sku}", handler::getStock)
        .POST("/api/stock/{sku}/decrement", handler::decrement)
        .build();
}
```

**结论**：MVC 可以叠加在任何网络模型上（Servlet + MVC = Spring MVC，Reactor + MVC = WebFlux）

## 常见误区

| 误区 | 正确理解 |
|------|----------|
| "WebFlux 一定比 MVC 快" | 仅在高并发 IO 密集场景有优势，CPU 密集场景反而更慢 |
| "WebFlux 不需要线程" | 需要事件循环线程，只是数量更少 |
| "原有代码直接迁移即可" | 需要重构为响应式风格，学习曲线较陡 |
| "所有数据库驱动都支持" | 需要响应式数据库驱动（如 R2DBC） |
| "WebFlux 没有 MVC" | **错误！** WebFlux 也支持 MVC，只是底层是响应式的 |
| "函数式路由一定比注解式好" | 各有适用场景，需按路由变化频率选择 |

### 函数式路由 vs 注解式路由

| 维度 | 注解式（Controller） | 函数式（RouterFunction） |
|------|---------------------|------------------------|
| **思维模式** | 面向对象（类组织） | 函数式（管道组合） |
| **路由定义** | 声明式（元数据） | 编程式（代码即配置） |
| **扩展方式** | 继承/注解 | 组合/高阶函数 |
| **可测试性** | 需要 Mock 容器 | 纯函数，易测试 |
| **动态性** | 低（编译时固定） | 高（运行时可组合） |
| **认知负担** | 低（符合直觉） | 中等（需要理解组合） |

**核心差异**：不是位置问题，而是**组合能力**
- 注解式：路由与代码强耦合，编译时确定
- 函数式：路由是一等公民，可像数据一样操作（组合、条件、过滤）

### 动态路由场景分类（三层决策模型）

```
┌──────────┬──────────────────┬───────────────────────┐
│ 高频率    │ API 网关         │ ✅ 函数式（组合能力）   │
│ 变化      │ 多租户 SaaS      │                       │
├──────────┼──────────────────┼───────────────────────┤
│ 低频率    │ 灰度发布         │ 🔀 混合模式            │
│ 变化      │ 特性开关         │ (注解 + Filter/AOP)    │
├──────────┼──────────────────┼───────────────────────┤
│ 固定      │ 大多数业务 API   │ 📝 注解式（开发效率）   │
│ 不变      │ CRUD 服务        │                       │
└──────────┴──────────────────┴───────────────────────┘
```

**场景 1 典型案例**：
- 多租户 SaaS：不同租户看到不同的 API
- API 网关/BFF：根据客户端类型返回不同格式
- 插件化架构：模块自动注册路由
- 环境差异：开发环境暴露调试接口

**场景 2 最佳实践**：注解写业务 + Filter/AOP 处理动态逻辑

**场景 3 适用场景**：大多数传统 CRUD、结构稳定的 RESTful API

## 最佳实践

```java
// 1. 基本响应式控制器
@RestController
@RequestMapping("/api")
public class StockController {
    
    private final StockService stockService;
    
    // 2. 返回 Mono（单个结果）
    @GetMapping("/stock/{sku}")
    public Mono<StockResponse> getStock(@PathVariable String sku) {
        return stockService.getStock(sku)
                .map(stock -> new StockResponse(sku, stock));
    }
    
    // 3. 返回 Flux（多个结果）
    @GetMapping("/stocks")
    public Flux<StockResponse> listStocks() {
        return stockService.listAllStocks()
                .map(s -> new StockResponse(s.getSku(), s.getQuantity()));
    }
    
    // 4. 组合操作
    @PostMapping("/stock/{sku}/decrement")
    public Mono<DecrementResponse> decrement(
            @PathVariable String sku,
            @RequestParam long quantity) {
        return stockService.decrementStock(sku, quantity)
                .map(result -> {
                    if (result > 0) {
                        return new DecrementResponse("success", result);
                    } else {
                        return new DecrementResponse("insufficient_stock", -1);
                    }
                });
    }
}
```

## 迁移策略

```
1. 评估是否需要迁移
   └─ 当前 QPS < 10K → 不需要
   └─ 当前 QPS > 50K 或增长快 → 考虑迁移

2. 渐进式迁移
   ├─ 先从 IO 密集的接口开始
   ├─ 保持新旧接口并行
   └─ 逐步淘汰 MVC 接口

3. 配套改造
   ├─ 数据库 → R2DBC 或 reactive Redis 客户端
   ├─ HTTP 调用 → WebClient
   └─ 异常处理 → 响应式错误处理
```

## 掌握度评估

- 当前等级：🌿 理解
- 更新记录：
  - 2026-05-07: mastery=30 (初建笔记，了解基本概念)
  - 2026-05-07: mastery=60 (+30, 深入理解诞生史、MVC本质、路由选择策略)
- 已理解：
  - ✅ WebFlux 的历史背景和技术驱动力
  - ✅ Servlet vs WebFlux 的统一分类（Web 编程模型）
  - ✅ MVC 是架构模式，与网络模型无关
  - ✅ 函数式路由 vs 注解式路由的适用场景
  - ✅ 动态路由的三层决策模型
- 下一步：实践 WebFlux 改造 redis-counter-service，对比性能差异
