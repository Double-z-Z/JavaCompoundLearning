# 多SKU订单服务 - 三级熔断测试文档

> **测试范围**：L2/L3 熔断单元测试、L1 手动压测
> **前置阶段**：Phase 1/2 功能测试完成

---

## 测试文件

| 测试类 | 范围 | 依赖 |
|--------|------|------|
| `MultiSkuOrderServiceCircuitBreakerTest` | L2 Redis 熔断 | Mockito（纯单元测试） |
| `SpikeOrderMQServiceCircuitBreakerTest` | L3 MQ 熔断 | Mockito（纯单元测试） |

---

## L2 熔断测试 (MultiSkuOrderServiceCircuitBreakerTest)

### T-CB-001: Redis 不可用时降级

**场景**: Redis 连接异常，触发熔断降级

**前置条件**: Redis 返回 `Flux.error(RuntimeException)`

**执行步骤**: `orderService.placeOrder(request)`

**预期结果**:
- success: `false`
- message: 包含 `"degraded"`

---

### T-CB-002: Redis 可用时正常流程

**场景**: Redis 正常返回库存

**前置条件**: Redis 返回 `Flux.just(99L)`

**执行步骤**: `orderService.placeOrder(request)`

**预期结果**:
- success: `true`

---

### T-CB-003: 库存不足返回失败

**场景**: Redis 返回 -1 表示库存不足

**前置条件**: Redis 返回 `Flux.just(-1L)`

**执行步骤**: `orderService.placeOrder(request)`

**预期结果**:
- success: `false`
- failed: 包含 SKU

---

### T-CB-004: 多 SKU 全部成功

**场景**: 两个 SKU 都扣减成功

**前置条件**: Redis 两次都返回成功

**执行步骤**: `orderService.placeOrder(request)`

**预期结果**:
- success: `true`

---

## L3 熔断测试 (SpikeOrderMQServiceCircuitBreakerTest)

### T-CB-010: MQ 不可用时降级到本地队列

**场景**: RabbitMQ 连接异常，消息暂存本地

**前置条件**: `RabbitTemplate.convertAndSend()` 抛出 `AmqpException`

**执行步骤**: `mqService.sendOrderMessage(deductResult, userId, requestId)`

**预期结果**:
- `getPendingMessageCount() == 1`

---

### T-CB-011: MQ 恢复后补偿重发

**场景**: MQ 恢复后重发本地暂存的消息

**前置条件**: 先触发降级暂存，再 Mock MQ 正常

**执行步骤**:
1. `sendOrderMessage()` 降级暂存
2. `resendPendingMessages()` 重发

**预期结果**:
- 重发成功：`resendCount == 1`
- 队列清空：`getPendingMessageCount() == 0`

---

### T-CB-012: 预扣失败不发送 MQ

**场景**: `PreDeductResult.isSuccess() == false`

**执行步骤**: `sendOrderMessage()` with `insufficient` result

**预期结果**:
- 不调用 `convertAndSend`
- 队列为空

---

### T-CB-013: MQ 正常发送

**场景**: MQ 正常工作，无降级

**前置条件**: `RabbitTemplate.convertAndSend()` 正常执行

**执行步骤**: `sendOrderMessage()`

**预期结果**:
- 队列为空
- `convertAndSend` 被调用 1 次

---

## 测试结果

```
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 手动测试（L1 熔断）

### 环境准备

```bash
# 启动 Redis
docker run -d --name redis -p 6379:6379 redis:7

# 启动 RabbitMQ
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:management

# 启动应用
cd redis-counter-service-webflux
mvn spring-boot:run
```

### L1 熔断验证（入口限流）

```bash
# 持续发请求超过 QPS 阈值，观察 429 响应
ab -n 20000 -c 100 -p request.json -T application/json http://localhost:8080/spike/order
```

### L2 熔断验证（Redis 故障）

```bash
# 正常下单
curl -X POST http://localhost:8080/spike/order \
  -H "Content-Type: application/json" \
  -d '{"items":[{"sku":"TEST","qty":1}]}'

# 停止 Redis，模拟故障
docker stop redis

# 再次下单，观察降级日志 "Service degraded, please retry later"

# 重启 Redis
docker start redis
```

### L3 熔断验证（MQ 故障）

```bash
# 停止 RabbitMQ
docker stop rabbitmq

# 正常下单，观察日志 "MQ降级：消息暂存本地队列"

# 重启 RabbitMQ
docker start rabbitmq
```

---

## 测试输出位置

```
src/test/java/com/example/counter/MultiSkuOrderServiceCircuitBreakerTest.java
src/test/java/com/example/counter/SpikeOrderMQServiceCircuitBreakerTest.java
```