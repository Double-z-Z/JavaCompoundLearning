---
created: 2026-05-01
tags: [redis, 哨兵, 高可用, raft]
status: 🌿
mastery: 65
---

# Redis 哨兵模式（Sentinel）

## 一句话定义
哨兵是 Redis 的高可用解决方案，通过监控主从节点、自动故障发现和故障转移，实现主节点宕机时的自动切换。


## 核心理解

### 哨兵的核心功能

```
┌─────────────────────────────────────────┐
│           Redis Sentinel 集群           │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐ │
│  │ Sentinel│  │ Sentinel│  │ Sentinel│ │  至少3个，奇数个
│  │   S1    │  │   S2    │  │   S3    │ │  投票决策
│  └────┬────┘  └────┬────┘  └────┬────┘ │
│       └─────────────┴─────────────┘    │
│              监控 + 决策                │
└─────────────────────────────────────────┘
              │
    ┌─────────┴─────────┐
    ▼                   ▼
┌─────────┐         ┌─────────┐
│  Master │◄────────│  Slave  │
│  M1     │         │  S1     │
└─────────┘         └─────────┘
```

| 功能 | 说明 |
|------|------|
| **监控** | 持续检查主从节点健康状态 |
| **通知** | 故障时通过 API 通知管理员 |
| **自动故障转移** | Master 宕机时，自动提升 Slave 为新 Master |
| **配置提供者** | 客户端向 Sentinel 获取当前 Master 地址 |

### 故障转移流程

```
Step 1: 主观下线（SDOWN）
Sentinel 检测到 Master 无响应（超过 down-after-milliseconds）
       ↓
Step 2: 客观下线（ODOWN）
多数 Sentinel 同意（达到 quorum），确认 Master 真正下线
       ↓
Step 3: 选举 Leader Sentinel
Sentinel 之间使用 Raft 算法选举 Leader，负责执行故障转移
       ↓
Step 4: 选择新 Master & 故障转移
按优先级、复制偏移量、RunID 选择最优 Slave 提升
       ↓
Step 5: 通知客户端
客户端向 Sentinel 查询新 Master 地址
```

### Raft 选举算法

```
核心机制：
├── 随机超时（Randomized Timeout）
│   └── 降低多个节点同时成为 Candidate 的概率
├── Term（任期）机制
│   └── 标识"第几届选举"，防止旧消息干扰
├── 先到先得（First-Come-First-Served）
│   └── 每个 Term 只能投一票
└── 多数派原则（Majority）
    └── 必须 > n/2 票才能当选

关键设计：
├── 延迟触发：复制偏移量越大的 Slave 延迟越短
├── 发起选举：向所有 Master 发送 FAILOVER_AUTH_REQUEST
└── Master 投票：每个 Master 在一个 epoch 只能投一票
```

### 部署架构

```
推荐部署（3节点）：
┌─────────────────────────────────────────┐
│           可用区 A（同机房）              │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐ │
│  │ Server1 │  │ Server2 │  │ Server3 │ │
│  │ Master  │  │ Slave   │  │ Slave   │ │
│  │ Sentinel│  │ Sentinel│  │ Sentinel│ │
│  └─────────┘  └─────────┘  └─────────┘ │
└─────────────────────────────────────────┘

配置：
├── 1 Master + 2 Slave
├── 3 Sentinel（奇数，避免平票）
├── 网络延迟 < 1ms
└── 成本：3 台机器
```


## 关键关联

- [[Redis-主从复制]] - 关联原因：哨兵建立在主从复制之上，提供自动故障转移能力
- [[Raft-共识算法]] - 关联原因：哨兵之间使用 Raft 算法选举 Leader，保证一致性
- [[CAP-理论]] - 关联原因：哨兵模式在 CAP 中选择 CP（一致性 + 分区容错）
- [[NIO-Selector]] - 关联原因：哨兵监控使用异步网络通信，类似 NIO 的事件驱动模型


## 我的误区与疑问

- ❌ 误区：以为哨兵是中心化的（实际是去中心化的 Raft 选举）
- ❓ 疑问：为什么不用 Slave 自治选举，而要引入哨兵？（视角独立性、避免脑裂）
- ❓ 疑问：偶数哨兵会平票吗？（会，推荐奇数个）


## 代码与实践

```bash
# sentinel.conf

# 监控主节点
sentinel monitor mymaster 192.168.1.1 6379 2
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 60000
sentinel parallel-syncs mymaster 1

# 查看哨兵状态
redis-cli -p 26379 sentinel master mymaster

# 手动故障转移
redis-cli -p 26379 sentinel failover mymaster
```


## 深入思考

💡 哨兵模式和 Cluster 模式的故障转移有什么本质区别？
💡 为什么 Raft 需要随机超时，而不是固定超时？
💡 在网络分区场景下，哨兵如何保证不脑裂？


## 来源

- 项目：[[netty-chatroom]]（后续 Redis 高可用改造）
- 对话：[[2026-05-01-Redis持久化与Cluster对话]]


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-05-01: mastery=65 (深入理解 Raft 选举算法，掌握哨兵故障转移流程)

### 建议下一步
1. 实践部署 3 节点哨兵集群
2. 模拟 Master 故障，观察自动切换
3. 学习 Redis Cluster 的去中心化设计

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #redis
SORT mastery DESC
```
