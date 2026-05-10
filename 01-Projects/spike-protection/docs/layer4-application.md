# Layer 4: 应用层设计

## 职责
- 库存预扣：快速响应用户请求
- 异步队列：削峰填谷，避免阻塞
- 订单创建：最终一致性保证

## 架构图

```
用户请求
    ↓
[库存预扣]
    ├── 成功 → 写入MQ → 返回"排队中"
    └── 失败 → 直接返回"库存不足"
           ↓
[MQ Consumer]
    ├── 创建订单
    ├── 更新状态
    └── 通知用户
```

## 异步队列削峰

### 流程说明

1. **预扣库存**：Lua 原子扣减
2. **写入 MQ**：发送创建订单任务
3. **返回用户**：立即响应"排队中"
4. **异步处理**：消费消息创建订单
5. **状态通知**：WebSocket/轮询通知用户

### 代码实现

```java
@Service
public class SpikeService {

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    public Mono<OrderResult> placeOrder(SpikeRequest request) {
        return executeLuaScript(request)
            .flatMap(result -> {
                if (result.isSuccess()) {
                    // 预扣成功，发送 MQ 消息
                    return sendToMQ(request, result)
                        .thenReturn(OrderResult.queuing("排队中", result.getDecremented()));
                } else {
                    // 预扣失败，直接返回
                    return Mono.just(result);
                }
            });
    }

    private Mono<Void> sendToMQ(SpikeRequest request, OrderResult result) {
        // 发送订单创建消息
        OrderMessage message = new OrderMessage()
            .setUserId(request.getUserId())
            .setSku(result.getDecremented().keySet())
            .setTimestamp(System.currentTimeMillis());

        return rocketMQTemplate.asyncSend("spike-orders", message)
            .then();
    }
}
```

### MQ 消费处理

```java
@Component
public class OrderConsumer {

    @Autowired
    private OrderService orderService;

    @RocketMQMessageListener(topic = "spike-orders", consumerGroup = "order-consumer")
    public void onMessage(OrderMessage message) {
        // 1. 创建订单
        Order order = orderService.createOrder(message);

        // 2. 更新库存状态（如果需要）

        // 3. 通知用户（WebSocket/短信/邮件）
        notificationService.notifyUser(message.getUserId(), order);
    }
}
```

## 分时段批次设计

### 批次状态机

```
PENDING (待开始)
    ↓ 时间到达
ACTIVE (抢购中)
    ↓ 库存售罄
SOLD_OUT (售罄)
    ↓ 时间结束
END (已结束)
```

### 实现方案

```java
@Service
public class BatchService {

    public Mono<Batch> getActiveBatch(String sku) {
        String batchKey = "batch:sku:" + sku + ":active";
        return redisTemplate.opsForValue().get(batchKey)
            .flatMap(batchId -> getBatchById(batchId))
            .switchIfEmpty(Mono.error(new BatchNotFoundException()));
    }

    public Mono<BatchResult> startBatch(String sku) {
        return getActiveBatch(sku)
            .filter(batch -> batch.getStatus() == BatchStatus.PENDING)
            .flatMap(batch -> {
                batch.setStatus(BatchStatus.ACTIVE);
                return saveBatch(batch);
            });
    }
}
```

### 批次配置示例

```yaml
spike:
  batches:
    - id: "batch-001"
      sku: "SKU001"
      startTime: "2024-01-01 10:00:00"
      endTime: "2024-01-01 10:30:00"
      stock: 100
    - id: "batch-002"
      sku: "SKU001"
      startTime: "2024-01-01 10:30:00"
      endTime: "2024-01-01 11:00:00"
      stock: 100
```

## 库存预扣优化

### 预扣流程

```
1. 检查批次状态
2. Lua 原子扣减
3. 写入用户预扣记录 (防止重复购买)
4. 发送 MQ 消息
```

### 防重复购买

```lua
-- 检查用户是否已预扣
local userKey = "user:preorder:" .. KEYS[1] .. ":" .. ARGV[2]
local exists = redis.call('GET', userKey)
if exists then
    return -2  -- 用户已预扣
end

-- 执行扣减
local stock = tonumber(redis.call('GET', KEYS[1]) or 0)
local quantity = tonumber(ARGV[1])
if stock < quantity then
    return -1  -- 库存不足
end

redis.call('DECRBY', KEYS[1], quantity)
redis.call('SETEX', userKey, 3600, "1")  -- 1小时有效期
return stock - quantity
```

## 验收标准

- [ ] 预扣接口响应时间 < 100ms
- [ ] MQ 消息不丢失
- [ ] 订单创建顺序正确
- [ ] 批次状态转换正确
- [ ] 防重复购买生效