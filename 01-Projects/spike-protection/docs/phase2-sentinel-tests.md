# Phase 2: Sentinel 自适应限流 - 测试文档

## 测试范围

| 测试项 | 测试类型 | 优先级 |
|--------|----------|--------|
| Sentinel Filter 正常拦截 | 单元测试 | P0 |
| 限流返回 429 + 响应头 | 集成测试 | P0 |
| 限流不影响其他接口 | 集成测试 | P0 |
| QPS 阈值动态生效 | 配置测试 | P1 |
| 集群限流模式 | 集成测试 | P2 |

---

## 前置条件

### 1. 应用启动
```bash
cd redis-counter-service-webflux
mvn spring-boot:run
# 或 docker 运行
```

### 2. 验证 Sentinel 生效
```bash
curl -s http://localhost:8080/actuator/health
# 应看到 Sentinel 相关日志
```

---

## P0 测试用例

### T1: Sentinel Filter 正常拦截

**测试目标**：验证 `/spike/**` 路径被限流 Filter 拦截

**测试步骤**：
1. 发送 GET `/spike/limit` 请求
2. 观察日志输出

**预期结果**：
- 请求被 `SentinelWebFluxFilter` 拦截
- 日志显示限流检查通过

**测试命令**：
```bash
curl -v http://localhost:8080/spike/limit
```

**预期日志**：
```
SentinelWebFluxFilter : 请求通过限流检查: resource=spike
```

---

### T2: 限流返回 429 + 响应头

**测试目标**：验证限流时返回正确的状态码和响应头

**测试步骤**：
1. 配置 QPS 阈值为较低值（测试用）
2. 并发发送超过阈值的请求
3. 检查响应状态码和响应头

**预期结果**：
- HTTP Status: `429 Too Many Requests`
- Response Header: `X-Spike-Limit: triggered`
- Response Body:
```json
{
  "code": 429,
  "success": false,
  "message": "系统繁忙，请稍后重试",
  "data": null,
  "requestId": "xxx"
}
```

**测试命令**：
```bash
# 并发测试
for i in {1..30}; do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/spike/order \
    -X POST \
    -H "Content-Type: application/json" \
    -d '{"items":[{"sku":"TEST","qty":1}]}' &
done
wait
echo "Done"
```

---

### T3: 限流不影响其他接口

**测试目标**：验证限流只影响 `/spike/**` 路径，不影响其他接口

**测试步骤**：
1. 触发 `/spike/**` 限流
2. 同时访问其他接口（如 `/stock/*`）

**预期结果**：
- `/spike/**` 返回 429
- `/stock/**` 正常返回 200

**测试命令**：
```bash
# 触发限流
curl -s http://localhost:8080/spike/order -X POST -H "Content-Type: application/json" \
  -d '{"items":[{"sku":"TEST","qty":1}]}' -w "\nStatus:%{http_code}\n"

# 访问其他接口
curl -s http://localhost:8080/stock/TEST_SKU -w "\nStatus:%{http_code}\n"
```

---

## P1 测试用例

### T4: QPS 阈值动态生效

**测试目标**：验证修改配置后限流阈值立即生效

**测试步骤**：
1. 设置 `sentinel.spike.qps-threshold=5`
2. 发送 10 个请求
3. 修改阈值为 100
4. 再发送 10 个请求

**预期结果**：
- 第一轮：5 个通过，5 个限流
- 第二轮：10 个全部通过

**测试命令**：
```bash
# 修改配置后需要重启应用（或使用配置中心）
```

---

## P2 测试用例

### T5: 集群限流模式

**测试目标**：验证集群环境下 Sentinel 集群限流生效

**前置条件**：
- 部署多个应用实例
- 配置 Sentinel Token Server

**测试步骤**：
1. 启动 3 个应用实例
2. 从不同实例发送请求
3. 统计总 QPS

**预期结果**：
- 集群总 QPS 超过阈值时触发限流
- 各实例独立限流但共享额度

---

## 单元测试

### SentinelWebFluxFilterTest

```java
@WebFluxTest(SentinelWebFluxFilter.class)
class SentinelWebFluxFilterTest {

    @Autowired
    private WebTestClient webClient;

    @Test
    void spikePathShouldBeFiltered() {
        webClient.get().uri("/spike/order")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .orExpect(status -> status.isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void nonSpikePathShouldNotBeFiltered() {
        webClient.get().uri("/stock/test")
                .exchange()
                .expectStatus().isOk();
    }
}
```

### SentinelBlockHandlerTest

```java
@SpringBootTest
class SentinelBlockHandlerTest {

    @Test
    void rateLimitedResponse() {
        // 模拟 BlockException
        OrderResult result = SentinelBlockHandler.getBlockedResult("test-request-id");

        assertFalse(result.isSuccess());
        assertEquals(429, result.getCode());
        assertEquals("系统繁忙，请稍后重试", result.getMessage());
    }
}
```

---

## 性能测试

### 限流性能开销

**测试目标**：验证 Sentinel 限流本身的性能开销

**测试方法**：
```bash
# 限流开启 vs 关闭对比
wrk -t4 -c100 -d30s --latency http://localhost:8080/spike/limit
```

**验收标准**：
- 限流开启：平均延迟 < 5ms
- 限流关闭：平均延迟 < 3ms
- 性能损耗 < 50%

---

## 故障排查

### 问题：请求未被限流

1. 检查 `sentinel.enabled=true`
2. 检查 Filter 是否被注册：`@Component`
3. 检查路径匹配：Filter 使用 `path.startsWith("/spike")`
4. 查看日志：`SentinelWebFluxFilter` 是否输出

### 问题：限流后应用假死

1. 检查 Sentinel 版本兼容性
2. 检查是否抛出未捕获异常
3. 查看 `BlockException` 是否正确处理

### 问题：限流阈值不生效

1. 确认配置项名称正确：`sentinel.spike.qps-threshold`
2. 确认配置加载顺序：`@PostConstruct` 后生效
3. 确认规则 ID（资源名）一致

---

## 测试报告模板

```markdown
## 测试报告 - [日期]

### 环境信息
- 应用: localhost:8080
- Sentinel 版本: 1.8.6
- QPS 阈值: 10000

### 测试结果汇总

| 用例 | 状态 | 实际 QPS | 限流 QPS | 备注 |
|------|------|----------|----------|------|
| T1 | ✅ 通过 | - | - | Filter 正常拦截 |
| T2 | ✅ 通过 | 15000 | 10000 | 10k 阈值正确 |
| T3 | ✅ 通过 | - | - | 其他接口不受影响 |
| T4 | ⏳ 待测 | - | - | 需要动态配置 |
| T5 | ⏳ 待测 | - | - | 需要集群环境 |

### 限流性能数据

| 场景 | 平均延迟 | P99 延迟 | QPS |
|------|----------|----------|-----|
| 限流关闭 | 2ms | 5ms | 50000 |
| 限流开启 | 3ms | 8ms | 48000 |

### 问题记录
| 问题 | 严重度 | 状态 | 解决方案 |
|------|--------|------|----------|
| 无 | - | - | - |
```
