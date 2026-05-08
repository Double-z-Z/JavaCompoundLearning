# 设计思路

## 需求场景

### 库存秒杀

```
用户请求 → 库存扣减 → 成功/失败
```

核心问题：
- **原子性**：库存不能超卖
- **高性能**：高并发秒杀场景
- **一致性**：Redis Cluster 不支持跨节点事务

### 多 SKU 购物车下单

```
用户下单 → 批量扣减多个商品库存
```

问题：
1. **跨节点批量操作** - Redis Cluster 不支持跨节点 MULTI/EXEC
2. **数据倾斜** - 热点商品集中在同一节点

---

## 技术方案

### 单 SKU 扣减：Lua 脚本原子操作

```lua
local stock = tonumber(redis.call('GET', KEYS[1]) or 0)
local quantity = tonumber(ARGV[1])
if stock >= quantity then
    return redis.call('DECRBY', KEYS[1], quantity)
else
    return -1
end
```

- 检查库存 + 扣减在 Lua 脚本里原子完成
- 不会超卖
- 单节点操作，Redis Cluster 自动路由

### 多 SKU 下单：Saga 补偿模式

```
Step 1: 并行执行 Lua 脚本（每个 SKU 检查+扣减）
  ├─ SKU001 → Node1 → Lua → 返回 90（成功）
  ├─ SKU002 → Node2 → Lua → 返回 -1（失败，库存不足）
  └─ SKU003 → Node3 → Lua → 返回 87（成功）

Step 2: 失败补偿（仅当部分成功时）
  ├─ SKU001 (已扣减) → INCRBY +10 回滚
  └─ SKU003 (已扣减) → INCRBY +13 回滚
```

- 每个 SKU 的检查+扣减是原子的（Lua 脚本保证）
- 跨 SKU 不是原子的，需要在应用层补偿
- 最终一致性，非强一致性

### 热点商品：本地缓存（Caffeine）

```
请求 → Nginx (轮询) → 多个应用实例
                          ↓
                    Caffeine (本地 LRU)
                          ↓ (缓存未命中)
                    Redis Cluster
```

- 热点商品被缓存到每个应用实例
- 显著减少 Redis 请求
- 扣减后失效缓存，保证一致性

---

## 技术栈

| 组件 | 技术 |
|------|------|
| 框架 | Spring WebFlux（响应式） |
| 容器 | Netty（嵌入式） |
| 客户端 | Lettuce（响应式） |
| 缓存 | Redis Cluster（6 节点） |
| 本地缓存 | Caffeine |
| 脚本 | Lua（原子操作） |

---

## 架构演进

```
Phase 1: 单 SKU 原子扣减
  └─ Lua 脚本保证检查+扣减原子性

Phase 2: 多 SKU Saga 补偿
  └─ 跨节点批量操作 + 失败回滚

Phase 3: 热点商品本地缓存
  └─ Caffeine 吸收热点请求
```