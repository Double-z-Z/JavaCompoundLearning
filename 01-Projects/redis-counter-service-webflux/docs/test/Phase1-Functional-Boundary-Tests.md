# MultiSKU 订单服务 - 功能与边界测试文档

> **测试范围**：功能测试、边界测试
> **不包括**：压力测试（压力测试在工作空间 `performance-testing` 中完成）
> **后续阶段**：Phase 2 - 数据倾斜测试

---

## 测试环境

- 框架：`Spring WebFlux` + `spring-boot-starter-data-redis-reactive`
- 压测工具：Embedded Redis / Testcontainers
- 验证方式：`@SpringBootTest` + `WebTestClient`

---

## 测试模板

```markdown
### T-XXX: <测试名称>

**场景**: <简要描述>

**前置条件**:
- SKU: [初始化库存]
- 请求: [OrderItem列表]

**执行步骤**:
1. 调用 `placeOrder()`

**预期结果**:
- success: true/false
- decremented: {sku: remaining, ...}
- failed: {sku: qty, ...} (如有)
- 库存验证: [各SKU最终库存]
```

---

## G1 - 空值与边界

### T-001: 空列表订单

**场景**: 提交订单项列表为空

**前置条件**:
- SKU: (无)
- 请求: `OrderItem[] = []`

**执行步骤**:
1. 调用 `placeOrder()`

**预期结果**:
- success: `false`
- message: `"Empty order"`
- decremented: `{}`
- failed: `{}`

---

### T-002: null 订单项

**场景**: 提交订单项为 null

**前置条件**:
- SKU: (无)
- 请求: `OrderItem[] = null`

**执行步骤**:
1. 调用 `placeOrder()`

**预期结果**:
- success: `false`
- message: `"Empty order"`
- decremented: `{}`
- failed: `{}`

---

### T-003: 单个SKU正常扣减

**场景**: 单个SKU，库存充足

**前置条件**:
- SKU: `sku-a` → 库存 `100`
- 请求: `[{sku: "sku-a", qty: 10}]`

**执行步骤**:
1. 调用 `placeOrder()`

**预期结果**:
- success: `true`
- decremented: `{"sku-a": 90}`
- failed: `{}`
- 库存验证: Redis `stock:sku-a` = `90`

---

## G2 - 成功场景

### T-010: 多SKU全部成功

**场景**: 多个SKU，库存全部充足

**前置条件**:
- SKU: `sku-a` → 库存 `100`, `sku-b` → 库存 `200`, `sku-c` → 库存 `300`
- 请求: `[{sku: "sku-a", qty: 10}, {sku: "sku-b", qty: 20}, {sku: "sku-c", qty: 30}]`

**执行步骤**:
1. 调用 `placeOrder()`

**预期结果**:
- success: `true`
- decremented: `{"sku-a": 90, "sku-b": 180, "sku-c": 270}`
- failed: `{}`
- 库存验证: `stock:sku-a`=90, `stock:sku-b`=180, `stock:sku-c`=270

---

### T-011: SKU库存刚好等于请求量

**场景**: SKU库存恰好等于请求数量，扣减后为0

**前置条件**:
- SKU: `sku-a` → 库存 `50`
- 请求: `[{sku: "sku-a", qty: 50}]`

**执行步骤**:
1. 调用 `placeOrder()`

**预期结果**:
- success: `true`
- decremented: `{"sku-a": 0}`
- failed: `{}`
- 库存验证: Redis `stock:sku-a` = `0`

**说明**: Lua脚本 `stock >= quantity` 条件满足，应返回扣减后的值（0），不应返回-1

---

## G3 - 失败与补偿

### T-020: 单SKU库存不足

**场景**: 单个SKU，库存不足以完成请求

**前置条件**:
- SKU: `sku-a` → 库存 `5`
- 请求: `[{sku: "sku-a", qty: 10}]`

**执行步骤**:
1. 调用 `placeOrder()`

**预期结果**:
- success: `false`
- decremented: `{}`
- failed: `{"sku-a": 10}`
- 库存验证: Redis `stock:sku-a` = `5` (未变化)

---

### T-021: 多SKU部分失败（部分库存不足）

**场景**: 多个SKU中部分库存不足，触发补偿机制

**前置条件**:
- SKU: `sku-a` → 库存 `100`, `sku-b` → 库存 `5`
- 请求: `[{sku: "sku-a", qty: 10}, {sku: "sku-b", qty: 10}]`

**执行步骤**:
1. 调用 `placeOrder()`

**预期结果**:
- success: `false`
- message: 包含 `"Partial failure"`
- decremented: `{}` (因为触发补偿，已扣减的 sku-a 被回滚)
- failed: `{"sku-b": 10}`
- 库存验证: `stock:sku-a` = `100` (已回滚), `stock:sku-b` = `5` (未变化)

**补偿验证重点**:
- [ ] 确认 sku-a 的扣减被回滚到原始值
- [ ] 确认 failedMap 正确记录了失败的 SKU

---

### T-022: 多SKU全部失败

**场景**: 所有SKU库存都不足，无需补偿

**前置条件**:
- SKU: `sku-a` → 库存 `5`, `sku-b` → 库存 `5`
- 请求: `[{sku: "sku-a", qty: 10}, {sku: "sku-b", qty: 10}]`

**执行步骤**:
1. 调用 `placeOrder()`

**预期结果**:
- success: `false`
- decremented: `{}`
- failed: `{"sku-a": 10, "sku-b": 10}`
- 库存验证: `stock:sku-a`=5, `stock:sku-b`=5 (均未变化)

**说明**: 由于没有任何SKU扣减成功，补偿逻辑不会执行任何操作

---

### T-023: 补偿后再次下单成功

**场景**: 部分失败触发补偿后，用补偿后的库存再次下单

**前置条件**:
- SKU: `sku-a` → 库存 `100`, `sku-b` → 库存 `5`
- 第一次请求: `[{sku: "sku-a", qty: 10}, {sku: "sku-b", qty: 10}]`

**执行步骤**:
1. 第一次调用 `placeOrder()` → 失败，sku-a 被回滚
2. 第二次请求: `[{sku: "sku-a", qty: 10}, {sku: "sku-b", qty: 5}]`
3. 第二次调用 `placeOrder()` → 应成功

**预期结果**:
- 第一次: success=false, sku-a回滚到100, sku-b保持5
- 第二次: success=true, decremented={"sku-a": 90, "sku-b": 0}

---

## G4 - 边界值

### T-030: 请求数量为0

**场景**: SKU请求数量为0

**前置条件**:
- SKU: `sku-a` → 库存 `100`
- 请求: `[{sku: "sku-a", qty: 0}]`

**执行步骤**:
1. 调用 `placeOrder()`

**预期结果**:
- success: `true`
- decremented: `{"sku-a": 100}` 或 `{"sku-a": 100}` (取决于实现)
- failed: `{}`

**Lua脚本行为**: `stock >= 0` 永远为true，会执行 `DECRBY key 0`，库存不变

**需确认**: 返回100还是99？扣减0是否应该被视为"无操作"？

---

### T-031: 请求数量为负数

**场景**: SKU请求数量为负数

**前置条件**:
- SKU: `sku-a` → 库存 `100`
- 请求: `[{sku: "sku-a", qty: -10}]`

**执行步骤**:
1. 调用 `placeOrder()`

**预期结果**:
- 行为未定：可能是成功（库存变成110），也可能报错

**Lua脚本行为**:
```lua
local quantity = tonumber(ARGV[1])  -- -10
if stock >= quantity then            -- 100 >= -10 为 true
    return redis.call('DECRBY', KEYS[1], quantity)  -- DECRBY key -10 = INCRBY key 10
end
-- 库存会变成 110（增加）
```

**建议**: 应在应用层或Lua脚本中校验 qty > 0

---

### T-032: 不存在的SKU

**场景**: 请求一个从未初始化过的SKU

**前置条件**:
- SKU: (无，`stock:new-sku` 不存在)
- 请求: `[{sku: "new-sku", qty: 10}]`

**执行步骤**:
1. 调用 `placeOrder()`

**预期结果**:
- 取决于Lua脚本对nil的处理

**Lua脚本行为**:
```lua
local stock = tonumber(redis.call('GET', KEYS[1]) or 0)  -- nil or 0 → 0
local quantity = tonumber(ARGV[1])  -- 10
if 0 >= 10 then  -- false
    return -1  -- 库存不足
end
```

**结论**: 不存在的SKU视为库存0，会返回-1（失败）

---

## G5 - 重复SKU

### T-040: 同一SKU出现多次

**场景**: 订单中同一SKU出现两次，请求量相加

**前置条件**:
- SKU: `sku-a` → 库存 `100`
- 请求: `[{sku: "sku-a", qty: 10}, {sku: "sku-a", qty: 20}]`

**执行步骤**:
1. 调用 `placeOrder()`

**预期结果**:
- success: `true` (取决于总请求量是否超过库存)
- 实际扣减: `100 - 10 - 20 = 70`
- decremented: `{"sku-a": 70}`

**超卖风险**:
- 如果代码有缺陷，可能出现 `100 - 10 - 20 = 80` (第二次扣减基于原始值)
- 这是并行执行未正确处理的典型问题

**验证重点**:
- [ ] 检查最终库存是否为70（正确）还是80（错误）
- [ ] 如果失败，是否正确返回 failed

---

## 测试执行顺序建议

```
G1 (T-001 → T-002 → T-003) → G2 (T-010 → T-011) → G3 (T-020 → T-021 → T-022 → T-023) → G4 (T-030 → T-031 → T-032) → G5 (T-040)
```

**理由**:
1. 先验证基础空值处理
2. 再验证正常成功路径
3. 然后验证失败和补偿机制
4. 接着测试边界值
5. 最后测试特殊情况（重复SKU）

---

## 待确认问题

| 问题 | 说明 |
|------|------|
| T-030 扣减0的处理 | Lua `DECRBY key 0` 是否会导致库存不变？ |
| T-031 负数校验 | 是否需要在应用层校验 qty > 0？ |
| T-040 超卖风险 | 并行flatMap是否存在竞态条件？ |

---

## Phase 2 预告（数据倾斜测试）

| 测试场景 | 描述 |
|----------|------|
| 热点SKU集中 | 同一SKU大量并发请求，验证串行化和补偿正确性 |
| 数据倾斜下的部分失败 | 热点SKU库存耗尽时，其他SKU请求的处理 |
| 热点SKU补偿性能 | 大量并发失败时的补偿延迟 |
