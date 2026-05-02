---
created: 2026-05-02
tags: [redis, counter, architecture, design]
---

# 计数器服务真实场景设计思考

## 问题澄清与纠正

### 问题1：缓存适用性 clarification

**我的错误表述**："热点数据 < 总数据 20% → 适合缓存"

**正确理解**：
- 缓存的价值 = 命中率 × 单次查询成本节省
- **高命中率**（>80%）才是缓存的前提
- 数据访问越**集中**（而非分散），缓存价值越高

```
场景A：1000万数据，Top 1% 数据承担 80% 访问 → 适合缓存 ✅
场景B：1000万数据，访问均匀分布 → 不适合缓存 ❌
```

### 问题2：多指标内存成本

**你的质疑完全正确**！

| 指标类型 | 单条内存 | 100万条 | 1000万条 |
|---------|---------|--------|---------|
| 阅读数 | 70 bytes | 70 MB | 700 MB |
| 点赞数 | 70 bytes | 70 MB | 700 MB |
| 收藏数 | 70 bytes | 70 MB | 700 MB |
| 分享数 | 70 bytes | 70 MB | 700 MB |
| **合计** | - | **280 MB** | **2.8 GB** |

**成本翻倍**：4个指标 = 4倍内存 = 4倍成本

### 问题3：数据热力分布规律

**承认局限**：我无法提供权威数据报告，但可以基于公开信息分析：

| 平台类型 | 热力特征 | 参考来源 |
|---------|---------|---------|
| 微博热搜 | 24小时生命周期 | 公开报道 |
| 知乎回答 | 长尾效应明显 | 公开数据 |
| B站视频 | 前3天占70%流量 | 创作者报告 |
| 电商商品 | 促销期突增 | 行业分析 |

**关键洞察**：不同业务有不同的热力曲线！

---

## 真实场景需求分类

### 需求维度矩阵

| 维度 | 类型A | 类型B | 类型C |
|------|-------|-------|-------|
| **访问模式** | 突发型（秒杀） | 稳定型（文章） | 波动型（直播） |
| **数据规模** | 小（<10万） | 中（10万-1000万） | 大（>1000万） |
| **一致性要求** | 强一致（库存） | 最终一致（阅读数） | 弱一致（点赞数） |
| **指标数量** | 单指标 | 3-5指标 | 10+指标 |
| **生命周期** | 短期（活动） | 中期（内容） | 长期（用户） |

### 典型业务场景

#### 场景1：内容平台（知乎/简书）
```
数据特征：
- 文章数：1000万+
- 日新增：1000篇
- 指标：阅读数、点赞数、收藏数、评论数
- 访问模式：新文章热，老文章冷（长尾）

热力分布：
- 24小时内：80%流量
- 7天内：15%流量
- 30天以上：5%流量
```

#### 场景2：电商平台（秒杀活动）
```
数据特征：
- 商品数：100万
- 活动商品：1000个
- 指标：库存、已售、预约数
- 访问模式：活动前预热，活动中突增，活动后归零

热力分布：
- 活动期间：99.9%流量集中在活动商品
- 非活动期：均匀分布
```

#### 场景3：直播平台（抖音/快手）
```
数据特征：
- 直播间：10万+（同时在线）
- 指标：在线人数、点赞数、礼物数、弹幕数
- 访问模式：实时变化，峰值波动大

热力分布：
- 大V直播：10%直播间占90%流量
- 普通直播：长尾分布
```

---

## 分层存储策略（修正版）

### 核心原则：按"访问密度"分层

```
访问密度 = 单位时间内的访问次数 / 数据总量

高密度 → Redis（内存）
中密度 → SSD 缓存（Redis on Flash / Pika）
低密度 → MySQL（磁盘）
```

### 动态分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                    动态分层存储架构                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  L1: 本地缓存 (Caffeine)                                    │
│      ├── 策略：固定容量 LRU                                 │
│      ├── 容量：10,000 条/实例                               │
│      └── 适用：当前秒级热点                                  │
│           ↓                                                 │
│  L2: Redis 热数据（动态调整）                                │
│      ├── 策略：按访问频率 + 时间窗口                         │
│      ├── 容量：可配置（100MB - 10GB）                        │
│      └── 适用：近 N 分钟活跃数据                             │
│           ↓                                                 │
│  L3: SSD 缓存（可选）                                        │
│      ├── 策略：Redis on Flash / Pika                        │
│      ├── 容量：100GB+                                       │
│      └── 适用：近 N 小时活跃数据                             │
│           ↓                                                 │
│  L4: MySQL 全量                                             │
│      ├── 策略：分库分表                                     │
│      └── 适用：全量历史数据                                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 动态升降级策略

```java
public class DynamicTierManager {
    
    // 升级：MySQL -> Redis
    boolean shouldPromote(String key, AccessStats stats) {
        return stats.getQpsLastMinute() > 100      // 1分钟QPS>100
            || stats.getTotalAccess() > 1000;      // 或累计访问>1000
    }
    
    // 降级：Redis -> MySQL
    boolean shouldDemote(String key, AccessStats stats) {
        return stats.getQpsLastHour() < 1          // 1小时QPS<1
            && stats.getLastAccessTime() < now() - 3600; // 且1小时无访问
    }
    
    // 定时任务执行升降级
    @Scheduled(fixedRate = 60000) // 每分钟检查
    public void rebalance() {
        // 1. 扫描 Redis，找出冷数据降级
        // 2. 扫描 MySQL，找出热数据升级
    }
}
```

---

## 多指标存储优化

### 问题：4个指标 = 4倍内存？

**优化方案1：Hash 聚合存储**

```
传统方案（4个key）：
- article:123:views → 70 bytes
- article:123:likes → 70 bytes
- article:123:favs  → 70 bytes
- article:123:shares → 70 bytes
总计：280 bytes

优化方案（1个hash）：
- article:123:counters (hash)
  - views → 9 bytes
  - likes → 9 bytes
  - favs → 9 bytes
  - shares → 9 bytes
总计：~100 bytes（节省 64%）
```

**优化方案2：冷热指标分离**

| 指标 | 访问频率 | 存储策略 |
|------|---------|---------|
| 阅读数 | 极高 | Redis String |
| 点赞数 | 高 | Redis Hash |
| 收藏数 | 中 | Redis Hash + 定期刷盘 |
| 分享数 | 低 | MySQL only |

### 成本对比

| 方案 | 100万文章4指标 | 1000万文章4指标 |
|------|---------------|----------------|
| 全String | 280 MB | 2.8 GB |
| 全Hash | 100 MB | 1 GB |
| 冷热分离 | 50 MB | 500 MB |

---

## 真实场景解决方案

### 场景1：内容平台（知乎模式）

```java
@Service
public class ContentCounterService {
    
    // 阅读数：高频，单独String
    public Long incrViews(Long articleId) {
        String key = "article:" + articleId + ":views";
        return redisTemplate.opsForValue().increment(key);
    }
    
    // 其他指标：低频，聚合Hash
    public Long incrLikes(Long articleId) {
        String key = "article:" + articleId + ":counters";
        return redisTemplate.opsForHash().increment(key, "likes", 1);
    }
    
    // 冷数据自动降级
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点
    public void archiveColdData() {
        // 1. 找出7天无访问的文章
        // 2. 将Redis数据刷入MySQL
        // 3. 删除Redis key
    }
}
```

### 场景2：秒杀活动（突发模式）

```java
@Service
public class SeckillCounterService {
    
    // 活动开始前：预热到Redis
    @EventListener
    public void onActivityStart(SeckillStartEvent event) {
        // 将活动商品库存加载到Redis
        List<Sku> skus = skuService.getActivitySkus(event.getActivityId());
        for (Sku sku : skus) {
            String key = "stock:" + sku.getId();
            redisTemplate.opsForValue().set(key, sku.getStock());
            // 设置过期时间：活动结束后自动清理
            redisTemplate.expire(key, event.getDuration(), TimeUnit.HOURS);
        }
    }
    
    // 库存扣减：Lua脚本保证原子性
    public boolean decrementStock(Long skuId, Integer quantity) {
        String key = "stock:" + skuId;
        // 执行Lua脚本...
    }
    
    // 活动结束后：数据归档
    @EventListener
    public void onActivityEnd(SeckillEndEvent event) {
        // 将剩余库存同步回MySQL
        // 删除Redis key（或等待自动过期）
    }
}
```

### 场景3：直播（实时波动模式）

```java
@Service
public class LiveCounterService {
    
    // 在线人数：高频更新，低精度要求
    public void updateOnlineCount(Long roomId, Integer count) {
        String key = "live:" + roomId + ":online";
        // 使用SETEX，5秒过期（允许短暂不一致）
        redisTemplate.opsForValue().set(key, count, 5, TimeUnit.SECONDS);
    }
    
    // 点赞数：超高频，批量聚合
    public void batchIncrLikes(Long roomId, Integer delta) {
        String key = "live:" + roomId + ":likes";
        // 本地聚合后批量写入
        localBuffer.add(new LikeEvent(roomId, delta));
    }
    
    // 礼物数：低频但重要，实时同步
    public void recordGift(Long roomId, Long userId, Integer amount) {
        // 直接写入Redis + 同步MySQL
        String key = "live:" + roomId + ":gifts";
        redisTemplate.opsForValue().increment(key, amount);
        mysqlRepository.save(new GiftRecord(roomId, userId, amount));
    }
}
```

---

## 监控与调优

### 关键指标

| 指标 | 阈值 | 告警 |
|------|------|------|
| Redis 内存使用率 | > 80% | 扩容或调整策略 |
| 缓存命中率 | < 90% | 检查策略是否合理 |
| MySQL QPS | > 5000 | 增加缓存层 |
| 数据一致性延迟 | > 5分钟 | 检查同步机制 |

### 动态调整策略

```java
@Component
public class CounterStrategyOptimizer {
    
    @Autowired
    private MetricsCollector metrics;
    
    @Scheduled(fixedRate = 300000) // 每5分钟
    public void optimize() {
        double hitRate = metrics.getCacheHitRate();
        double memoryUsage = metrics.getRedisMemoryUsage();
        
        if (hitRate < 0.85) {
            // 命中率低，增加热数据容量
            hotDataConfig.increaseCapacity(1.2);
        }
        
        if (memoryUsage > 0.8) {
            // 内存不足，缩短冷数据过期时间
            expirationConfig.reduceTTL(0.8);
        }
    }
}
```

---

## 总结：设计原则

1. **没有银弹**：不同场景需要不同策略
2. **数据驱动**：基于实际访问模式设计，而非假设
3. **动态调整**：策略应该可配置、可监控、可自动优化
4. **成本控制**：内存是昂贵的，只存储真正需要的数据
5. **渐进优化**：从简单方案开始，根据监控数据逐步优化

---

*最后更新：2026-05-02*
