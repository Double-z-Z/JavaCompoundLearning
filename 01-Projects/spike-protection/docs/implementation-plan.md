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

## Phase 2: Layer 3 接入层限流 (P1→P0)

### 目标
保护 Redis 不超过 200k QPS，防止热点 SKU 打垮集群。

### 设计思路

```
请求 → [SentinelWebFluxFilter]
              ↓
       SphU.entry("spike") 限流检查
              ↓
       通过 → 继续处理
       阻塞 → 直接返回 429（不走后续逻辑）
```

**关键设计点：**
- **WebFlux Filter 级别拦截**：在 Controller 之前做限流检查，避免无效请求打到业务逻辑
- **资源名统一管理**：`SentinelConfig.SPIKE_RESOURCE = "spike"`
- **非阻塞优先**：Filter 返回 429 时不进入业务逻辑，减少资源消耗
- **响应头标识**：`X-Spike-Limit: triggered` 便于客户端区分限流场景

**为什么不直接用 @SentinelResource？**
- WebFlux 是非阻塞响应式编程，传统注解方式不完全兼容
- Filter 方式更通用，能在请求入口处统一处理

### 方案
- 工具：Alibaba Sentinel
- 策略：Sentinel WebFlux Filter + 用户级限流

### 实施步骤

1. **Sentinel 依赖** (pom.xml)
   ```xml
   <dependency>
       <groupId>com.alibaba.csp</groupId>
       <artifactId>sentinel-core</artifactId>
       <version>1.8.6</version>
   </dependency>
   <dependency>
       <groupId>com.alibaba.csp</groupId>
       <artifactId>sentinel-spring-webflux-adapter</artifactId>
       <version>1.8.6</version>
   </dependency>
   ```

2. **Sentinel 配置类**
   - `SentinelConfig.java` - 限流规则初始化
   - `SentinelWebFluxFilter.java` - WebFlux Filter 拦截 /spike/** 请求
   - `SentinelBlockHandler.java` - 限流 BlockException 处理

3. **限流配置** (application.yml)
   ```yaml
   sentinel:
     enabled: true
     spike:
       resource-name: spike
       qps-threshold: 10000          # 单用户 QPS 阈值
       cluster-threshold: 200000    # 集群总 QPS 阈值
   ```

### 验收标准
- [x] 单用户 QPS 超过阈值被限流
- [x] 单 SKU QPS 超过阈值被限流
- [x] 限流返回 429 "系统繁忙"

---

## Phase 3: Layer 4 异步队列削峰 (P1→P0)

### 目标
应用层不阻塞，用户体验平滑。

### 设计思路

**同步预扣 + 异步下单**
```
请求                    响应                    MQ Consumer
  │                      │                        │
  ├─→ Lua 原子扣减 ──────┤                        │
  │   (同步, <10ms)      │                        │
  │                      │                        │
  ├─→ 扣减成功 ─────────→├─ 202 (排队中) ────────→├─→ 创建订单
  │                      │   + 发送MQ消息         │   (异步)
  │                      │                        │
  ├─→ 扣减失败 ─────────→├─ 200 (库存不足)         │
  │                      │                        │
```

**为什么预扣和MQ发送要同步？**
- **保证不丢单**：预扣成功后必须确保消息进入 MQ，否则库存扣了但订单没创建
- **解耦下游**：预扣成功后立即返回，用户不用等待订单创建
- **吞吐量提升**：预扣 <10ms，MQ 发送后立即返回，200k QPS 完全可行

**死信队列设计**
```
spike-order-queue ──(消费失败)──→ spike-order-queue.dlq
                                    ↓
                              人工处理/告警
```

**RabbitTemplate 配置**
- `publishOn(boundedElastic)`：避免 MQ 操作阻塞 Netty 线程

### 方案
- 工具：RabbitMQ
- 模式：预扣库存 → 写入 MQ → 异步创建订单

### 实施步骤

1. **RabbitMQ 依赖** (pom.xml)
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-amqp</artifactId>
   </dependency>
   ```

2. **MQ 配置** (application.yml)
   ```yaml
   rabbitmq:
     host: ${RABBITMQ_HOST:localhost}
     port: ${RABBITMQ_PORT:5672}
     spike:
       exchange: spike-exchange
       queue: spike-order-queue
       routing-key: spike.order.create
       prefetch: 100
       concurrent-consumers: 5
   ```

3. **MQ 配置类**
   - `RabbitMQConfig.java` - Exchange/Queue/Binding 配置

4. **MQ 消息**
   - `SpikeOrderMessage.java` - 订单消息 DTO
   - `SpikeOrderMQService.java` - 生产者服务
   - `SpikeOrderConsumer.java` - 消费者服务

5. **秒杀控制器**
   - `SpikeController.java` - `/spike/order` 接口

6. **预扣流程**
   ```
   请求 → Sentinel限流 → Lua扣减 → 成功 → 写入MQ → 返回202排队中
   请求 → Sentinel限流 → Lua扣减 → 失败 → 返回200库存不足
   ```

7. **消费流程**
   ```
   MQ消息 → 创建订单 → 更新状态 → 通知用户
   ```

### 验收标准
- [x] 高并发下接口响应时间 < 100ms（预扣+MQ发送同步完成）
- [x] MQ 消费顺序正确
- [x] 订单不丢失（MQ持久化+DLQ）

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

| Phase | 优先级 | 内容 | 状态 |
|-------|--------|------|------|
| 1 | P0 | Layer 2 网关签名校验 | 待实现 |
| 2 | P0 | Layer 5 Lua 原子扣减 | ✅ 已实现 |
| 3 | P0 | Layer 3 Sentinel 限流 | ✅ 已实现 |
| 4 | P0 | Layer 4 异步队列 | ✅ 已实现 |
| 5 | P1 | Layer 1 CDN 限流 | 待调研 |
| 6 | P1 | Layer 2 行为验证 | 待实现 |
| 7 | P2 | Layer 4 分时段批次 | 待调研 |

**已实现**: 3/7 (Lua扣减、Sentinel限流、MQ削峰)