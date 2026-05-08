---
type: atomic-note
id: CONCEPT-spring-mvc-performance-bottleneck
created: 2026-05-07
tags: [spring, mvc, performance, json, serialization]
related_emrg: [EMRG-Spring性能优化]
related_goal: [GOAL-Java核心深化]
mastery: 65
---

# Spring MVC 性能瓶颈

## 一句话定义

Spring MVC + Tomcat + JSON 序列化的同步阻塞模型在高并发场景下会产生显著开销，典型损耗占整体性能的 50%-70%。

## 核心理解

### 请求处理链路开销分析

```
HTTP Request
    ↓
Tomcat NIO Accept (少量开销)
    ↓
Spring MVC DispatcherServlet (路由调度)
    ↓
HandlerMapping (URL → Controller) (中等开销)
    ↓
Controller 方法调用
    ↓
  ├── 参数解析 (@PathVariable, @RequestParam) (中等开销)
  ├── 业务逻辑 (Redis Lua 执行) (实际工作)
  └── 创建 ResponseEntity<Map> (堆分配)
    ↓
HttpMessageConverter (Map → JSON) (**高开销**)
    ↓
Tomcat Response 发送 (少量开销)
```

### 各环节性能损耗（基于 redis-counter-service 实测）

| 环节 | 耗时占比 | 具体开销 | 优化方向 |
|------|----------|----------|----------|
| Spring MVC 调度 | 15-20% | DispatcherServlet、HandlerMapping | WebFlux |
| 参数解析 | 5-10% | 注解处理、类型转换 | 简化参数 |
| HashMap 创建 | 5-8% | 堆分配、GC 压力 | 复用对象/纯文本 |
| **JSON 序列化** | **20-30%** | **Jackson 反射、字符串拼接** | **Protobuf/纯文本** |
| Tomcat 线程上下文 | 10-15% | 线程切换、阻塞等待 | 异步化 |

### 实测数据对比

```
ping (无 Redis):           59,493 QPS
  └── Spring MVC + Tomcat 理论上限

decrement (完整链路):       38,841 QPS
  └── 比 ping 低 20,652 QPS (Redis + Lua 开销)

Jedis 直连 (无 HTTP):       89,764 QPS
  └── 比 decrement 高 50,923 QPS (Spring/JSON 开销)
```

**结论**：Spring MVC + JSON 消耗了 57% 的理论性能。

## 关键关联

- [[WebFlux]]: 响应式替代方案，非阻塞 IO
- [[Jackson序列化优化]]: 如何减少 JSON 序列化开销
- [[Tomcat线程模型]]: NIO vs APR vs NIO2 的性能差异
- [[Redis-性能压测-分层排除法]]: 如何量化各层开销

## 常见误区

| 误区 | 正确理解 |
|------|----------|
| "Spring Boot 性能很差，不能用" | 大多数场景足够，高并发才需要优化 |
| "升级 Spring Boot 版本就能解决" | 核心问题是同步模型，非版本问题 |
| "换成 FastJSON 就快了" | 序列化只是部分开销，调度层也是瓶颈 |

## 优化策略矩阵

| 优化方案 | 预期提升 | 工作量 | 风险 | 适用场景 |
|----------|----------|--------|------|----------|
| 纯文本响应 | 10-20% | 低 | 低 | 内部服务、快速验证 |
| Jackson 调优 | 5-15% | 低 | 低 | 必须保留 JSON 时 |
| Tomcat APR | 5-10% | 中 | 中 | 有原生库环境 |
| WebFlux | 50%+ | 高 | 中 | 长期高并发服务 |
| Protobuf | 20-30% | 中 | 低 | 微服务间通信 |

## 代码层面的瓶颈示例

```java
// 当前实现（有瓶颈）
@PostMapping("/{sku}/decrement")
public ResponseEntity<Map<String, Object>> decrement(...) {
    Long result = stockService.decrementStock(sku, quantity);
    Map<String, Object> response = new HashMap<>();  // 堆分配
    response.put("sku", sku);                         // 装箱
    response.put("status", "success");                // 字符串
    response.put("remaining", result);                // 装箱
    return ResponseEntity.ok(response);               // Jackson 序列化
}

// 优化方向 1：纯文本响应
@PostMapping("/{sku}/decrement")
public String decrement(...) {
    Long result = stockService.decrementStock(sku, quantity);
    return "{\"status\":\"success\",\"remaining\":" + result + "}";
}

// 优化方向 2：WebFlux
@PostMapping("/{sku}/decrement")
public Mono<String> decrement(...) {
    return stockService.decrementStockAsync(sku, quantity)
        .map(result -> "{\"remaining\":" + result + "}");
}
```

## 掌握度评估

- 当前等级：🌿 理解
- 已能识别 Spring MVC 各环节的瓶颈并量化
- 下一步：实践纯文本响应优化，验证数据
