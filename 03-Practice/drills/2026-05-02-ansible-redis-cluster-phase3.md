---
created: 2026-05-02
tags: [drill, redis, cluster, ha]
difficulty: 🍎
related_concepts:
  - [[Redis-Cluster模式]]
  - [[Redis-主从复制]]
  - [[Redis-哨兵模式]]
---

# Ansible Redis Cluster - Phase 3: 集群初始化与故障转移测试

> 🎯 目标：初始化 Redis Cluster，验证数据分片，测试故障转移机制


## 练习内容

### 题目/需求
1. 使用 `redis-cli --cluster create` 初始化 6 节点集群
2. 验证 slots 分配和数据分片
3. 测试故障转移：停止一个 Master，观察 Slave 提升

### 我的实现

#### 1. 集群初始化

```bash
redis-cli --cluster create \
  10.0.0.102:6379 10.0.0.103:6379 10.0.0.104:6379 \
  10.0.0.105:6379 10.0.0.106:6379 10.0.0.107:6379 \
  --cluster-replicas 1
```

**输出确认**：
- 3 个 Master 节点分配 slots：0-5460, 5461-10922, 10923-16383
- 3 个 Slave 节点分别挂载到对应 Master

#### 2. 验证集群状态

```bash
# 查看集群信息
$ redis-cli CLUSTER INFO
cluster_state:ok
cluster_slots_assigned:16384
cluster_slots_ok:16384
cluster_known_nodes:6
cluster_size:3

# 查看节点拓扑
$ redis-cli CLUSTER NODES
9cf199c97ba7f8af4db50fb952e3978fb348edd1 10.0.0.103:6379@16379 master - 0 1777700022797 2 connected 5461-10922
b85d900c72be578a8b42c63488ca9db82546e9c7 10.0.0.104:6379@16379 master - 0 1777700022598 3 connected 10923-16383
13cb65b051a4f66516f430a3d2ad1dfe083f2852 10.0.0.106:6379@16379 master - 0 1777700021795 7 connected 0-5460
...
```

#### 3. 数据分片测试

```bash
# 使用 -c 参数启用集群模式（自动处理 MOVED 重定向）
$ redis-cli -c SET key1 value1
OK
$ redis-cli -c GET key1
"value1"

# 查看 key 所在的 slot
$ redis-cli CLUSTER KEYSLOT key1
(integer) 9189
```

#### 4. 故障转移测试

**步骤**：
1. 停止 Master-1 (10.0.0.102) 的 Redis 服务
2. 观察集群状态变化
3. 验证 Slave 是否提升为 Master

```bash
# 停止 102 的 Redis
$ ssh 10.0.0.102 "sudo systemctl stop redis-server"

# 观察集群状态（30 秒后）
$ redis-cli CLUSTER NODES
f3382cceb2d7afcd9c37cfbbcc533888db4d2d70 10.0.0.102:6379@16379 master,fail - 1777700000000 1777700000000 1 disconnected
13cb65b051a4f66516f430a3d2ad1dfe083f2852 10.0.0.106:6379@16379 master - 0 1777700021795 7 connected 0-5460
...
```

**观察结果**：
- 102 标记为 `fail`
- 106 从 Slave 提升为 Master，接管 slots 0-5460

#### 5. 恢复测试

```bash
# 重启 102 的 Redis
$ ssh 10.0.0.102 "sudo systemctl start redis-server"

# 观察状态
$ redis-cli CLUSTER NODES
f3382cceb2d7afcd9c37cfbbcc533888db4d2d70 10.0.0.102:6379@16379 myself,slave 13cb65b051a4f66516f430a3d2ad1dfe083f2852 0 17777000 connected
```

**观察结果**：
- 102 重新加入集群
- 102 变成了 106 的 Slave（角色互换）


### 遇到的困难
- 无显著困难，故障转移按预期工作
- 对 `CLUSTER NODES` 输出格式需要适应


## 验证与测试

### 测试方案
1. 集群初始化验证
2. 数据读写测试（验证分片）
3. 故障转移测试（停止 Master）
4. 恢复测试（重启旧 Master）

### 测试结果

| 测试项 | 结果 | 说明 |
|--------|------|------|
| 集群初始化 | ✅ 通过 | 6 节点成功加入集群 |
| slots 分配 | ✅ 均匀 | 0-5460, 5461-10922, 10923-16383 |
| 数据分片 | ✅ 正常 | MOVED 重定向自动处理 |
| 故障转移 | ✅ 成功 | Slave 在 node-timeout 后提升为 Master |
| 服务恢复 | ✅ 正常 | 旧 Master 以 Slave 身份重新加入 |


## 复盘总结

### 学到的
- `redis-cli --cluster create` 命令的使用
- Redis Cluster 的故障转移机制（基于 Gossip 协议 + 选举）
- `CLUSTER NODES` 输出格式解读
- 故障转移后角色互换的现象

### 关联知识
- [[Redis-Cluster模式]] - Gossip 协议、故障转移流程、slots 分配
- [[Redis-主从复制]] - 复制关系在故障转移中的作用
- [[Redis-哨兵模式]] - 对比 Cluster 和 Sentinel 的故障转移机制差异

### 关键洞察

**故障转移流程**：
```
1. Master-1 (102) 停止
   ↓
2. 其他 Master 通过 Gossip 发现 102 失联
   ↓
3. 超过 cluster-node-timeout (5000ms)
   ↓
4. Slave-1 (106) 发起选举
   ↓
5. 获得多数派投票 → 提升为 Master
   ↓
6. 接管 slots 0-5460
   ↓
7. 102 重启后作为 Slave 重新加入
```

**与 Sentinel 的对比**：
- Cluster：去中心化，节点间直接通过 Gossip 通信
- Sentinel：中心化，需要独立的 Sentinel 进程监控


---

## 🤖 AI评价

### 完成质量
- 功能实现：✅ 完整
- 故障测试：✅ 全面
- 概念应用：✅ 正确

### 对掌握度的影响
- [[Redis-Cluster模式]]: +20分 (完成故障转移实践，深度理解)
- [[Redis-主从复制]]: +10分 (理解复制在故障转移中的作用)
- [[Redis-哨兵模式]]: +5分 (对比理解两种高可用方案)

### 建议
1. 尝试手动触发 failover：`CLUSTER FAILOVER` 命令
2. 测试网络分区场景（使用 iptables 模拟）
3. 探索 `redis-cli --cluster reshard` 进行数据迁移

---

```dataview
TABLE difficulty, created, related_concepts
FROM #drill AND #redis
SORT created DESC
```
