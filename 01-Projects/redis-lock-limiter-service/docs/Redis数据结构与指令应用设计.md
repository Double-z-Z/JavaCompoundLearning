# Redis 数据结构与指令应用设计

> 本文档详细说明项目中各模块如何使用 Redis 数据结构和指令

---

## 1. 分布式锁 (DistributedLock)

### 1.1 数据结构选择

**数据结构**：String

### 1.2 核心指令

| 操作 | 指令 | 说明 |
|------|------|------|
| 加锁 | `SET key value NX EX ttl` | 原子设置值 + 过期时间 |
| 解锁 | `GET key` + `DEL key` | Lua 脚本保证原子性 |
| 续期 | `GET key` + `EXPIRE key ttl` | Lua 脚本保证原子性 |
| 检查 | `EXISTS key` | 键是否存在 |

### 1.3 Lua 脚本实现

#### 1.3.1 加锁脚本 (acquire_lock.lua)

```lua
-- SET key value NX EX ttl
-- 返回 1 表示成功，0 表示失败
local key = KEYS[1]
local value = ARGV[1]
local ttl = ARGV[2]

local result = redis.call('SET', key, value, 'NX', 'EX', ttl)
if result then
    return 1
else
    return 0
end
```

**设计要点**：
- `NX`：键不存在时才设置，保证互斥
- `EX ttl`：自动过期，防止死锁
- 返回值 1/0：便于 Java 处理

#### 1.3.2 解锁脚本 (release_lock.lua)

```lua
-- 校验后删除，保证只能删除自己的锁
local key = KEYS[1]
local value = ARGV[1]

local current = redis.call('GET', key)
if current == value then
    redis.call('DEL', key)
    return 1
else
    return 0
end
```

**设计要点**：
- `GET` 后 `DEL`：非原子操作，可能误删
- Lua 脚本保证 Check-and-Delete 原子性
- 校验 value，只删除自己持有的锁

#### 1.3.3 续期脚本 (renew_lock.lua)

```lua
-- 只能续期自己持有的锁
local key = KEYS[1]
local value = ARGV[1]
local ttl = ARGV[2]

local current = redis.call('GET', key)
if current == value then
    redis.call('EXPIRE', key, ttl)
    return 1
else
    return 0
end
```

### 1.4 Key 设计

```
lock:{resource}
示例：lock:order:123, lock:inventory:SKU-001
```

### 1.5 唯一标识

每个锁实例生成 UUID 作为 value，用于标识锁持有者。

---

## 2. 三种限流策略对比

| 策略 | 数据结构 | 算法特点 | 突发支持 | 边界穿透 |
|------|---------|---------|---------|----------|
| **固定窗口** | String | 时间片独立计数 | 无 | 有 |
| **滑动窗口** | Sorted Set | 精确时间窗口，无穿透 | 无 | 无 |
| **令牌桶** | Hash | 令牌补充速率，支持突发 | 有 | 无 |

---

## 3. 固定窗口限流器 (FixedWindowLimiter)

### 3.1 数据结构

**数据结构**：String（计数器）

### 3.2 核心指令

| 操作 | 指令 | 说明 |
|------|------|------|
| 计数 | `INCR key` | 原子递增 |
| 设置过期 | `EXPIRE key seconds` | 仅首次创建时设置 |

### 3.3 Lua 脚本

```lua
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window_sec = tonumber(ARGV[2])

local current = redis.call('INCR', key)

-- 只有第一次创建 key 时才设置过期
if current == 1 then
    redis.call('EXPIRE', key, window_sec)
end

if current <= limit then
    return {1, current, limit - current}
else
    return {0, current, 0}
end
```

### 3.4 Key 设计

```
limiter:fixed:{prefix}:{timeSlice}
示例：limiter:fixed:user:12345:2023051612  (2023-05-16 12:00:00开始的窗口)
```

### 3.5 特点

- **优点**：实现简单，性能高
- **缺点**：有边界穿透问题（窗口切换瞬间可 2x 通过）
- **适用**：内部服务粗粒度限流、统计计数

---

## 4. 滑动窗口限流器 (SlidingWindowLimiter)

### 4.1 数据结构

**数据结构**：Sorted Set (ZSet)

### 4.2 核心指令

| 操作 | 指令 | 说明 |
|------|------|------|
| 添加请求 | `ZADD key score member` | 添加时间戳作为分数 |
| 清理过期 | `ZREMRANGEBYSCORE key 0 timestamp` | 删除窗口外的记录 |
| 统计计数 | `ZCARD key` | 获取当前请求数 |
| 设置过期 | `PEXPIRE key ms` | 防止内存泄漏 |

### 4.3 Lua 脚本

```lua
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local member = ARGV[4]

local window_start = now - window

-- 清理窗口外的旧记录
redis.call('ZREMRANGEBYSCORE', key, 0, window_start)

-- 获取当前窗口内的请求数
local current = redis.call('ZCARD', key)

if current < limit then
    -- 允许通过，添加当前请求
    redis.call('ZADD', key, now, member)
    -- 延长 key 过期时间，防止冷 key 残留
    redis.call('PEXPIRE', key, window)
    return {1, current + 1, limit - current - 1}
else
    return {0, current, 0}
end
```

### 4.4 Key 设计

```
limiter:sliding:{key}
示例：limiter:sliding:user:12345, limiter:sliding:ip:192.168.1.1
```

### 4.5 数据结构图解

```
limiter:sliding:user:12345
┌─────────────────────────────┐
│  Score (timestamp) │ Member │
├─────────────────────────────┤
│  1715856000000    │ req-uuid-1  │
│  1715856000100    │ req-uuid-2  │
│  1715856000200    │ req-uuid-3  │
└─────────────────────────────┘
  ↑ 窗口起点 = now - windowMs
```

### 4.6 特点

- **优点**：精确限流，无边界穿透
- **缺点**：内存消耗与窗口内请求数成正比
- **适用**：API 网关限流、精确 QPS 控制

---

## 5. 令牌桶限流器 (TokenBucketLimiter)

### 5.1 数据结构

**数据结构**：Hash

### 5.2 核心指令

| 操作 | 指令 | 说明 |
|------|------|------|
| 获取状态 | `HMGET key tokens last_time_ms` | 获取桶状态 |
| 更新状态 | `HMSET key tokens X last_time_ms Y` | 更新桶状态 |
| 设置过期 | `EXPIRE key seconds` | 防止死键 |

### 5.3 Lua 脚本

```lua
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate_per_sec = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])
local now_ms = tonumber(ARGV[4])

-- 获取状态
local state = redis.call('HMGET', key, 'tokens', 'last_time_ms')
local tokens = tonumber(state[1])
local last_time_ms = tonumber(state[2])

-- 初始化
if tokens == nil then
    tokens = capacity
    last_time_ms = now_ms
end

-- 按时间差填充令牌
local delta_ms = now_ms - last_time_ms
local fill = (delta_ms / 1000) * rate_per_sec
tokens = math.min(capacity, tokens + fill)

if tokens >= requested then
    tokens = tokens - requested
    redis.call('HMSET', key, 'tokens', tokens, 'last_time_ms', now_ms)
    redis.call('EXPIRE', key, 60)
    return {1, math.floor(tokens)}
else
    redis.call('HMSET', key, 'tokens', tokens, 'last_time_ms', now_ms)
    redis.call('EXPIRE', key, 60)
    return {0, math.floor(tokens)}
end
```

### 5.4 Key 设计

```
limiter:token:{key}
示例：limiter:token:user:12345, limiter:token:download:file1
```

### 5.5 数据结构图解

```
limiter:token:user:12345 (Hash)
┌────────────────────────────┐
│  Field        │ Value     │
├───────────────┼───────────┤
│  tokens       │ 8         │
│  last_time_ms │ 1715856000│
└────────────────────────────┘
```

### 5.6 算法流程

```
1. 获取当前时间戳 now_ms
2. HMGET 获取 tokens 和 last_time_ms
3. 计算 delta = now_ms - last_time_ms
4. 计算 fill = (delta / 1000) * rate_per_sec
5. tokens = min(capacity, tokens + fill)
6. 如果 tokens >= requested：
   - tokens -= requested
   - HMSET 保存状态
   - 返回允许
7. 否则返回拒绝
```

### 5.7 特点

- **优点**：支持突发流量，内存 O(1)
- **缺点**：令牌累积有上限，补充有时间延迟
- **适用**：带宽控制、下载限速、支付并发限制

---

## 6. 三种策略详细对比

### 6.1 核心指标对比

| 指标 | 固定窗口 | 滑动窗口 | 令牌桶 |
|------|---------|---------|--------|
| **数据结构** | String | Sorted Set | Hash |
| **内存复杂度** | O(1) | O(窗口内请求数) | O(1) |
| **时间精度** | 时间片对齐 | 毫秒级 | 取决于补充速率 |
| **突发支持** | ❌ | ❌ | ✅ |
| **边界穿透** | ✅ 有 | ❌ 无 | ❌ 无 |
| **实现复杂度** | 低 | 中 | 中 |

### 6.2 典型场景选择

| 场景 | 推荐策略 | 原因 |
|------|---------|------|
| API 限流（精确） | 滑动窗口 | 拒绝突发，精确控制 |
| 内部服务保护 | 固定窗口 | 简单高性能 |
| 下载限速 | 令牌桶 | 支持突发 |
| 支付并发限制 | 令牌桶 | 支持突发，平滑 |
| 防爬虫 | 滑动窗口 | 精确计数 |

### 6.3 Redis 命令统计

| 策略 | 读命令 | 写命令 | Lua 脚本 |
|------|-------|-------|---------|
| 固定窗口 | - | `INCR`, `EXPIRE` | ✅ 原子判断 |
| 滑动窗口 | `ZCARD` | `ZADD`, `ZREMRANGEBYSCORE`, `PEXPIRE` | ✅ 原子操作 |
| 令牌桶 | `HMGET` | `HMSET`, `EXPIRE` | ✅ 原子操作 |

---

## 7. 设计原则

### 7.1 原子性保证

- 复杂操作使用 Lua 脚本保证原子性
- 避免 Read-Then-Write 模式

### 7.2 内存管理

- 所有 key 设置过期时间，防止泄漏
- 滑动窗口使用 PEXPIRE（毫秒）精确控制

### 7.3 性能优化

- 固定窗口：O(1) 复杂度，最高性能
- 令牌桶：O(1) 内存，原子操作
- 滑动窗口：Lua 减少网络往返

---

## 8. 后续扩展

### 8.1 可重入锁

使用 Hash 结构：`lock:reentrant:{resource}` → `threadId:count`

### 8.2 Redlock

多实例协调，5 个 Redis 实例半数以上成功才算成功。

---

*文档版本：v0.4 | 更新日期：2026-05-16*