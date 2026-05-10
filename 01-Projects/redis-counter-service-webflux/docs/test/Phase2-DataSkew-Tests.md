# MultiSKU 订单服务 - 数据倾斜测试文档

> **测试范围**：数据倾斜场景下的并发测试
> **不包括**：压力测试（压力测试在工作空间 `performance-testing` 中完成）
> **前置阶段**：Phase 1 - 功能与边界测试

---

## 测试环境

- 框架：`Spring WebFlux` + `spring-boot-starter-data-redis-reactive`
- 压测工具：Embedded Redis / Testcontainers
- 验证方式：`@SpringBootTest` + `WebTestClient` + `CountDownLatch`

---

## T-050: 单热点SKU高并发

**场景**：100个并发请求同时扣减同一SKU（库存=50），验证串行化和补偿正确性

**前置条件**：
- SKU `hot-sku` → 库存 `50`
- 100个并发请求，每个请求 qty=1

**执行步骤**：
1. 使用 `CountDownLatch` + `ExecutorService` 发起100个并发请求
2. 记录每个请求的完成时间和结果

**预期结果**：
- 前50个请求成功（库存从50→0）
- 后50个请求失败（库存不足）
- 无超卖、无数据丢失

**验证断言**：
- `finalStock >= 0`（无超卖）
- `successCount == 50`
- `failCount == 50`
- `successCount + failCount == 100`

---

## T-051: 多SKU部分失败（部分库存不足）

**场景**：多SKU订单中，部分SKU库存不足，触发补偿

**前置条件**：
- SKU `sku-a` → 库存 `100`
- SKU `sku-b` → 库存 `5`
- 请求：`[{sku-a: 10}, {sku-b: 10}]`

**执行步骤**：
1. 调用 `placeOrder()`

**预期结果**：
- success=false，message包含"Partial failure"
- sku-a 被回滚到 100
- sku-b 保持 5（未变化）
- failed={sku-b: 10}

---

## T-052: 补偿链完整性测试

**场景**：验证补偿后再次下单仍能成功

**前置条件**：
- SKU `sku-a` → 库存 `100`
- SKU `sku-b` → 库存 `5`
- 第一次请求：`[{sku-a: 10}, {sku-b: 10}]`

**执行步骤**：
1. 调用 `placeOrder()` → 失败，触发补偿（sku-a 回滚到 100）
2. 再次调用 `placeOrder()` → `[{sku-a: 10}, {sku-b: 5}]`

**预期结果**：
- 第一次：success=false，sku-a 回滚到 100
- 第二次：success=true，sku-a 扣减到 90，sku-b 扣减到 0

---

## T-053: 热点SKU + 普通SKU 混合场景

**场景**：一个订单中同时包含热点SKU和普通SKU

**前置条件**：
- SKU `hot-sku` → 库存 `3`
- SKU `normal-sku` → 库存 `100`
- 请求：`[{hot-sku: 2}, {normal-sku: 10}]`

**预期结果**：
- success=true
- decremented={hot-sku: 1, normal-sku: 90}

---

## T-054: 热点SKU库存刚好耗尽

**场景**：验证库存刚好耗尽时的行为

**前置条件**：
- SKU `sku` → 库存 `5`
- 5个请求，每个 qty=1

**执行步骤**：
1. 连续5次调用 `placeOrder()`，每次 qty=1
2. 第6次调用 `placeOrder()`，qty=1

**预期结果**：
- 前5次：success=true
- 第6次：success=false，failed={sku: 1}
- 最终库存：0

---

## T-055: 多SKU全部失败（无需补偿）

**场景**：所有SKU都库存不足，无需触发补偿

**前置条件**：
- SKU `sku-a` → 库存 `5`
- SKU `sku-b` → 库存 `5`
- 请求：`[{sku-a: 10}, {sku-b: 10}]`

**预期结果**：
- success=false
- decremented={}
- failed={sku-a: 10, sku-b: 10}
- 库存均未变化

**执行步骤**：
1. 调用 `placeOrder()`

**预期结果**：
- success=true（因为库存都充足）
- decremented={hot-sku: 1, normal-sku: 90}
- 热点SKU库存从3变成1

---

## 验证方式

1. **并发测试**：使用 `CountDownLatch` + `ExecutorService`
2. **顺序测试**：直接调用 `orderService.placeOrder()`
3. **库存验证**：每个测试后验证 Redis 库存状态

---

## Phase 2 测试输出位置

测试代码：`src/test/java/com/example/counter/MultiSkuOrderServiceDataSkewTest.java`