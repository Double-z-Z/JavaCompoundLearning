# 秒杀防御体系 - 测试总览

> 本文档汇总五层防御架构的全部测试方法与状态，提供快速冒烟测试入口和环境检查清单。

---

## 1. 测试矩阵

| 防御层 | 测试内容 | 测试文档 | 状态 |
|--------|----------|----------|------|
| L1 | CDN 边缘限流 | — | ⏳ 待调研 |
| L2 | 网关签名校验 | — | ⏳ 待实现 |
| L3 | Sentinel 自适应限流 | [l2-sentinel-tests.md](./l2-sentinel-tests.md) | ✅ 已完成 |
| L4 | MQ 异步队列削峰 | [l3-mq-spike-tests.md](./l3-mq-spike-tests.md) | ⏳ 计划中 |
| L4 | 分时段批次 | — | ⏳ 待实现 |
| L5 | 库存扣减（功能/边界/倾斜） | [l1-functional-boundary-tests.md](./l1-functional-boundary-tests.md) | ✅ 已完成 |
| L5 | 本地缓存性能 | [l4-cache-performance-report.md](./l4-cache-performance-report.md) | ✅ 已完成 |
| — | 压测标杆记录 | [benchmark-record.md](./benchmark-record.md) | ✅ 参考 |

---

## 2. 环境检查清单

每次测试前执行：

```bash
# Redis Cluster
redis-cli -c -h 10.0.0.102 -p 6379 ping

# RabbitMQ
rabbitmq-diagnostics -q ping

# 应用健康检查
curl -s http://localhost:8080/actuator/health
```

---

## 3. 快速冒烟测试

### 3.1 初始化库存

```bash
curl -X POST http://localhost:8080/stock/incr \
  -H "Content-Type: application/json" \
  -d '{"sku":"TEST_QUICK","qty":100}'
```

### 3.2 正常下单 → MQ → 202

```bash
curl -s http://localhost:8080/spike/order \
  -X POST \
  -H "Content-Type: application/json" \
  -H "X-User-Id: smoke-test" \
  -d '{"items":[{"sku":"TEST_QUICK","qty":1}]}'
```

### 3.3 库存不足 → 直接返回 200

```bash
curl -s http://localhost:8080/spike/order \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"items":[{"sku":"TEST_QUICK","qty":999}]}'
```

### 3.4 限流触发 → 429

```bash
# 快速压测（需安装 wrk）
wrk -t4 -c20 -d5s -s post_body.lua http://localhost:8080/spike/order
```

---

## 4. 测试环境配置

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

## 5. CI/CD 集成

### Maven 测试

```bash
# 单元测试
mvn test

# 集成测试（需 Redis + RabbitMQ）
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

---

## 6. 命令速查

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

### bombardier（Windows 推荐）

```powershell
# 下载
Invoke-WebRequest -Uri "https://github.com/codesenberg/bombardier/releases/download/v2.0.2/bombardier-windows-amd64.exe" -OutFile "bombardier.exe"

# 限流压测
.\bombardier.exe -c 100 -n 500000 -H "Content-Type: application/json" -m POST -f request_body.json http://10.0.0.142:8080/spike/order
```

---

## 7. 测试结果记录模板

```markdown
| 日期 | 测试项 | 结果 | QPS | 延迟 | 问题 |
|------|--------|------|-----|------|------|
| 2026-05-11 | T5 MQ预扣 | ✅ | 5000 | 45ms | 无 |
| 2026-05-11 | T6 库存不足 | ✅ | - | - | 无 |
```

---

*最后更新：2026-05-12*
