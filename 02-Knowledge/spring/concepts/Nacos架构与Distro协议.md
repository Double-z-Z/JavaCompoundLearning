---
type: atomic-note
id: CONCEPT-Nacos架构与Distro协议
created: 2026-05-16
updated: 2026-05-16
tags: [spring-cloud, nacos, distributed-system]
status: 🌿
mastery: 50
related_emrg: [EMRG-SpringCloud微服务]
related_goal: [GOAL-SpringCloud微服务]
---

# Nacos 架构与 Distro 协议

## 一句话定义
Nacos 采用双协议架构：Raft 保证配置管理的强一致性，Distro 保证服务发现的最终一致性与高可用性。

## 核心理解

### 为什么需要两种协议？

| 协议 | 用途 | 一致性模型 | 类比 |
|------|------|-----------|------|
| **Raft** | 配置管理（Config Service） | 强一致（CP） | Redis 主从同步 |
| **Distro** | 服务发现（Naming Service） | 最终一致（AP） | Redis Cluster 异步复制 |

**设计哲学**：
- 配置数据（如数据库密码）：错了就完蛋 → 必须强一致
- 服务实例列表：晚几秒被发现 → 影响不大（Sentinel 兜底）

### Distro 协议核心设计

**关键特征**：
- 每个节点存储全量数据（不像 Redis Cluster 的 slot 分片）
- 写入任意节点 → 异步复制到其他节点
- 读取任意节点 → 可能读到旧数据

```
┌─────────────────────────────────────┐
│            Nacos 集群               │
│                                     │
│   ┌─────────┐ ┌─────────┐ ┌──────┐ │
│   │ Node 1  │ │ Node 2  │ │Node 3│ │
│   │ (全量)  │ │ (全量)  │ │(全量)│ │
│   └────┬────┘ └────┬────┘ └──┬───┘ │
│        └───────────┼────────┘     │
│              异步双向复制          │
└─────────────────────────────────────┘
```

### Distro vs Redis Cluster 对比

| 维度 | Distro | Redis Cluster |
|------|--------|---------------|
| 数据分布 | 每个节点存全部数据 | 每个 node 只存部分 slot |
| 写入 | 写入任意节点，异步复制 | 写入对应 slot 的节点 |
| 读取 | 读任意节点，可能读到旧数据 | 可能被 MOVE 重定向 |

### 优缺点分析

**优点**：
- ✅ 读取快（无需重定向）
- ✅ 容错性强（任一节点可响应）
- ✅ 简化客户端逻辑

**风险/缺点**：
- ⚠️ 内存占用大（每个节点存全量）
- ⚠️ 数据一致性延迟（异步复制）
- ⚠️ 复制消耗大（N 个节点 = N-1 次复制）

### CAP 取舍

Nacos 在服务发现场景选择 **AP（可用性 + 分区容错）**：

> **理由**：
> - 读多写少（查询 >> 注册/注销）
> - 允许短暂不一致（几秒内某实例不可见）
> - 可用性 > 一致性（注册中心挂了比数据稍旧更严重）

## 关键关联

- [[Redis-Cluster模式]] - 关联原因：Distro 与 Cluster 都是分布式存储方案，但设计取舍不同——Distro 选 AP，Cluster 选 CP。
- [[Redis-主从复制]] - 关联原因：Raft 协议的 Leader/Follower 机制类似 Redis 主从复制的强一致性保证。
- [[Sentinel-核心机制]] - 关联原因：当注册中心返回的服务列表有延迟时，Sentinel 作为客户端侧熔断器提供兜底保护。
- [[服务注册与发现]] - 关联原因：Distro 是 Nacos 实现服务发现的核心协议。

## 我的误区与疑问

- ❓ **疑问**：Distro 如何处理网络分区（Split-Brain）？两个分区都认为自己是最新的怎么办？
- ❓ **疑问**：Nacos 的健康检查是客户端心跳还是服务端主动探测？
- 💡 **待验证**：实际生产环境中 Distro 的数据一致性延迟通常在多少毫秒级？

## 代码与实践

```yaml
# Nacos 集群配置示例
spring:
  cloud:
    nacos:
      discovery:
        server-addr: nacos1:8848,nacos2:8848,nacos3:8848
        cluster-name: DEFAULT
```

## 深入思考
💡 如果要自己实现一个简易版的 Distro 协议，需要解决哪些核心问题？（提示：时钟同步、冲突解决、故障恢复）

## 来源
- 对话：[[2026-05-16-SpringCloud服务注册与发现对话]]

---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-05-16: mastery=50 (理解了双协议设计和 AP 取舍)

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #nacos
SORT mastery DESC
```
