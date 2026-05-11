# 秒杀系统 - 五层纵深防御架构

> **项目目标**：构建从边缘到内核的纵深防御体系，确保秒杀场景下的库存扣减公平性和系统稳定性

## 背景问题

1. **超卖问题** — Redis Lua 原子扣减已实现
2. **请求篡改** — 网关层签名校验缺失
3. **Redis 过载** — 缺乏限流保护
4. **应用层阻塞** — 缺乏异步削峰
5. **Bot 攻击** — 缺乏行为验证

## 五层纵深防御架构

```
用户请求
    ↓
[Layer 1] CDN/边缘节点：静态资源缓存 + 边缘限流（IP级）
    ↓
[Layer 2] 网关层（Nginx/OpenResty）：签名验证 + 令牌桶 + 防重放
    ↓
[Layer 3] 接入层（Spring Cloud Gateway/Sentinel）：用户级限流 + 黑名单
    ↓
[Layer 4] 应用层（秒杀服务）：库存预扣 + 异步队列 + 订单创建
    ↓
[Layer 5] 数据层（Redis/MySQL）：Lua原子扣减 + 最终一致性
```

## 实施计划

| 优先级 | 层级 | 改造点 | 工具/方案 | 状态 |
|--------|------|--------|-----------|------|
| P0 | L2 | 网关层加签名校验 | OpenResty + HMAC | 待实现 |
| P0 | L5 | Redis Lua原子扣减 | `EVALSHA` | ✅ 已实现 (redis-counter-service-webflux) |
| P0 | L3 | Sentinel自适应限流 | Alibaba Sentinel + WebFlux Filter | ✅ 已实现 (redis-counter-service-webflux) |
| P0 | L4 | MQ削峰队列 | RabbitMQ + 异步订单创建 | ✅ 已实现 (redis-counter-service-webflux) |
| P1 | L1 | CDN边缘限流 | 边缘节点配置 | 待调研 |
| P1 | L2 | 行为验证接入 | 无感风控/验证码 | 待实现 |
| P2 | L4 | 分时段批次设计 | 业务逻辑改造 | 待调研 |

## 项目结构

```
spike-protection/
├── docs/
│   ├── README.md                    # 项目概述
│   ├── architecture.md             # 五层架构设计
│   ├── layer1-cdn.md               # CDN边缘限流
│   ├── layer2-gateway.md           # 网关层设计
│   ├── layer3-access.md            # 接入层设计
│   ├── layer4-application.md       # 应用层设计
│   ├── layer5-data.md              # 数据层设计
│   └── implementation-plan.md       # 实施计划
├── layer1-cdn/                     # Layer 1 配置
├── layer2-gateway/                  # Layer 2 代码
├── layer3-access/                  # Layer 3 代码
├── layer4-application/              # Layer 4 代码
└── layer5-data/                    # Layer 5 配置
```

## 当前进度

### ✅ 已完成 (redis-counter-service-webflux)

**Layer 5 数据层**：
- Redis Lua 原子扣减脚本
- 多 SKU 并行扣减
- Saga 补偿机制

**Layer 4 应用层**（部分）：
- WebFlux 响应式服务
- 库存扣减接口

### ⏳ 待实现

**Layer 2 网关层**：
- OpenResty + HMAC 签名校验
- 令牌桶限流
- 防重放机制

**Layer 3 接入层**：
- Sentinel 限流配置
- 热点参数限流
- 用户级黑名单

**Layer 4 应用层**：
- 异步队列削峰
- 订单预创建
- 分时段批次

**Layer 1 CDN**：
- 边缘节点限流配置

## 关键技术方案

### Layer 2 - 网关层签名校验

```
请求参数：{ sku, qty, timestamp, nonce }
签名生成：HMAC-SHA256(secret, "sku={sku}&qty={qty}&t={timestamp}&n={nonce}")
Header：X-Signature: {signature}
```

### Layer 3 - Sentinel 限流

```java
@SentinelResource(value = "spike", blockHandler = "spikeBlockHandler")
public OrderResult spike(SpikeRequest request) {
    // 秒杀逻辑
}

public OrderResult spikeBlockHandler(SpikeRequest request, BlockException ex) {
    return OrderResult.fail("系统繁忙，请稍后重试");
}
```

### Layer 4 - 异步队列

```
秒杀请求 → 预扣库存 → 写入MQ → 返回"排队中"
              ↓
        MQ Consumer → 创建订单 → 更新状态
```

### Layer 5 - Lua 原子扣减

```lua
local stock = tonumber(redis.call('GET', KEYS[1]) or 0)
local quantity = tonumber(ARGV[1])
if stock >= quantity then
    return redis.call('DECRBY', KEYS[1], quantity)
else
    return -1
end
```