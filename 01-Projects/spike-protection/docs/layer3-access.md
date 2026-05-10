# Layer 3: 接入层设计

## 职责
- 用户级限流：防止单用户请求过快
- 热点参数限流：保护 Redis 不超 200k QPS
- 黑名单机制：封禁异常用户

## 架构图

```
[OpenResty] → [Spring Cloud Gateway] → [Sentinel] → [应用服务]
                      ↓
              [限流规则配置]
              [黑名单存储]
```

## Sentinel 限流配置

### 1. 引入依赖
```xml
<dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-core</artifactId>
    <version>1.8.6</version>
</dependency>
<dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-web-servlet</artifactId>
    <version>1.8.6</version>
</dependency>
```

### 2. 限流规则配置
```java
@Configuration
public class SentinelConfig {

    @PostConstruct
    public void init() {
        // 用户级限流规则
        List<FlowRule> userRules = new ArrayList<>();
        FlowRule userRule = new FlowRule("spike")
            .setGrade(RuleConstant.FLOW_GRADE_QPS)
            .setCount(10)  // 每秒 10 次
            .setStrategy(RuleConstant.STRATEGY_DIRECT)
            .setResourceType(2);  // Web 类型
        userRules.add(userRule);
        FlowRuleManager.loadRules(userRules);

        // 热点参数限流规则
        List<ParamFlowRule> paramRules = new ArrayList<>();
        ParamFlowRule paramRule = new ParamFlowRule("spike")
            .setGrade(RuleConstant.FLOW_GRADE_QPS)
            .setCount(1000)  // 每秒 1000 次
            .setParamIdx(0);  // 第一个参数是 SKU
        paramRules.add(paramRule);
        ParamFlowRuleManager.loadRules(paramRules);
    }
}
```

### 3. 限流降级处理
```java
@SentinelResource(value = "spike",
    blockHandler = "spikeBlockHandler",
    fallback = "spikeFallback")
public OrderResult spike(SpikeRequest request) {
    // 秒杀逻辑
    return orderService.placeOrder(request);
}

public OrderResult spikeBlockHandler(SpikeRequest request, BlockException ex) {
    log.warn("Spike限流触发: {}", ex.getClass().getSimpleName());
    return OrderResult.fail("系统繁忙，请稍后重试");
}

public OrderResult spikeFallback(SpikeRequest request, Throwable ex) {
    log.error("Spike异常: {}", ex.getMessage());
    return OrderResult.fail("服务异常，请稍后重试");
}
```

## 热点参数限流

### 规则配置

```java
// 集群总限流 200k QPS
ClusterBuilderConfig config = new ClusterBuilderConfig()
    .setThreshold(200000);

ClusterFlowRuleManager.setClusterConfig(config);

// 热点 SKU 限流
ParamFlowRule skuRule = ParamFlowRule.builder()
    .resource("spike")
    .paramIdx(0)  // SKU 参数索引
    .count(10000)  // 单 SKU 10k QPS
    .build();
ParamFlowRuleManager.addParamFlowRule(skuRule);
```

### 配置参数说明

| 参数 | 值 | 说明 |
|------|-----|------|
| cluster threshold | 200k | 集群总 QPS |
| param count | 10k | 单 SKU QPS |
| user count | 10 | 单用户 QPS |

## 黑名单机制

### 实现方案

```java
@Component
public class BlacklistService {

    private final ReactiveStringRedisTemplate redisTemplate;

    public Mono<Boolean> isBlacklisted(String userId) {
        String key = "blacklist:user:" + userId;
        return redisTemplate.hasKey(key);
    }

    public Mono<Void> addToBlacklist(String userId, Duration duration) {
        String key = "blacklist:user:" + userId;
        return redisTemplate.opsForValue().set(key, "1", duration).then();
    }

    public Mono<Void> removeFromBlacklist(String userId) {
        String key = "blacklist:user:" + userId;
        return redisTemplate.delete(key);
    }
}
```

### 网关拦截

```java
@Component
public class BlacklistFilter implements GlobalFilter {

    @Autowired
    private BlacklistService blacklistService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String userId = exchange.getAttribute("userId");

        return blacklistService.isBlacklisted(userId)
            .flatMap(isBlacklisted -> {
                if (isBlacklisted) {
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                }
                return chain.filter(exchange);
            });
    }
}
```

## Sentinel Dashboard 集成

```yaml
spring:
  cloud:
    sentinel:
      dashboard: localhost:8080
      transport:
        port: 8719
```

启动 Dashboard：
```bash
java -jar sentinel-dashboard.jar --server.port=8080
```

## 限流效果验证

### 测试场景

```bash
# 模拟 100 用户，每用户 100 QPS
for i in {1..100}; do
    curl -X POST http://spike-api/order \
        -H "X-User-Id: user-$i" \
        -d '{"sku":"SKU001","qty":1}'
done
```

### 预期结果

| 场景 | 预期行为 |
|------|----------|
| 单用户超过 10 QPS | 限流返回 429 |
| 单 SKU 超过 10k QPS | 限流返回 429 |
| 集群超过 200k QPS | 限流返回 429 |
| 黑名单用户 | 返回 403 |

## 验收标准

- [ ] 单用户 QPS 超过 10 被限流
- [ ] 单 SKU QPS 超过 10k 被限流
- [ ] 集群总 QPS 超过 200k 被限流
- [ ] 黑名单用户返回 403
- [ ] 限流返回 "系统繁忙，请稍后重试"