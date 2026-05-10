# 秒杀系统 - 实施计划

## Phase 1: Layer 2 网关层签名校验 (P0)

### 目标
杜绝请求篡改，确保请求参数完整性。

### 方案
- 工具：OpenResty + HMAC-SHA256
- 签名内容：`sku + qty + timestamp + nonce`
- 防重放：timestamp 5分钟内有效，nonce 唯一性检查

### 实施步骤

1. **Nginx/OpenResty 配置**
   ```nginx
   location /spike {
       access_by_lua_block {
           local signature = ngx.req.get_headers()["X-Signature"]
           local args = ngx.req.get_uri_args()
           local expected = hmac_sha256(secret_key, build_sign_string(args))
           if signature ~= expected then
               ngx.exit(ngx.HTTP_FORBIDDEN)
           end
       }
       proxy_pass http://spike-backend;
   }
   ```

2. **签名生成客户端示例**
   ```javascript
   const sign = (params) => {
     const str = Object.keys(params).sort().map(k => `${k}=${params[k]}`).join('&')
     return crypto.createHmac('sha256', secret).update(str).digest('hex')
   }
   ```

### 验收标准
- [ ] 未带签名请求返回 403
- [ ] 篡改参数后请求返回 403
- [ ] 正常签名请求通过

---

## Phase 2: Layer 3 接入层限流 (P1)

### 目标
保护 Redis 不超过 200k QPS，防止热点 SKU 打垮集群。

### 方案
- 工具：Alibaba Sentinel
- 策略：热点参数限流 + 用户级限流

### 实施步骤

1. **Sentinel 规则配置**
   ```java
   FlowRule rule = new FlowRule()
       .setResource("spike")
       .setGrade(RuleConstant.FLOW_GRADE_QPS)
       .setCount(10000)  // 10k QPS/用户
       .setParamFlowItem(new ParamFlowItem().setObject("sku").setCount(1000));
   ```

2. **热点参数限流规则**
   ```java
   ClusterBuilderConfig config = new ClusterBuilderConfig()
       .setThreshold(200000);  // 集群总限流 200k
   ```

### 验收标准
- [ ] 单用户 QPS 超过阈值被限流
- [ ] 单 SKU QPS 超过阈值被限流
- [ ] 限流返回 "系统繁忙"

---

## Phase 3: Layer 4 异步队列削峰 (P1)

### 目标
应用层不阻塞，用户体验平滑。

### 方案
- 工具：RocketMQ / RabbitMQ
- 模式：预扣库存 → 写入 MQ → 异步创建订单

### 实施步骤

1. **消息队列配置**
   - Topic: `spike-orders`
   - Consumer Group: `order-consumer`
   - 延迟队列处理

2. **预扣流程**
   ```
   请求 → Lua扣减 → 成功 → 写入MQ → 返回"排队中"
   请求 → Lua扣减 → 失败 → 直接返回"库存不足"
   ```

3. **消费流程**
   ```
   MQ消息 → 创建订单 → 更新状态 → 通知用户
   ```

### 验收标准
- [ ] 高并发下接口响应时间 < 100ms
- [ ] MQ 消费顺序正确
- [ ] 订单不丢失

---

## Phase 4: Layer 1 CDN 边缘限流 (P2)

### 目标
减少无效请求打到源站。

### 方案
- 工具：Cloudflare / 阿里云 CDN
- 策略：IP 级限流 + 验证码挑战

### 实施步骤

1. **CDN 配置**
   - 启用 Rate Limiting
   - 设置 IP 请求阈值：100 req/min

2. **验证码挑战**
   - Bot 检测触发时返回验证码页面

### 验收标准
- [ ] 异常 IP 被自动拦截
- [ ] 验证码有效区分人机

---

## Phase 5: Layer 4 分时段批次 (P2)

### 目标
提升用户体验与公平感。

### 方案
- 业务逻辑改造
- 分批次投放库存

### 实施步骤

1. **批次设计**
   ```
   10:00 第一批：100件
   10:30 第二批：100件
   11:00 第三批：100件
   ```

2. **批次状态机**
   - `PENDING` → `ACTIVE` → `SOLD_OUT` → `END`

### 验收标准
- [ ] 用户感知批次开始/结束
- [ ] 批次之间库存隔离

---

## 优先级总结

| Phase | 优先级 | 内容 | 预计工时 |
|-------|--------|------|----------|
| 1 | P0 | Layer 2 网关签名校验 | 2天 |
| 2 | P0 | Layer 5 Lua 原子扣减 | ✅ 已实现 |
| 3 | P1 | Layer 3 Sentinel 限流 | 3天 |
| 4 | P1 | Layer 4 异步队列 | 5天 |
| 5 | P2 | Layer 1 CDN 限流 | 2天 |
| 6 | P2 | Layer 4 分时段批次 | 3天 |

**总计**：约 15 天