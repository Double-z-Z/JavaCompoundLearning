# 秒杀系统 - 五层纵深防御架构

> 构建从边缘到内核的纵深防御体系，确保秒杀场景下的库存扣减公平性和系统稳定性

## 架构概览

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

## 项目结构

```
spike-protection/
├── docs/
│   ├── README.md                    # 本文件
│   ├── architecture.md             # 架构设计概述
│   ├── implementation-plan.md      # 实施计划（含设计思路）
│   ├── testing-guide.md            # 测试指南汇总
│   ├── phase2-sentinel-tests.md    # Sentinel 限流测试
│   ├── phase3-mq-spike-tests.md    # MQ 异步队列测试
│   ├── layer1-cdn.md               # CDN边缘限流
│   ├── layer2-gateway.md           # 网关层设计（签名校验 + 限流）
│   ├── layer3-access.md            # 接入层设计（Sentinel限流）
│   ├── layer4-application.md       # 应用层设计（异步队列 + 批次）
│   └── layer5-data.md              # 数据层设计（Lua原子扣减）
├── layer1-cdn/                     # Layer 1 配置
├── layer2-gateway/                 # Layer 2 代码
├── layer3-access/                  # Layer 3 代码
├── layer4-application/             # Layer 4 代码
└── layer5-data/                   # Layer 5 配置
```

## 优先级与进度

| 优先级 | 层级 | 改造点 | 状态 |
|--------|------|--------|------|
| P0 | L2 | 网关层加签名校验 | ⏳ 待实现 |
| P0 | L5 | Redis Lua原子扣减 | ✅ 已实现 |
| P0 | L3 | Sentinel自适应限流 | ✅ 已实现 |
| P0 | L4 | MQ异步队列削峰 | ✅ 已实现 |
| P1 | L1 | CDN边缘限流 | ⏳ 待调研 |
| P1 | L2 | 行为验证接入 | ⏳ 待实现 |
| P2 | L4 | 分时段批次设计 | ⏳ 待实现 |

## 已实现组件

### Layer 5: Redis Lua 原子扣减 ✅

**项目位置**：`../redis-counter-service-webflux`

**功能**：
- Lua 脚本原子扣减，防止超卖
- 多 SKU 并行扣减
- Saga 补偿机制

**关键文件**：
- `src/main/java/com/example/counter/config/ReactiveRedisConfig.java` — Lua 脚本定义
- `src/main/java/com/example/counter/service/MultiSkuOrderServiceImpl.java` — 扣减逻辑

**Lua 脚本**：
```lua
local stock = tonumber(redis.call('GET', KEYS[1]) or 0)
local quantity = tonumber(ARGV[1])
if stock >= quantity then
    return redis.call('DECRBY', KEYS[1], quantity)
else
    return -1
end
```

---

### Layer 3: Sentinel 自适应限流 ✅

**项目位置**：`../redis-counter-service-webflux`

**功能**：
- WebFlux Filter 级别限流
- QPS 阈值保护（默认 10k QPS/用户）
- 限流返回 429 + 友好提示

**关键文件**：
- `src/main/java/com/example/counter/config/SentinelConfig.java` — 限流规则配置
- `src/main/java/com/example/counter/config/SentinelWebFluxFilter.java` — Filter 拦截器
- `src/main/java/com/example/counter/config/SentinelBlockHandler.java` — BlockException 处理

**测试文档**：[phase2-sentinel-tests.md](./phase2-sentinel-tests.md)

---

### Layer 4: MQ 异步队列削峰 ✅

**项目位置**：`../redis-counter-service-webflux`

**功能**：
- 预扣库存成功后写入 RabbitMQ
- 异步创建订单
- 死信队列（DLQ）保障

**关键文件**：
- `src/main/java/com/example/counter/config/RabbitMQConfig.java` — MQ 配置
- `src/main/java/com/example/counter/service/SpikeOrderMQService.java` — 生产者
- `src/main/java/com/example/counter/service/SpikeOrderConsumer.java` — 消费者
- `src/main/java/com/example/counter/controller/SpikeController.java` — 秒杀接口

**测试文档**：[phase3-mq-spike-tests.md](./phase3-mq-spike-tests.md)

## 快速开始

### 1. 部署 Layer 5（已实现）

```bash
cd ../redis-counter-service-webflux
mvn spring-boot:run
```

### 2. 测试库存扣减

```bash
curl -X POST http://localhost:8080/order/multi-sku \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {"sku": "SKU001", "qty": 1}
    ]
  }'
```

## 后续计划

1. **Layer 2 网关签名校验**：使用 OpenResty + HMAC-SHA256（P0 待实现）
2. **Layer 1 CDN 边缘限流**：边缘节点配置（P1 待调研）
3. **Layer 2 行为验证**：无感风控/验证码（P1 待实现）
4. **Layer 4 分时段批次**：业务逻辑改造（P2 待实现）

---

## 测试指南

详细测试方法请参考：

- [测试指南汇总](./testing-guide.md) — 快速测试流程、CI/CD 集成
- [Sentinel 限流测试](./phase2-sentinel-tests.md) — P0/P1/P2 测试用例
- [MQ 异步队列测试](./phase3-mq-spike-tests.md) — 预扣、MQ、DLQ 测试

## 参考资料

- [Sentinel 官方文档](https://sentinelguard.io/zh-cn/)
- [OpenResty 最佳实践](https://moonbingbing.gitbooks.io/openresty-best-practices/)
- [RocketMQ 官方文档](https://rocketmq.apache.org/docs/)