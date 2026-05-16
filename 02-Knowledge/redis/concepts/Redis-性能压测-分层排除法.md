---
type: atomic-note
id: CONCEPT-redis-benchmark-layered-analysis
created: 2026-05-07
tags: [redis, benchmark, performance, bottleneck-analysis]
related_emrg: [EMRG-Redis]
related_goal: [GOAL-Redis深入]
mastery: 60
---

# Redis 性能压测：分层排除法

## 一句话定义

通过逐层剥离 HTTP 协议、Redis 客户端、Redis 服务端，精确定位性能瓶颈所在层级的系统化方法。

## 核心理解

### 为什么需要分层？

传统压测只对比"HTTP QPS"和"redis-benchmark QPS"，得出"应用层有损耗"的模糊结论。但"应用层"包含 Spring MVC、JSON 序列化、Lettuce 客户端等多个组件，无法确定优化方向。

### 三层模型

```
┌─────────────────────────────────────┐
│  Layer 3: HTTP 协议层                │
│  Spring MVC + JSON + Tomcat         │
│  测试方法: ping 接口（无 Redis）      │
├─────────────────────────────────────┤
│  Layer 2: Redis 客户端层             │
│  Lettuce / Jedis 连接池             │
│  测试方法: Jedis 直连（绕过 HTTP）    │
├─────────────────────────────────────┤
│  Layer 1: Redis 服务端               │
│  redis-server 进程                   │
│  测试方法: redis-benchmark           │
└─────────────────────────────────────┘
```

### 数据解读公式

```
HTTP 层损耗 = Jedis 直连 QPS - ping QPS
Redis 客户端损耗 = ping QPS - HTTP decrement QPS
Redis 服务端余量 = redis-benchmark QPS - Jedis 直连 QPS
```

## 实战案例

### 案例：redis-counter-service 瓶颈定位

| 层级 | 测试方式 | QPS | 占比 |
|------|----------|-----|------|
| Layer 1 | redis-benchmark | 155,351 | 服务端上限 |
| Layer 2 | Jedis 直连 | 89,764 | 客户端上限 |
| Layer 3a | ping（无 Redis）| 59,493 | HTTP 基线 |
| Layer 3b | HTTP decrement | 38,841 | 完整链路 |

**计算**:
- HTTP 层损耗 = 89,764 - 59,493 = **30,271 QPS (34%)**
- Redis 客户端损耗 = 59,493 - 38,841 = **20,652 QPS (23%)**
- 结论: **Spring MVC + JSON 是主要瓶颈**

## 关键关联

- [[JVM预热效应]]: 首次测试可能偏低 40%+，必须预热
- [[压测工具选择]]: bombardier vs JMeter vs wrk 的差异
- [[Spring-MVC性能瓶颈]]: 为什么 Spring MVC 会成为瓶颈

## 常见误区

| 误区 | 正确理解 |
|------|----------|
| "redis-benchmark 155K，HTTP 只有 30K，差距太大" | 必须分层测试，不能直接对比 |
| "Lettuce 连接池调优一定能提升" | 可能不是瓶颈，调优无效 |
| "一次测试就能定位" | 需要多轮测试，排除预热和波动 |

## 掌握度评估

- 当前等级：🍎 应用
- 已能独立完成分层压测并定位瓶颈
- 下一步：学习各层的具体优化方法
