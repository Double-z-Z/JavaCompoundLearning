# MultiSKU 订单服务 - JUnit 测试用例索引

> **本文档性质**：JUnit 测试索引，**非人工测试报告**
> **功能测试代码**：`src/test/java/com/example/counter/MultiSkuOrderServiceTest.java`（覆盖 G1-G5）
> **数据倾斜测试代码**：`src/test/java/com/example/counter/MultiSkuOrderServiceDataSkewTest.java`（覆盖 G6）
> **不包括**：压力测试（压力测试见 [l4-cache-performance-report.md](./l4-cache-performance-report.md)）

---

## 1. 测试范围

| 分组 | 测试类 | 用例编号 | 说明 |
|------|--------|----------|------|
| G1-G5 | `MultiSkuOrderServiceTest` | T-001 ~ T-040 | 功能、边界、失败补偿、重复 SKU |
| G6 | `MultiSkuOrderServiceDataSkewTest` | T-050 ~ T-055 | 并发数据倾斜 |

---

## 2. 测试环境

| 组件 | 配置 | 说明 |
|------|------|------|
| 框架 | Spring WebFlux + spring-boot-starter-data-redis-reactive | 响应式非阻塞 |
| Redis | Embedded Redis / Testcontainers | 测试隔离 |
| 并发控制 | `CountDownLatch` + `ExecutorService` | G6 数据倾斜场景 |
| Lua 脚本 | `stock_deduct.lua` | 原子扣减与补偿 |

---

## 3. JUnit 参考用例（G1-G5）

> 以下用例由 `MultiSkuOrderServiceTest.java` 自动覆盖，执行结果以 JUnit 报告为准。

### G1 - 空值与边界

| 用例 | 场景 | 预期结果 |
|------|------|----------|
| T-001 | 空列表订单 | success=false, message="Empty order" |
| T-002 | null 订单项 | success=false, message="Empty order" |
| T-003 | 单 SKU 正常扣减 | success=true, decremented={"sku-a": 90} |

### G2 - 成功场景

| 用例 | 场景 | 预期结果 |
|------|------|----------|
| T-010 | 多 SKU 全部成功 | success=true, decremented={sku-a:90, sku-b:180, sku-c:270} |
| T-011 | 库存刚好等于请求量 | success=true, decremented={"sku-a": 0} |

### G3 - 失败与补偿

| 用例 | 场景 | 预期结果 |
|------|------|----------|
| T-020 | 单 SKU 库存不足 | success=false, failed={sku-a:10}, 库存不变 |
| T-021 | 多 SKU 部分失败 | success=false, 触发补偿, sku-a 回滚到 100 |
| T-022 | 多 SKU 全部失败 | success=false, 无需补偿, 库存均不变 |
| T-023 | 补偿后再次下单 | 第二次 success=true, decremented={sku-a:90, sku-b:0} |

### G4 - 边界值

| 用例 | 场景 | 预期结果 | 注意事项 |
|------|------|----------|----------|
| T-030 | 请求数量为 0 | success=true, 库存不变 | Lua `DECRBY key 0` 无实际影响 |
| T-031 | 请求数量为负数 | 行为未定 | `DECRBY key -10` 等价于 `INCRBY key 10`，建议应用层校验 `qty > 0` |
| T-032 | 不存在的 SKU | 视为库存 0，返回失败 | `GET` 不存在返回 `nil`，经 `or 0` 处理后视为 0 |

### G5 - 重复 SKU

| 用例 | 场景 | 预期结果 | 注意事项 |
|------|------|----------|----------|
| T-040 | 同一 SKU 出现多次 | 实际扣减 30，库存变为 70 | 如果代码有缺陷，可能出现 `100 - 10 = 90`（第二次基于原始值扣减） |

---

## 4. JUnit 并发参考用例（G6）

> 以下用例由 `MultiSkuOrderServiceDataSkewTest.java` 自动覆盖，使用 `CountDownLatch` + `ExecutorService` 模拟并发场景，执行结果以 JUnit 报告为准。

| 用例 | 场景 | 预期结果 |
|------|------|----------|
| T-050 | 单热点 SKU 高并发（100 并发扣同一 SKU） | 前 50 成功，后 50 失败，无超卖 |
| T-051 | 多 SKU 部分失败 | success=false, sku-a 回滚到 100 |
| T-052 | 补偿链完整性 | 第二次 success=true, sku-a=90, sku-b=0 |
| T-053 | 热点 + 普通 SKU 混合 | success=true, decremented={hot-sku:1, normal-sku:90} |
| T-054 | 库存刚好耗尽 | 前 5 成功，第 6 失败，最终库存 0 |
| T-055 | 多 SKU 全部失败 | success=false, 库存均未变化 |

> **T-050 并发验证断言**：
> - `finalStock >= 0`（无超卖）
> - `successCount == 50`
> - `failCount == 50`
> - `successCount + failCount == 100`

---

## 5. 命令速查

```bash
# 全部功能测试（G1-G5）
mvn test -Dtest=MultiSkuOrderServiceTest

# 数据倾斜并发测试（G6）
mvn test -Dtest=MultiSkuOrderServiceDataSkewTest

# 全部执行
mvn test -Dtest=MultiSkuOrderServiceTest,MultiSkuOrderServiceDataSkewTest
```

---

**文档性质**: JUnit 测试用例索引（非人工测试报告）
**JUnit 报告**: `target/surefire-reports/`
