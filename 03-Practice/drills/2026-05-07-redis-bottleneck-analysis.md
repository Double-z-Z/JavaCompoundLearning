---
type: drill
date: 2026-05-07
topic: redis-performance-bottleneck-analysis
tools: [bombardier, JMeter, Jedis, redis-benchmark]
related_concepts:
  - [[Redis-性能压测]]
  - [[Spring-MVC性能瓶颈]]
  - [[JSON序列化开销]]
  - [[JVM预热效应]]
related_project: "[[redis-counter-service]]"
mastery: 65
---

# Redis 计数器服务瓶颈定位实战

## 练习目标

通过分层压测，精确定位 Redis 计数器服务的性能瓶颈，区分 HTTP 层、Redis 客户端层、Redis 服务端层的各自开销。

## 测试环境

- 应用服务器: 10.0.0.142:8080 (Spring Boot 3.2 + Lettuce)
- Redis Cluster: 6 节点 (10.0.0.102~107:6379)
- 压测端: Windows 8C16T

## 分层测试数据

### Layer 1: Redis 服务端基准

| 工具 | 命令 | QPS | 说明 |
|------|------|-----|------|
| redis-benchmark | `redis-benchmark -h 10.0.0.104 -t set -n 1000000 -c 100` | 155,351 | 服务端硬上限 |

### Layer 2: Redis 客户端层（绕过 HTTP）

| 工具 | 请求数 | QPS | 延迟 | 说明 |
|------|--------|-----|------|------|
| Jedis 直连 | 1,500,000 (3轮×50万) | **89,764** | 1.108ms | 执行相同 Lua 脚本，无 HTTP 开销 |

**测试方法**: 使用 JedisCluster 直连 Redis，执行与生产环境完全相同的 Lua 脚本。

### Layer 3: HTTP 协议层（含 Redis）

| 工具 | 场景 | 请求数 | QPS | P50 | 说明 |
|------|------|--------|-----|-----|------|
| bombardier | ping | 1,784,182 | **59,493** | 1.25ms | 纯 Tomcat，无 Redis |
| bombardier | decrement | 1,164,835 | **38,841** | 2.27ms | 完整链路，含 Redis Lua |
| JMeter | decrement | 1,047,333 | 33,622 | 1.93ms | 未充分预热 |

**关键发现**: 
- 首次测试 26,753 QPS → 预热后 38,841 QPS（+45%）
- JMeter 33K vs bombardier 38K，工具自身性能也会影响结果

## 瓶颈定位结论

```
Redis 直连:     89,764 QPS (100%)
    ↓ - HTTP 层损耗 30,271 QPS (34%)
ping 测试:      59,493 QPS (66%)
    ↓ - Redis 层损耗 20,652 QPS (23%)
HTTP decrement: 38,841 QPS (43%)
```

**最终结论**: 
- ✅ Redis 服务端无瓶颈（155K > 89K）
- ✅ Redis 客户端无瓶颈（Jedis 89K ≈ Lettuce 理论值）
- ❌ **Spring MVC + JSON 序列化是主要瓶颈**（消耗 57% 性能）
- ❌ **Tomcat 调度也有损耗**（ping 59K vs HTTP 38K = 21K 差距）

## 关键教训

1. **预热效应**: JVM C2 编译 + 连接池初始化，首次测试可能偏低 40%+
2. **工具差异**: bombardier (fasthttp) 比 JMeter 更能压榨服务端性能
3. **分层排除**: 必须分别测试 ping、HTTP decrement、Redis 直连三层，才能精确定位

## 关联知识

- [[Redis-性能压测]]: 压测方法论和工具选择
- [[Spring-MVC性能瓶颈]]: 为什么 Spring MVC + JSON 会成为瓶颈
- [[JVM预热效应]]: C2 编译和连接池预热的影响

## 下一步练习

- [ ] 纯文本响应优化（移除 JSON）
- [ ] WebFlux 响应式改造
