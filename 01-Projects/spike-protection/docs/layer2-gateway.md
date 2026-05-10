# Layer 2: 网关层设计

## 职责
- 签名验证：杜绝请求篡改
- 令牌桶限流：控制请求速率
- 防重放：防止请求重复使用

## 架构图

```
用户请求
    ↓
[OpenResty]
    ├── 签名校验 (HMAC-SHA256)
    ├── 令牌桶限流
    └── 防重放 (nonce + timestamp)
    ↓
[后端服务]
```

## 签名校验流程

```
1. 客户端生成签名:
   sign_string = "qty=1&sku=SKU001&t=1699999999&n=abc123"
   signature = HMAC-SHA256(secret_key, sign_string)

2. 请求Header:
   X-Signature: {signature}
   X-Timestamp: 1699999999
   X-Nonce: abc123

3. OpenResty 验证:
   - 检查 timestamp 是否在 5 分钟内
   - 检查 nonce 是否已使用（Redis Set NX）
   - 验证签名是否匹配
```

## OpenResty 配置

```nginx
server {
    listen 80;
    server_name spike.example.com;

    location /spike/order {
        # 1. 获取请求参数
        access_by_lua_block {
            local headers = ngx.req.get_headers()
            local args = ngx.req.get_uri_args()

            -- 验证 timestamp (5分钟内有效)
            local timestamp = tonumber(headers["x-timestamp"])
            local now = os.time()
            if not timestamp or math.abs(now - timestamp) > 300 then
                ngx.exit(ngx.HTTP_FORBIDDEN)
            end

            -- 验证 nonce (防重放)
            local nonce = headers["x-nonce"]
            local nonce_key = "nonce:" .. nonce
            local redis = require("resty.redis"):new()
            local ok, err = redis:connect("127.0.0.1", 6379)
            local exists = redis:get(nonce_key)
            if exists then
                ngx.exit(ngx.HTTP_FORBIDDEN)  -- nonce 已使用，重放攻击
            end
            redis:setex(nonce_key, 300, "1")  -- 5分钟过期

            -- 验证签名
            local signature = headers["x-signature"]
            local sign_str = "qty=" .. args.qty .. "&sku=" .. args.sku .. "&t=" .. timestamp .. "&n=" .. nonce
            local expected = ngx.encode_base64(ngx.hmac_sha1(secret_key, sign_str))
            if signature ~= expected then
                ngx.exit(ngx.HTTP_FORBIDDEN)
            end
        }

        -- 2. 令牌桶限流
        limit_req_zone $binary_remote_addr zone=spike:10m rate=100r/s;
        limit_req zone=spike burst=200;

        proxy_pass http://spike-backend;
    }
}
```

## 签名算法

### 客户端签名生成 (JavaScript)
```javascript
function sign(params, secret) {
    // 1. 按 key 排序
    const sorted = Object.keys(params).sort().map(k => `${k}=${params[k]}`).join('&')
    // 2. HMAC-SHA256
    return crypto.createHmac('sha256', secret).update(sorted).digest('hex')
}

const params = {
    sku: 'SKU001',
    qty: 1,
    t: Math.floor(Date.now() / 1000),
    n: Math.random().toString(36).substr(2, 10)
}
const signature = sign(params, 'your-secret-key')
```

### 客户端签名生成 (Java)
```java
public static String sign(Map<String, String> params, String secret) {
    String str = params.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(e -> e.getKey() + "=" + e.getValue())
        .collect(Collectors.joining("&"));

    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
    return Base64.getEncoder().encodeToString(mac.doFinal(str.getBytes()));
}
```

## 限流规则

| 维度 | 阈值 | 说明 |
|------|------|------|
| IP | 100 req/s | 单 IP 限流 |
| User | 10 req/s | 单用户限流 |
| 全局 | 50k req/s | 集群限流 |

## 验收标准

- [ ] 未带签名请求返回 403
- [ ] 篡改参数后签名不匹配返回 403
- [ ] 重放请求（相同 nonce）返回 403
- [ ] 超限请求被限流返回 429
- [ ] 正常请求通过