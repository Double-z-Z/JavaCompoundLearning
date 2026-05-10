# Layer 1: CDN/边缘节点设计

## 职责
- 静态资源缓存：减少源站压力
- 边缘限流：IP 级请求限制
- Bot 检测：区分人机访问

## 架构图

```
用户请求
    ↓
[CDN 边缘节点]
    ├── 静态资源 → 直接返回缓存
    ├── 动态请求 → 限流检查
    │        ├── 正常 → 回源
    │        └── 异常 → 返回验证码/拦截
    ↓
[源站]
```

## CDN 边缘限流配置

### Cloudflare 限流规则

```json
{
  "description": "Spike protection rate limit",
  "type": "request",
  "expression": "true",
  "ratelimit": {
    "requests_per_unit": 100,
    "unit": "minute",
    "key": "cf.client.rw"
  }
}
```

### 阿里云 CDN 配置

```json
{
  "RuleName": "spike-limit",
  "Condition": "request_uri contains '/spike'",
  "Action": {
    "Type": "RateLimit",
    "Value": 100
  }
}
```

## Bot 检测与挑战

### 验证码触发条件

| 条件 | 阈值 | 动作 |
|------|------|------|
| IP 请求频率 | > 60 req/min | 验证码 |
| Session 请求频率 | > 30 req/min | 验证码 |
| User-Agent 异常 | 已知 Bot | 直接拦截 |
| 地理位置异常 | 非秒杀区域 | 验证码 |

### 验证码类型

1. **无感验证**（推荐）
   - Cloudflare Turnstile
   - Google reCAPTCHA v3

2. **轻度验证码**
   - 滑动拼图
   - 点选文字

3. **重度验证码**
   - 短信验证码
   - 图形验证码

## 边缘节点配置示例

### Nginx 限流配置

```nginx
# IP 限流
limit_req_zone $binary_remote_addr zone=ip_limit:10m rate=10r/s;

# Server 级限流
limit_req_zone $server_name zone=server_limit:10m rate=1000r/s;

server {
    location /spike {
        # IP 限流
        limit_req zone=ip_limit burst=20 nodelay;

        # 验证 Header
        if ($http_x-forwarded-for ~* "^(1\.1\.1\.1|2\.2\.2\.2)$") {
            return 403;
        }

        proxy_pass http://spike-backend;
    }
}
```

### OpenResty Bot 检测

```lua
access_by_lua_block {
    local user_agent = ngx.var.http_user_agent
    local bot_patterns = {
        "curl", "wget", "scrapy", "python-requests"
    }

    for _, pattern in ipairs(bot_patterns) do
        if string.find(user_agent, pattern) then
            ngx.exit(ngx.HTTP_FORBIDDEN)
        end
    end

    -- 检查 robots.txt 尊重情况
    if ngx.var.request_uri == "/robots.txt" then
        ngx.exit(ngx.HTTP_NOT_FOUND)
    end
}
```

## 缓存策略

### 静态资源缓存

```nginx
location ~* \.(css|js|jpg|png|svg)$ {
    expires 1d;
    add_header Cache-Control "public";
}
```

### 动态请求不缓存

```nginx
location /spike {
    proxy_cache off;
    proxy_pass http://spike-backend;
}
```

## 验收标准

- [ ] 异常 IP 被自动拦截
- [ ] 验证码有效区分人机
- [ ] 源站压力显著降低
- [ ] 正常用户无感知延迟