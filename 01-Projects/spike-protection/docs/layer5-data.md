# Layer 5: 数据层设计

## 职责
- Lua 原子扣减：保证库存扣减原子性，防止超卖
- 最终一致性：订单与库存的最终一致性保证
- 数据持久化：MySQL 订单数据持久化

## 已实现：Redis Lua 原子扣减

### Lua 脚本

```lua
local stock = tonumber(redis.call('GET', KEYS[1]) or 0)
local quantity = tonumber(ARGV[1])

-- 参数校验
if quantity <= 0 then
    return -2  -- 无效数量
end

-- 库存不足
if stock < quantity then
    return -1  -- 库存不足
end

-- 原子扣减
return redis.call('DECRBY', KEYS[1], quantity)
```

### 返回值说明

| 返回值 | 含义 |
|--------|------|
| >= 0 | 扣减后剩余库存 |
| -1 | 库存不足 |
| -2 | 无效数量（qty <= 0） |

### Java 调用

```java
public Mono<Long> decrementStock(String sku, int qty) {
    String key = "stock:" + sku;
    return redisTemplate.execute(decrementScript, List.of(key), List.of(String.valueOf(qty)))
        .next()
        .map(Long::longValue);
}
```

## 多 SKU 并行扣减

### 流程

```
1. 并行执行多个 SKU 的 Lua 脚本
2. 收集成功/失败结果
3. 任意失败 → 触发补偿（回滚已成功的）
4. 全部成功 → 返回成功
```

### 实现

```java
public Mono<OrderResult> placeOrder(MultiSkuOrderRequest request) {
    return Flux.fromIterable(request.getItems())
        .flatMap(item -> executeDecrement(item.getSku(), item.getQty()))
        .collectList()
        .flatMap(results -> {
            if (hasFailure.get()) {
                // 触发补偿
                return compensate(successMap.get())
                    .thenReturn(OrderResult.failure("Partial failure", successMap.get(), failedMap.get()));
            }
            return Mono.just(OrderResult.success(successMap.get()));
        });
}
```

## 补偿机制

### 补偿逻辑

```java
private Mono<Void> compensate(Map<String, Long> decremented) {
    if (decremented.isEmpty()) {
        return Mono.empty();
    }

    return Flux.fromIterable(decremented.entrySet())
        .flatMap(entry -> {
            String sku = entry.getKey();
            Long qty = entry.getValue();
            String key = "stock:" + sku;

            // 回滚：INCRBY
            return redisTemplate.opsForValue()
                .increment(key, qty)
                .doOnNext(v -> log.info("Compensated: {} +{} = {}", sku, qty, v));
        })
        .then();
}
```

### 补偿触发条件

| 场景 | 是否触发补偿 |
|------|--------------|
| 单 SKU 库存不足 | 否（无已成功项） |
| 多 SKU 部分失败 | 是（回滚已成功的） |
| 多 SKU 全部失败 | 否（无可回滚项） |

## 订单持久化

### 异步落库

```java
@Component
public class OrderPersistenceService {

    @Autowired
    private OrderRepository orderRepository;

    public Mono<Order> saveOrder(OrderMessage message) {
        Order order = Order.builder()
            .userId(message.getUserId())
            .sku(message.getSku())
            .quantity(message.getQuantity())
            .status(OrderStatus.PENDING)
            .createTime(System.currentTimeMillis())
            .build();

        return orderRepository.save(order);
    }
}
```

### 定时对账

```java
@Scheduled(fixedDelay = 60000)
public void reconcile() {
    // 1. 查询 Redis 预扣数量
    // 2. 查询 MySQL 已创建订单数量
    // 3. 差异告警
}
```

## 验收标准

- [ ] Lua 脚本原子执行，无超卖
- [ ] 补偿机制正确回滚
- [ ] 订单数据不丢失
- [ ] Redis 与 MySQL 最终一致