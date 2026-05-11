# Phase 3: MQ 异步队列削峰 - 测试文档

## 测试范围

| 测试项 | 测试类型 | 优先级 |
|--------|----------|--------|
| Sentinel 限流拦截 | 单元测试 | P0 |
| 限流返回 429 + 响应头 | 集成测试 | P0 |
| MQ 消息发送成功 | 单元测试 | P0 |
| MQ 消息消费正确 | 集成测试 | P0 |
| 预扣成功 → MQ → 202响应 | 集成测试 | P0 |
| 预扣失败 → 直接返回 200 | 集成测试 | P0 |
| 响应时间 < 100ms | 性能测试 | P1 |
| MQ 顺序消费 | 集成测试 | P1 |
| DLQ 死信处理 | 集成测试 | P2 |

---

## 前置条件

### 1. 启动 Redis Cluster
```bash
# 验证 Redis 连接
redis-cli -c -h 10.0.0.102 -p 6379 ping
```

### 2. 启动 RabbitMQ
```bash
# 验证 RabbitMQ
rabbitmq-diagnostics -q ping
# 或管理界面 http://localhost:15672
```

### 3. 初始化库存
```bash
curl -X POST http://localhost:8080/stock/init \
  -H "Content-Type: application/json" \
  -d '{"sku":"TEST_SKU_001","qty":100}'
```

---

## P0 测试用例

### T1: Sentinel 限流拦截

**测试目标**：验证 QPS 超过阈值时请求被拦截

**测试步骤**：
1. 设置 QPS 阈值为 10（测试用）
2. 并发发送 20 个请求
3. 统计返回码分布

**预期结果**：
- 前 10 个请求通过（200/202）
- 后 10 个请求被限流（429）
- 限流请求响应头包含 `X-Spike-Limit: triggered`

**测试命令**：
```bash
# 使用 wrk 或 ab 压测
wrk -t4 -c20 -d10s -s post_body.lua http://localhost:8080/spike/order

# 或使用 ab
ab -n 100 -c 50 -p body.json -T application/json http://localhost:8080/spike/order
```

**断言**：
```java
assertEquals(429, response.getStatusCode());
assertTrue(response.getHeaders().containsKey("X-Spike-Limit"));
```

---

### T2: 限流返回 429 + 友好提示

**测试目标**：验证限流响应格式正确

**测试步骤**：
1. 发送请求触发限流
2. 检查响应体格式

**预期结果**：
```json
{
  "success": false,
  "code": 429,
  "message": "系统繁忙，请稍后重试",
  "data": null
}
```

**测试命令**：
```bash
# 先压测触发限流
while true; do
  curl -s -w "\nHTTP_CODE:%{http_code}\n" http://localhost:8080/spike/order \
    -X POST -H "Content-Type: application/json" \
    -d '{"items":[{"sku":"TEST","qty":1}]}'
  sleep 0.01
done | grep -A2 "HTTP_CODE:429"
```

---

### T3: MQ 消息发送成功

**测试目标**：验证预扣成功后消息正确发送到 RabbitMQ

**测试步骤**：
1. 调用 `/spike/order` 接口
2. 检查 RabbitMQ 队列是否有消息

**预期结果**：
- RabbitMQ Management 界面看到 `spike-order-queue` 有 1 条消息
- 消息内容包含 orderId、userId、items、timestamp

**验证命令**：
```bash
# 查看队列消息数
rabbitmqctl list_queues name messages

# 查看队列内容（需要开启 management 插件）
curl -s -u guest:guest http://localhost:15672/api/queues/%2F/spike-order-queue
```

---

### T4: MQ 消息消费正确

**测试目标**：验证消费者正确处理订单消息

**测试步骤**：
1. 发送预扣成功的请求
2. 等待 1 秒
3. 检查消费者日志
4. 检查订单是否创建（模拟）

**预期日志**：
```
INFO 收到订单消息: orderId=ORD-xxx, userId=xxx, requestId=xxx
INFO [OrderService] 创建订单: orderId=xxx, userId=xxx, items=xxx, timestamp=xxx
INFO 订单创建完成: orderId=xxx
```

---

### T5: 预扣成功 → MQ → 202响应

**测试目标**：验证完整流程：预扣成功 → 发MQ → 返回 202

**测试步骤**：
1. 初始化库存 100
2. 发送预扣请求 qty=1
3. 检查响应码
4. 检查 Redis 库存是否扣减
5. 检查 MQ 是否有消息

**预期结果**：
- 响应码：`202 Accepted`
- 响应体：`{"success":true,"message":"排队中","code":202,"orderId":"ORD-xxx"}`
- Redis 库存：99
- MQ 消息：1 条

**测试命令**：
```bash
# 1. 初始化
curl -X POST http://localhost:8080/stock/incr \
  -H "Content-Type: application/json" \
  -d '{"sku":"TEST_T5","qty":100}'

# 2. 发送预扣请求
curl -s -w "\nHTTP_CODE:%{http_code}\n" http://localhost:8080/spike/order \
  -X POST \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user123" \
  -H "X-Request-Id: req-t5-001" \
  -d '{"items":[{"sku":"TEST_T5","qty":1}]}'

# 3. 检查 Redis 库存
redis-cli -c -h 10.0.0.102 GET stock:TEST_T5

# 4. 检查 MQ
rabbitmqctl list_queues name messages | grep spike-order-queue
```

---

### T6: 预扣失败 → 直接返回 200

**测试目标**：验证库存不足时直接返回，不发 MQ

**测试步骤**：
1. 初始化库存 5
2. 发送预扣请求 qty=10
3. 检查响应码和消息体
4. 检查 MQ 是否有新消息

**预期结果**：
- 响应码：`200 OK`
- 响应体：`{"success":false,"message":"库存不足","code":200}`
- MQ：`spike-order-queue` 消息数不变

**测试命令**：
```bash
# 1. 初始化
curl -X POST http://localhost:8080/stock/incr \
  -H "Content-Type: application/json" \
  -d '{"sku":"TEST_T6","qty":5}'

# 2. 发送超额预扣
curl -s http://localhost:8080/spike/order \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"items":[{"sku":"TEST_T6","qty":10}]}'
```

---

## P1 测试用例

### T7: 响应时间 < 100ms

**测试目标**：验证高并发下接口响应时间符合预期

**测试步骤**：
1. 使用 wrk 压测
2. 统计平均响应时间

**验收标准**：
- 平均响应时间 < 100ms
- P99 < 200ms

**测试命令**：
```bash
# wrk 压测
wrk -t4 -c100 -d30s --latency http://localhost:8080/spike/order

# 或使用 hey
hey -n 10000 -c 100 -m POST \
  -H "Content-Type: application/json" \
  -d '{"items":[{"sku":"TEST","qty":1}]}' \
  http://localhost:8080/spike/order
```

**预期输出**：
```
Running 30s test @ http://localhost:8080/spike/order
  4 threads and 100 connections
  Thread Stats   Avg      Stdev     Max   +/-Latency
    Latency     45.23ms    8.12ms  123.45ms   85.32%
  100000 requests in 30.00s, 0.00 errors
```

---

### T8: MQ 顺序消费

**测试目标**：验证同一 SKU 的订单按顺序消费

**测试步骤**：
1. 初始化库存 100
2. 并发发送 50 个预扣请求（每个 qty=1）
3. 检查消费者日志顺序
4. 检查最终库存是否为 50

**预期结果**：
- 消费者按顺序处理消息
- Redis 库存最终为 50（无超卖）

---

## P2 测试用例

### T9: DLQ 死信处理

**测试目标**：验证消费失败的消息进入 DLQ

**测试步骤**：
1. 模拟消费者抛出异常
2. 检查消息是否进入 DLQ

**预期结果**：
- `spike-order-queue` 消息数 -1
- `spike-order-queue.dlq` 消息数 +1

**验证命令**：
```bash
rabbitmqctl list_queues name messages | grep -E "(spike-order-queue|dlq)"
```

---

## 自动化测试

### 集成测试类

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SpikeIntegrationTest {

    @Autowired
    private WebTestClient webClient;

    @Test
    void testSpikeOrderSuccess() {
        // 初始化库存
        initStock("TEST_INTEGRATION", 100);

        // 发送预扣请求
        webClient.post().uri("/spike/order")
                .header("X-User-Id", "user1")
                .bodyValue(createRequest("TEST_INTEGRATION", 1))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.code").isEqualTo(202);
    }

    @Test
    void testSpikeOrderInsufficientStock() {
        initStock("TEST_INTEGRATION", 5);

        webClient.post().uri("/spike/order")
                .bodyValue(createRequest("TEST_INTEGRATION", 10))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("库存不足");
    }

    @Test
    void testRateLimit() {
        // 连续发送大量请求触发限流
        for (int i = 0; i < 20; i++) {
            webClient.post().uri("/spike/order")
                    .bodyValue(createRequest("TEST_LIMIT", 1))
                    .exchange()
                    .expectStatus()
                    .is2xxSuccessful()
                    .orExpect(status -> status.isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        }
    }
}
```

---

## 测试报告模板

```markdown
## 测试报告 - [日期]

### 环境信息
- Redis: 10.0.0.102:6379
- RabbitMQ: localhost:5672
- 应用: localhost:8080

### 测试结果汇总

| 用例 | 状态 | 备注 |
|------|------|------|
| T1 | ✅ 通过 | 20请求/10阈值 → 10通过10限流 |
| T2 | ✅ 通过 | 429响应格式正确 |
| T3 | ✅ 通过 | MQ消息发送成功 |
| T4 | ✅ 通过 | 消费日志正常 |
| T5 | ✅ 通过 | 202响应+MQ消息 |
| T6 | ✅ 通过 | 库存不足返回200 |
| T7 | ⏳ 待测 | 需要压测环境 |
| T8 | ⏳ 待测 | 需要压测环境 |
| T9 | ⏳ 待测 | 需要模拟失败 |

### 性能数据
- QPS: xxx
- 平均响应时间: xxx ms
- P99延迟: xxx ms

### 问题记录
| 问题 | 严重度 | 状态 |
|------|--------|------|
| xxx | P1 | open |
```
