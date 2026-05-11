# 秒杀系统 - 测试指南

## 概述

本文档汇总五层防御架构的测试方法，包含各层测试文档链接和集成测试策略。

---

## 测试文档索引

| Phase | 内容 | 测试文档 |
|-------|------|----------|
| L2 | 网关签名校验 | 待实现 |
| L5 | Redis Lua 原子扣减 | `phase5-lua-tests.md` (待创建) |
| L3 | Sentinel 限流 | [phase2-sentinel-tests.md](./phase2-sentinel-tests.md) |
| L4 | MQ 异步队列 | [phase3-mq-spike-tests.md](./phase3-mq-spike-tests.md) |
| L1 | CDN 边缘限流 | 待调研 |
| L4 | 分时段批次 | 待实现 |

---

## 快速测试流程

### 1. 环境检查

```bash
# Redis Cluster
redis-cli -c -h 10.0.0.102 -p 6379 ping

# RabbitMQ
rabbitmq-diagnostics -q ping

# 应用健康检查
curl http://localhost:8080/actuator/health
```

### 2. 初始化测试数据

```bash
# 初始化库存
curl -X POST http://localhost:8080/stock/incr \
  -H "Content-Type: application/json" \
  -d '{"sku":"TEST_QUICK","qty":100}'
```

### 3. 快速冒烟测试

```bash
# T5: 预扣成功 → MQ → 202
curl -s http://localhost:8080/spike/order \
  -X POST \
  -H "Content-Type: application/json" \
  -H "X-User-Id: smoke-test" \
  -d '{"items":[{"sku":"TEST_QUICK","qty":1}]}'

# T6: 预扣失败 → 200
curl -s http://localhost:8080/spike/order \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"items":[{"sku":"TEST_QUICK","qty":999}]}'
```

### 4. 限流测试

```bash
# 快速压测（需要安装 wrk）
wrk -t4 -c20 -d5s -s post_body.lua http://localhost:8080/spike/order
```

---

## 集成测试

### 测试夹具

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SpikeIntegrationTest {

    @Autowired
    private WebTestClient webClient;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        // 清理测试数据
        redisTemplate.delete("stock:TEST_*");
    }

    @AfterEach
    void tearDown() {
        // 清理测试数据
        redisTemplate.delete("stock:TEST_*");
    }
}
```

---

## 测试环境配置

### application-test.yml

```yaml
spring:
  data:
    redis:
      cluster:
        nodes: localhost:6379
  rabbitmq:
    host: localhost
    port: 5672

sentinel:
  enabled: true
  spike:
    qps-threshold: 10  # 测试用低阈值

logging:
  level:
    com.example.counter: DEBUG
```

---

## 压测脚本

### wrk post_body.lua

```lua
wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
wrk.headers["X-User-Id"] = "wrk-test"

local counter = 0
wrk.body = function()
    counter = counter + 1
    return string.format([[{"items":[{"sku":"TEST_%d","qty":1}]}]], counter)
end
```

### 运行压测

```bash
# 限流测试
wrk -t4 -c100 -d30s --latency \
  -s post_body.lua \
  http://localhost:8080/spike/order

# MQ 吞吐测试
wrk -t8 -c200 -d60s --latency \
  -s post_body.lua \
  http://localhost:8080/spike/order
```

---

## 测试结果记录

每次测试后记录：

```markdown
| 日期 | 测试项 | 结果 | QPS | 延迟 | 问题 |
|------|--------|------|-----|------|------|
| 2026-05-11 | T5 MQ预扣 | ✅ | 5000 | 45ms | 无 |
| 2026-05-11 | T6 库存不足 | ✅ | - | - | 无 |
```

---

## CI/CD 集成

### Maven 测试

```bash
# 运行单元测试
mvn test

# 运行集成测试（需要 Redis + RabbitMQ）
mvn verify -Pintegration-test

# 生成测试报告
mvn surefire-report:report
```

### Docker Compose 测试环境

```yaml
# docker-compose.test.yml
version: '3.8'
services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  rabbitmq:
    image: rabbitmq:3-management
    ports:
      - "5672:5672"
      - "15672:15672"

  app:
    build: .
    depends_on:
      - redis
      - rabbitmq
    environment:
      - SPRING_DATA_REDIS_CLUSTER_NODES=redis:6379
      - RABBITMQ_HOST=rabbitmq
```
