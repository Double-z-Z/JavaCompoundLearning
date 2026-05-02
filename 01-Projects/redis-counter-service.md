---
created: 2026-05-02
updated: 2026-05-02
tags: [project, redis, counter, lua, high-concurrency]
status: planning
---

# 高并发计数器服务

> 项目目标：实现一个支撑百万 QPS 的分布式计数器服务，支持文章阅读数、视频播放量、商品库存等场景
> 项目类型：学习验证型 + 性能对比型


## 涉及知识点

| 知识点 | 在项目中的角色 | 相关练习 | 当前掌握度 |
|-------|--------------|---------|-----------|
| [[Redis-String]] | 核心数据结构：INCR/DECR 原子操作 | [[练习记录-Phase1]] | 🌱 初识 |
| [[Redis-Lua脚本]] | 批量操作原子化：库存扣减 | [[练习记录-Phase2]] | 🌱 初识 |
| [[Redis-Pipeline]] | 性能优化：批量命令打包 | [[练习记录-Phase2]] | 🌱 初识 |
| [[Redis-持久化]] | 数据可靠性：AOF + RDB | - | 🍎 70 |
| [[Redis-过期策略]] | 自动清理：TTL 设置 | [[练习记录-Phase1]] | 🌱 初识 |
| [[线程池]] | 异步处理：批量写入线程 | - | 🌿 55 |
| [[CompletableFuture]] | 异步 API：非阻塞计数 | - | 🌿 55 |


## 架构设计

### 项目结构
```
redis-counter-service/
├── pom.xml                          # Maven 配置
├── src/
│   ├── main/
│   │   ├── java/com/example/counter/
│   │   │   ├── CounterService.java          # 计数器核心服务
│   │   │   ├── CounterServiceImpl.java      # 实现类
│   │   │   ├── LuaScriptManager.java        # Lua 脚本管理
│   │   │   ├── BatchPersistenceManager.java # 批量持久化管理
│   │   │   ├── CounterController.java       # REST API 控制器
│   │   │   ├── config/
│   │   │   │   ├── RedisConfig.java         # Redis 配置
│   │   │   │   └── CounterConfig.java       # 服务配置
│   │   │   └── model/
│   │   │       ├── CounterType.java         # 计数器类型枚举
│   │   │       ├── CounterValue.java        # 计数器值对象
│   │   │       └── StockResult.java         # 库存操作结果
│   │   └── resources/
│   │       ├── lua/
│   │       │   ├── decrement_stock.lua      # 库存扣减脚本
│   │       │   ├── batch_incr.lua           # 批量自增脚本
│   │       │   └── get_and_reset.lua        # 获取并重置脚本
│   │       └── application.yml
│   └── test/
│       └── java/com/example/counter/
│           ├── CounterServiceTest.java       # 功能测试
│           └── CounterBenchmark.java         # 性能压测
└── README.md
```

### 关键设计决策

#### 决策1：分层架构设计
```
┌─────────────────────────────────────────┐
│  CounterController (REST API)           │
│  - POST /counter/{key}/incr             │
│  - GET  /counter/{key}                  │
│  - POST /stock/{sku}/decrement          │
├─────────────────────────────────────────┤
│  CounterService (业务逻辑)               │
│  - 计数器类型判断                        │
│  - 缓存策略选择                          │
│  - 批量写入调度                          │
├─────────────────────────────────────────┤
│  Redis Client (数据访问)                 │
│  - String 操作                           │
│  - Lua 脚本执行                          │
│  - Pipeline 批量操作                     │
└─────────────────────────────────────────┘
```

#### 决策2：计数器类型与存储策略
| 计数器类型 | 存储策略 | 过期时间 | 持久化方式 |
|-----------|---------|---------|-----------|
| 阅读数 (views) | String + 定时刷盘 | 7天 | 异步批量写入 DB |
| 点赞数 (likes) | String + 实时写 | 永久 | 同步写入 DB |
| 库存 (stock) | String + Lua 脚本 | 永久 | 实时同步 |
| 临时计数 (temp) | String | 1小时 | 不持久化 |

#### 决策3：批量持久化策略
```
┌─────────────────────────────────────────┐
│  计数请求 → Redis INCR                   │
│       ↓                                 │
│  写入 Buffer (内存队列)                   │
│       ↓                                 │
│  批量写入线程 (每 100ms / 1000 条)        │
│       ↓                                 │
│  批量 INSERT/UPDATE MySQL               │
└─────────────────────────────────────────┘
```

### 组件交互流程

#### 阅读数自增流程
```
Client
  ↓ POST /counter/article:123/views/incr
CounterController
  ↓
CounterService.incr(CounterType.VIEWS, "article:123")
  ↓
Redis: INCR counter:views:article:123
  ↓
添加到批量写入队列
  ↓
返回当前值

[异步] 批量写入线程
  ↓
读取队列数据
  ↓
MySQL: UPDATE article SET views = ? WHERE id = 123
```

#### 库存扣减流程（Lua 脚本保证原子性）
```
Client
  ↓ POST /stock/SKU123/decrement?quantity=2
CounterController
  ↓
CounterService.decrementStock("SKU123", 2)
  ↓
Redis Lua Script:
  1. GET stock:SKU123
  2. 检查是否 >= 2
  3. 如果足够: DECRBY 2, 返回新值
  4. 如果不足: 返回 -1
  ↓
返回结果 (成功/失败)
  ↓
如果成功: 同步写入 MySQL
```


## 实现阶段

### Phase 1: 基础计数器实现
**目标**: 
- 实现基于 Redis String 的计数器基本功能
- 支持 INCR/DECR/GET/SET 操作
- 实现简单的阅读数统计功能

**核心代码**:
```java
// 基础计数器接口
public interface CounterService {
    Long incr(String key);
    Long incrBy(String key, long delta);
    Long decr(String key);
    Long get(String key);
    void set(String key, long value);
}
```

**验证方式**: 
```bash
# 1. 启动服务
mvn spring-boot:run

# 2. 测试自增
curl -X POST http://localhost:8080/counter/article:123/views/incr
# 返回: 1

curl -X POST http://localhost:8080/counter/article:123/views/incr
# 返回: 2

# 3. 测试获取
curl http://localhost:8080/counter/article:123/views
# 返回: 2
```

**关联练习**: [[2026-05-02-counter-service-phase1]]（待创建）

**可能遇到的问题**: 
- ⚠️ 并发下 Redis 连接池耗尽（参考 [[MISTAKE-002-NIO-RaceCondition-Register]] 的资源管理教训）
- ⚠️ Key 命名不规范导致冲突

**当前状态**: ⏳ 待开始

---

### Phase 2: Lua 脚本与库存扣减
**目标**: 
- 实现 Lua 脚本管理器
- 完成库存扣减功能（原子性保证）
- 实现批量操作（Pipeline）

**核心代码**:
```lua
-- decrement_stock.lua
local key = KEYS[1]
local decrement = tonumber(ARGV[1])
local current = tonumber(redis.call('GET', key) or 0)

if current >= decrement then
    local newValue = redis.call('DECRBY', key, decrement)
    return newValue
else
    return -1  -- 库存不足
end
```

**验证方式**: 
```bash
# 1. 设置初始库存
curl -X POST http://localhost:8080/stock/SKU123/init?quantity=100

# 2. 扣减库存
curl -X POST http://localhost:8080/stock/SKU123/decrement?quantity=2
# 返回: 98 (成功)

curl -X POST http://localhost:8080/stock/SKU123/decrement?quantity=100
# 返回: -1 (失败，库存不足)

# 3. 并发测试（使用 JMeter 或 wrk）
wrk -t4 -c100 -d30s http://localhost:8080/stock/SKU123/decrement?quantity=1
```

**关联练习**: [[2026-05-02-counter-service-phase2]]（待创建）

**可能遇到的问题**: 
- ⚠️ Lua 脚本语法错误（使用 redis-cli 预先测试）
- ⚠️ 库存超卖（确保 Lua 脚本的原子性）

**当前状态**: ⏳ 待开始

---

### Phase 3: 批量持久化与性能优化
**目标**: 
- 实现批量写入管理器
- 异步持久化到 MySQL
- 性能压测与优化

**核心代码**:
```java
@Component
public class BatchPersistenceManager {
    private final BlockingQueue<CounterUpdate> buffer = 
        new LinkedBlockingQueue<>(10000);
    
    @Scheduled(fixedRate = 100)  // 每 100ms 执行一次
    public void flush() {
        List<CounterUpdate> batch = new ArrayList<>();
        buffer.drainTo(batch, 1000);  // 最多取 1000 条
        
        if (!batch.isEmpty()) {
            batchInsertToMySQL(batch);  // 批量写入
        }
    }
}
```

**验证方式**: 
```bash
# 1. 压测准备
# 准备 100 个 article key

# 2. 执行压测
wrk -t4 -c100 -d60s -s counter_test.lua http://localhost:8080

# 3. 验证 Redis 和 MySQL 数据一致性
# Redis: 获取当前值
curl http://localhost:8080/counter/article:1/views

# MySQL: 查询数据库
SELECT * FROM article_views WHERE article_id = 1;

# 4. 检查批量写入性能
# 日志输出: "Batch insert 1000 records in 15ms"
```

**性能目标**:
| 指标 | 目标值 | 说明 |
|------|--------|------|
| QPS | 100,000+ | 单 Redis 节点 |
| 延迟 (P99) | < 5ms | 自增操作 |
| 批量写入吞吐量 | 10,000+ 条/秒 | 到 MySQL |

**关联练习**: [[2026-05-02-counter-service-phase3]]（待创建）

**可能遇到的问题**: 
- ⚠️ 批量写入线程阻塞（使用独立线程池）
- ⚠️ Redis 内存不足（设置合理的过期策略）
- ⚠️ MySQL 写入瓶颈（使用批量 INSERT）

**当前状态**: ⏳ 待开始


## 性能测试与对比

### 测试环境
- **CPU**: 8 vCPU
- **内存**: 16GB
- **Redis**: 单节点（后续可扩展为 Cluster）
- **MySQL**: 8.0
- **网络**: 内网 < 1ms 延迟

### 对比方案

| 方案 | 吞吐量 (QPS) | 延迟(P99) | 数据一致性 | 适用场景 |
|-----|-------------|-----------|-----------|---------|
| 纯 MySQL | 2,000 | 50ms | 强一致 | 低并发、强一致 |
| Redis + 实时写 MySQL | 10,000 | 10ms | 最终一致 | 中等并发 |
| **Redis + 批量写 MySQL** | **100,000+** | **3ms** | **最终一致** | **高并发** |
| Redis Cluster | 300,000+ | 5ms | 最终一致 | 超高并发 |

### 关键发现
<!-- 测试后记录 -->
- 


## 项目特有的坑与解决方案

### 问题1: 并发下库存超卖
**现象**: 高并发时库存扣减为负数
**根因**: 先读后写存在竞态条件
**解决**: 使用 Lua 脚本保证原子性
**预防**: 所有库存操作必须通过 Lua 脚本

### 问题2: 批量写入数据丢失
**现象**: Redis 重启后部分计数未同步到 MySQL
**根因**: 批量写入队列中的数据在重启时丢失
**解决**: 
1. 使用 Redis List 作为持久化队列
2. 或者使用 AOF 持久化 Redis 数据
**预防**: 关键计数（如库存）实时同步，非关键计数批量同步

### 问题3: Key 冲突
**现象**: 不同业务计数器互相覆盖
**根因**: Key 命名不规范
**解决**: 统一 Key 命名规范：`counter:{type}:{id}:{metric}`
**预防**: 在 CounterType 枚举中定义所有类型


## 跨概念综合洞察

### 概念间的协同效应

- [[Redis-String]] + [[Redis-Lua脚本]]:
  - 单独使用 String 只能做简单自增
  - 结合 Lua 可以实现复杂的原子操作（如库存检查+扣减）
  - 这让我想起了 [[NIO-Buffer]] 的 flip/clear，都是原子状态管理

- [[Redis-Pipeline]] + [[线程池]]:
  - Pipeline 减少网络 RTT
  - 线程池管理批量写入线程
  - 两者结合实现高吞吐量的异步处理

- [[CompletableFuture]] + 批量写入:
  - 异步 API 提升响应速度
  - 批量写入保证数据最终一致性
  - 类似 [[Netty]] 的异步事件驱动模型

### 与单一概念理解的差异

- **理论上**: Redis INCR 是 O(1) 操作，性能无限
- **实践上**: 网络 IO、连接池、序列化都会成为瓶颈
- **启示**: 需要使用 Pipeline、连接池优化才能达到理论性能


## 复盘总结

### 架构层面的收获
<!-- 项目完成后总结 -->
1. 

### 待深入的方向
<!-- 项目暴露出的知识盲区 -->
- [[Redis-Cluster模式]] 在计数器服务中的应用
- [[Redis-Stream]] 用于计数变更日志
- [[布隆过滤器]] 用于防止重复计数


## 相关链接
- 项目规划: [[redis-scenarios-overview]]
- 主题地图: [[MOC-Redis]]（待创建）
- 错误记录: 
  - [[MISTAKE-007-Redis-Connection-Pool-Exhausted]]（待创建）
  - [[MISTAKE-008-Redis-Key-Naming-Conflict]]（待创建）


---
📊 **项目完成度**: 0%（规划阶段）
🎯 **核心收获**: 待完成
🔗 **关联练习数**: 0（待创建 Phase 1/2/3）
📈 **涉及知识点掌握度提升**: 
- Redis-String: 0 → ?
- Redis-Lua脚本: 0 → ?
- Redis-Pipeline: 0 → ?
