---
created: 2026-05-02
updated: 2026-05-02
tags: [redis, scenarios, roadmap, projects]
status: planning
---

# Redis 经典应用场景与实战项目规划

> 基于 Redis 核心特性设计的 5 个代表性实战项目，覆盖缓存、计数器、消息队列、分布式锁、实时排行榜等经典场景


## Redis 核心特性速览

| 特性 | 数据结构 | 经典应用场景 |
|------|---------|-------------|
| **高速读写** | String | 缓存、Session、配置 |
| **原子操作** | String(INCR/DECR) | 计数器、限流、ID生成 |
| **过期机制** | 所有类型 | 缓存失效、临时数据 |
| **发布订阅** | Pub/Sub | 消息广播、实时通知 |
| **列表操作** | List | 消息队列、时间线 |
| **集合运算** | Set | 标签、共同好友、抽奖 |
| **有序集合** | Sorted Set | 排行榜、延迟队列、地理位置 |
| **哈希存储** | Hash | 对象缓存、购物车 |
| **Bitmap** | Bitmaps | 签到、在线状态、布隆过滤器 |
| **HyperLogLog** | HyperLogLog | UV统计、基数估算 |
| **Geo** | Geo | 附近的人、位置服务 |
| **Stream** | Stream | 消息队列（Kafka风格） |


## 5 个实战项目全景图

```
┌─────────────────────────────────────────────────────────────────┐
│                    Redis 实战项目路线图                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  项目1: 高并发计数器服务                                          │
│  ├── 场景: 点赞、阅读数、库存扣减                                  │
│  ├── 核心: String + Lua脚本 + 持久化                              │
│  └── 难度: 🌿🌿                                                  │
│       ↓                                                         │
│  项目2: 分布式锁与限流系统                                        │
│  ├── 场景: 秒杀、防重复提交、API限流                               │
│  ├── 核心: SET NX EX + Redisson + 令牌桶                          │
│  └── 难度: 🌿🌿🌿                                                │
│       ↓                                                         │
│  项目3: 实时排行榜系统                                            │
│  ├── 场景: 游戏排行榜、热搜榜、直播间热度                          │
│  ├── 核心: Sorted Set + 时间窗口 + 聚合计算                        │
│  └── 难度: 🌿🌿🌿                                                │
│       ↓                                                         │
│  项目4: 消息队列与异步处理                                        │
│  ├── 场景: 订单处理、通知推送、任务调度                            │
│  ├── 核心: List/Stream + 消费者组 +  ACK机制                      │
│  └── 难度: 🍎🍎                                                  │
│       ↓                                                         │
│  项目5: 社交网络关系系统                                          │
│  ├── 场景: 关注/粉丝、共同好友、Feed流                             │
│  ├── 核心: Set + 交集并集 + 时间线聚合                             │
│  └── 难度: 🍎🍎🍎                                                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```


## 项目1: 高并发计数器服务

### 场景描述
实现一个支撑百万 QPS 的计数器服务，用于文章阅读数、视频播放量、商品库存等场景。

### 核心挑战
- 高并发下的原子性（避免竞态条件）
- 数据持久化（Redis 重启不丢失）
- 批量写入优化（减少数据库压力）

### Redis 技术应用
| 技术点 | 应用方式 | 解决的问题 |
|--------|---------|-----------|
| String INCR | `INCR article:123:views` | 原子自增，无竞态条件 |
| Lua 脚本 | 批量操作原子化 | 库存扣减的读写一致性 |
| 过期策略 | `EXPIRE key 86400` | 自动清理过期计数 |
| 持久化 | AOF + RDB | 数据不丢失 |
| Pipeline | 批量命令打包 | 减少网络 RTT |

### 项目结构
```
counter-service/
├── src/
│   ├── CounterService.java      # 计数器核心服务
│   ├── LuaScripts.java          # Lua脚本管理
│   ├── BatchWriter.java         # 批量持久化
│   └── CounterController.java   # REST API
├── lua/
│   ├── decrement_stock.lua      # 库存扣减脚本
│   └── batch_incr.lua           # 批量自增脚本
└── test/
    └── CounterBenchmark.java    # 性能压测
```

### 学习价值
- 深入理解 Redis 原子操作
- 掌握 Lua 脚本编写
- 学习高并发计数器设计模式


## 项目2: 分布式锁与限流系统

### 场景描述
构建一个生产级分布式锁服务，支持秒杀场景、防重复提交、API 接口限流。

### 核心挑战
- 锁的可靠性（避免死锁、误删）
- 可重入性（同一线程多次获取锁）
- 限流算法的准确性（令牌桶 vs 漏桶）

### Redis 技术应用
| 技术点 | 应用方式 | 解决的问题 |
|--------|---------|-----------|
| SET NX EX | `SET lock:order:123 nx ex 30` | 原子加锁+过期 |
| Lua 解锁 | 校验 UUID 后删除 | 防止误删他人锁 |
| Hash | `HSET lock:order:123 threadId 1 count 2` | 可重入锁实现 |
| 看门狗 | 后台线程续期 | 业务未完成时锁不释放 |
| ZSet | `ZADD rate_limit:api 时间戳 请求ID` | 滑动窗口限流 |

### 项目结构
```
lock-limiter-service/
├── src/
│   ├── DistributedLock.java     # 分布式锁接口
│   ├── RedisLock.java           # Redis 锁实现
│   ├── RateLimiter.java         # 限流器接口
│   ├── TokenBucketLimiter.java  # 令牌桶实现
│   ├── SlidingWindowLimiter.java # 滑动窗口实现
│   └── Watchdog.java            # 看门狗续期
├── lua/
│   ├── acquire_lock.lua         # 加锁脚本
│   ├── release_lock.lua         # 解锁脚本
│   └── renew_lock.lua           # 续期脚本
└── test/
    └── LockConcurrencyTest.java # 并发测试
```

### 学习价值
- 理解分布式锁的 Redlock 算法
- 掌握限流算法的实现原理
- 学习 Redisson 的设计思想


## 项目3: 实时排行榜系统

### 场景描述
构建支持百万用户同时在线的实时排行榜，支持日榜、周榜、月榜，以及多维度排序。

### 核心挑战
- 实时性（分数变化立即反映）
- 多维度排序（分数相同按时间）
- 大数据量分页查询性能
- 时间窗口自动切换

### Redis 技术应用
| 技术点 | 应用方式 | 解决的问题 |
|--------|---------|-----------|
| Sorted Set | `ZADD leaderboard:daily score userId` | 有序存储+排名 |
| 复合分数 | `score = 实际分数 * 1000000000 + (9999999999 - timestamp)` | 分数相同按时间排序 |
| ZREVRANGE | `ZREVRANGE leaderboard:daily 0 99 WITHSCORES` | 高效分页查询 |
| ZRANK | `ZRANK leaderboard:daily userId` | 查询个人排名 |
| 过期策略 | 日榜 2 天过期、周榜 8 天过期 | 自动清理历史数据 |
| Bitmap | 记录用户今日是否已计分 | 去重判断 |

### 项目结构
```
leaderboard-service/
├── src/
│   ├── LeaderboardService.java      # 排行榜核心服务
│   ├── ScoreCalculator.java         # 分数计算器
│   ├── RankQueryService.java        # 排名查询服务
│   ├── TimeWindowManager.java       # 时间窗口管理
│   └── LeaderboardController.java   # REST API
├── model/
│   ├── RankEntry.java               # 排名条目
│   └── LeaderboardType.java         # 榜单类型枚举
└── test/
    └── LeaderboardBenchmark.java    # 性能压测
```

### 学习价值
- 掌握 Sorted Set 高级用法
- 理解复合分数设计技巧
- 学习大数据量分页优化


## 项目4: 消息队列与异步处理

### 场景描述
基于 Redis 实现一个轻量级消息队列，支持延迟消息、消息重试、消费确认，用于订单超时取消、异步通知等场景。

### 核心挑战
- 消息可靠性（不丢失、不重复）
- 延迟消息精确触发
- 消费失败重试机制
- 消息顺序性保证

### Redis 技术应用
| 技术点 | 应用方式 | 解决的问题 |
|--------|---------|-----------|
| List | `LPUSH queue:order msg` / `BRPOP queue:order 0` | 简单队列 |
| Stream | `XADD stream:order * field value` | Kafka风格消息流 |
| Consumer Group | `XGROUP CREATE stream:order group1 $` | 消费者组 |
| Pending List | `XPENDING stream:order group1` | 处理中消息监控 |
| Sorted Set | `ZADD delay:queue 时间戳 msg` | 延迟队列 |
| 定时任务 | 每秒扫描 ZSet 到期消息 | 延迟触发 |

### 项目结构
```
message-queue-service/
├── src/
│   ├── MessageQueue.java            # 队列接口
│   ├── RedisListQueue.java          # List实现
│   ├── RedisStreamQueue.java        # Stream实现
│   ├── DelayQueue.java              # 延迟队列
│   ├── ConsumerGroup.java           # 消费者组管理
│   ├── MessageHandler.java          # 消息处理器
│   └── RetryManager.java            # 重试管理器
├── model/
│   ├── Message.java                 # 消息实体
│   └── MessageStatus.java           # 消息状态枚举
└── test/
    └── MessageReliabilityTest.java  # 可靠性测试
```

### 学习价值
- 理解 List vs Stream 的适用场景
- 掌握延迟队列实现原理
- 学习消息队列的可靠性设计


## 项目5: 社交网络关系系统

### 场景描述
构建一个 Twitter/微博风格的社交网络系统，支持关注/粉丝、共同好友、Feed流推送。

### 核心挑战
- 关系数据的高效存储（关注关系是 N:N）
- 共同好友快速计算
- Feed流的实时推送 vs 拉取
- 大 V 用户的粉丝列表性能

### Redis 技术应用
| 技术点 | 应用方式 | 解决的问题 |
|--------|---------|-----------|
| Set | `SADD following:userA userB userC` | 关注列表存储 |
| Set | `SADD followers:userB userA` | 粉丝列表存储 |
| SINTER | `SINTER following:userA following:userB` | 共同关注计算 |
| SUNION | `SUNION following:userA following:userB` | 并集计算 |
| List | `LPUSH feed:userA tweetId` | Feed流存储 |
| 推拉结合 | 大V推+普通用户拉 | 性能优化 |

### 项目结构
```
social-network-service/
├── src/
│   ├── RelationService.java         # 关系服务
│   ├── FeedService.java             # Feed流服务
│   ├── CommonFriendService.java     # 共同好友服务
│   ├── FollowController.java        # 关注API
│   └── FeedController.java          # Feed流API
├── model/
│   ├── UserRelation.java            # 用户关系
│   ├── Tweet.java                   # 推文
│   └── FeedEntry.java               # Feed条目
└── test/
    └── SocialGraphBenchmark.java    # 社交图谱性能测试
```

### 学习价值
- 掌握 Set 的交集并集运算
- 理解推拉结合的 Feed流设计
- 学习社交图谱的存储优化


## 学习路径建议

```
推荐学习顺序（由浅入深）：

计数器服务 ──► 分布式锁 ──► 排行榜 ──► 消息队列 ──► 社交网络
   🌿🌿           🌿🌿🌿          🌿🌿🌿          🍎🍎            🍎🍎🍎
   
原因：
1. 计数器：熟悉 Redis 基础操作和 Lua 脚本
2. 分布式锁：理解分布式系统的复杂性
3. 排行榜：掌握 Sorted Set 高级用法
4. 消息队列：学习 Stream 和可靠性设计
5. 社交网络：综合运用所有数据结构
```


## 与已掌握知识的关联

| 已掌握知识 | 在新项目中的应用 |
|-----------|----------------|
| [[Redis-Cluster模式]] | 所有项目都支持 Cluster 部署 |
| [[Redis-主从复制]] | 读写分离提升性能 |
| [[Redis-持久化]] | 数据可靠性保障 |
| [[Ansible]] | 项目部署自动化 |
| [[Netty]] | 高性能网络层（可选） |
| [[CompletableFuture]] | 异步处理 |
| [[线程池]] | 消费者线程管理 |


## 下一步行动

1. **选择第一个项目开始**（推荐：计数器服务）
2. **创建项目笔记**（使用项目拆解模板）
3. **逐步实现 Phase 1/2/3**
4. **完成后选择下一个项目**

---

📊 **项目规划完成度**: 100%
🎯 **覆盖 Redis 特性**: String, Hash, List, Set, Sorted Set, Lua, Stream, Bitmap
🔗 **关联已掌握知识**: Redis Cluster, 主从复制, 持久化, Ansible, Netty
📈 **预计掌握度提升**: 
- Redis 综合应用: 70 → 90
- 分布式系统设计: 50 → 75
