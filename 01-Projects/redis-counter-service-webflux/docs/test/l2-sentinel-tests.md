# Sentinel 自适应限流 - 测试报告

> **测试目标**：验证 Sentinel 在五层防御 L3 层的限流能力，包括拦截精度、响应格式、接口隔离性和性能开销。
> **服务器地址**：`10.0.0.142:8080`
> **QPS 阈值**：10,000（默认配置）
> **Sentinel 版本**：1.8.6
> **测试日期**：2026-05-12

---

## 1. 背景与目标

秒杀场景下，L3 层需要通过 Sentinel 对 `/spike/**` 路径进行自适应限流，防止流量击穿后端服务。本次测试验证：
1. 限流 Filter 正常拦截目标路径
2. 限流时返回正确的 429 状态码、响应头和响应体
3. 限流不影响非秒杀接口（如 `/stock/**`）
4. 限流本身的性能开销在可接受范围内

---

## 2. 测试环境

| 组件 | 配置/地址 | 说明 |
|------|-----------|------|
| 应用服务器 | 10.0.0.142:8080 | Spring Boot WebFlux |
| Sentinel | 1.8.6 | 本地规则模式 |
| QPS 阈值 | 10,000 | `sentinel.spike.qps-threshold` |
| 压测端 | Windows PowerShell | 8C16T |
| 压测工具 | bombardier v2.0.2 | Go + fasthttp |

---

## 3. 测试工具与方法

| 工具 | 用途 | 版本 |
|------|------|------|
| bombardier | HTTP 并发压测 | v2.0.2 |
| curl | 单请求验证响应头和响应体 | 8.18.0 |
| WebTestClient | 自动化单元/集成测试 | Spring Boot Test |

### 压测参数

```powershell
bombardier -c 100 -n 500000 -H "Content-Type: application/json" -m POST -f request_body.json http://10.0.0.142:8080/spike/order
```

- 并发连接数：100
- 请求总数：500,000
- 请求体：`{"items":[{"sku":"TEST_QUICK","qty":1}]}`

---

## 4. 测试用例与结果

### T1: Sentinel Filter 正常拦截

| 项目 | 内容 |
|------|------|
| 目标 | 验证 `/spike/**` 路径被限流 Filter 拦截 |
| 步骤 | 发送 GET `/spike/limit` 请求，观察日志 |
| 预期 | 请求被 `SentinelWebFluxFilter` 拦截，日志显示限流检查通过 |
| 实际 | — |
| 状态 | ⏳ 待执行 |

### T2: 限流返回 429 + 响应头

| 项目 | 内容 |
|------|------|
| 目标 | 验证限流时返回正确的状态码和响应头 |
| 步骤 | 1. 并发发送超过阈值的请求 2. 检查响应状态码和响应头 |
| 预期 | HTTP 429, Header `X-Spike-Limit: triggered`, Body 含错误信息 |
| 状态 | ✅ 通过 |

**压测结果**：

```
Bombarding http://10.0.0.142:8080/spike/order with 500000 request(s) using 100 connection(s)
 500000 / 500000 [===================================] 100.00% 12603/s 39s
Done!
Statistics        Avg      Stdev        Max
  Reqs/sec     12672.65    9855.15   47620.73
  Latency        7.89ms     6.49ms   161.23ms
  HTTP codes:
    1xx - 0, 2xx - 351603, 3xx - 0, 4xx - 148397, 5xx - 0
    others - 0
  Throughput:     4.74MB/s
```

**限流响应验证**：

```powershell
curl.exe -v -X POST -H "Content-Type: application/json" -d '{"items":[{"sku":"TEST_QUICK","qty":1}]}' http://10.0.0.142:8080/spike/order
```

```
< HTTP/1.1 429 Too Many Requests
< Content-Type: application/json;charset=UTF-8
< X-Spike-Limit: triggered
< content-length: 113
{"code":429,"success":false,"message":"系统繁忙，请稍后重试","data":null,"requestId":"666c1d82-116916"}
```

### T3: 限流不影响其他接口

| 项目 | 内容 |
|------|------|
| 目标 | 验证限流只影响 `/spike/**`，不影响 `/stock/**` |
| 步骤 | 触发 `/spike/**` 限流的同时访问 `/stock/TEST_QUICK` |
| 预期 | `/spike/**` 返回 429，`/stock/**` 返回 200 |
| 状态 | ✅ 通过 |

**验证命令**：

```powershell
curl.exe -s -w "stock/TEST_QUICK: %{http_code}`n" http://10.0.0.142:8080/stock/TEST_QUICK
```

**实际结果**：

```
9891698
stock/TEST_QUICK: 200
```

### T4: QPS 阈值动态生效

| 项目 | 内容 |
|------|------|
| 目标 | 验证修改配置后限流阈值立即生效 |
| 步骤 | 1. 设置阈值=5，发送 10 个请求 2. 修改阈值为 100，再发送 10 个 |
| 预期 | 第一轮 5 通过 5 限流，第二轮 10 全部通过 |
| 状态 | ⏳ 待测（需要动态配置中心） |

### T5: 集群限流模式

| 项目 | 内容 |
|------|------|
| 目标 | 验证集群环境下 Sentinel 集群限流生效 |
| 前置条件 | 部署 3 个实例，配置 Sentinel Token Server |
| 步骤 | 从不同实例发送请求，统计总 QPS |
| 预期 | 集群总 QPS 超过阈值时触发限流，各实例共享额度 |
| 状态 | ⏳ 待测（需要集群环境） |

---

## 5. 核心数据

### 压测性能指标

| 指标 | 数值 |
|------|------|
| **请求总数** | 500,000 |
| **并发连接数** | 100 |
| **总耗时** | 39 秒 |
| **平均 QPS** | 12,672.65 |
| **峰值 QPS** | 47,620.73 |
| **平均延迟** | 7.89 ms |
| **最大延迟** | 161.23 ms |
| **吞吐量** | 4.74 MB/s |

### HTTP 响应分布

| 状态码 | 数量 | 占比 |
|--------|------|------|
| 2xx | 351,603 | 70.3% |
| 4xx (429) | 148,397 | 29.7% |
| 5xx | 0 | 0% |

### 验证结果汇总

| 验证项 | 预期结果 | 实际结果 | 状态 |
|--------|----------|----------|------|
| 压测 QPS | 接近 10,000 | **12,672.65** | ✅ 达标 |
| 429 响应数 | > 0 | **148,397** | ✅ 达标 |
| HTTP Status | `429 Too Many Requests` | `429 Too Many Requests` | ✅ 达标 |
| 响应头 | `X-Spike-Limit: triggered` | `X-Spike-Limit: triggered` | ✅ 达标 |
| 响应体格式 | `code:429, success:false` | 完全匹配 | ✅ 达标 |
| `/stock` 接口 | 返回 200 | 返回 200 | ✅ 达标 |

---

## 6. 关键发现

### 发现 1：限流精确触发

**平均 QPS 12,672 超过阈值 10,000**，约 29.7% 的请求被正确限流为 429，说明 Sentinel 的 QPS 统计和拦截逻辑工作正常。

### 发现 2：接口隔离性验证通过

在 `/spike/order` 被大量压测触发限流的同时，`/stock/TEST_QUICK` 仍能正常返回 200，证明限流规则的路径匹配是精确的，不存在误伤。

### 发现 3：响应格式规范

限流响应包含：
- 正确的 HTTP 状态码 429
- 自定义响应头 `X-Spike-Limit: triggered`
- 标准错误体（含 `requestId` 便于链路追踪）

### 发现 4：性能开销待量化

本次测试是**全链路压测**（含 Redis + MQ），Sentinel 本身的纯限流开销尚未单独剥离。当前 7.89ms 是全链路延迟，非 Sentinel 单点开销。

---

## 7. 命令速查

### 准备请求体文件

```powershell
@'
{"items":[{"sku":"TEST_QUICK","qty":1}]}
'@ | Out-File -FilePath request_body.json -Encoding utf8
```

### 并发压测

```powershell
bombardier -c 100 -n 500000 -H "Content-Type: application/json" -m POST -f request_body.json http://10.0.0.142:8080/spike/order
```

### 验证限流响应

```powershell
curl.exe -v -X POST -H "Content-Type: application/json" -d '{"items":[{"sku":"TEST_QUICK","qty":1}]}' http://10.0.0.142:8080/spike/order
```

### 验证其他接口不受影响

```powershell
curl.exe -s -w "stock/TEST_QUICK: %{http_code}`n" http://10.0.0.142:8080/stock/TEST_QUICK
```

### 单元测试

```bash
mvn test -Dtest=SentinelWebFluxFilterTest,SentinelBlockHandlerTest
```

---

**测试状态**: ✅ T2/T3 已通过 | ⏳ T1/T4/T5 待执行
