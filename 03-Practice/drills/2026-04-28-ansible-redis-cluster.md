---
created: 2026-04-28
tags: [drill, redis, ansible, cluster, devops]
difficulty: 🌿
related_concepts:
  - [[RESP协议]]
  - [[Redis集群]]
  - [[Ansible]]
  - [[PVE虚拟化]]
  - [[Cloud-Init]]
---

# Ansible 部署 Redis 集群（完整版）

> 🎯 目标：使用 Ansible 在 6 个 Ubuntu VM 上部署 Redis Cluster（3 Master + 3 Slave），并深入学习 RESP 协议和集群原理

---

## 练习内容

### 题目/需求
1. 在 PVE 上创建 6 个 Ubuntu Server VM（10.0.0.102-107）
2. 使用 Ansible 自动化部署 Redis
3. 初始化 Redis Cluster
4. 验证集群状态
5. 深入学习 RESP 协议和 Redis 集群原理

---

## 阶段一：PVE 环境准备（04-27）

### 1. VM 创建脚本

```bash
# vm-redis-cluster.sh
# 关键配置：q35 + VirtIO SCSI

qm create $VMID \
    --machine q35 \
    --scsihw virtio-scsi-single \
    --ciuser redis \
    --cipassword redis123456 \
    --sshkeys <(echo "$SSH_PUBLIC_KEY") \
    --ipconfig0 ip=${IP}/24,gw=10.0.0.1
```

**遇到的问题与解决**：
- **VM启动卡住**：使用 LSI SCSI 控制器时，Ubuntu Cloud Image 启动卡住
  - ✅ 解决：改用 VirtIO SCSI 控制器
  
- **Console无输出**：Cloud Image 默认输出到 serial console
  - ✅ 解决：添加 serial 设备或改用 VGA 输出

---

## 阶段二：Ansible 自动化部署（04-27）

### 2. 项目结构

```
ansible-redis-cluster/
├── ansible.cfg
├── inventory/hosts.ini      # 6节点分组
├── group_vars/all.yml
├── site.yml
├── roles/redis/
│   ├── tasks/main.yml
│   ├── handlers/main.yml
│   └── templates/redis.conf.j2
└── vm-redis-cluster.sh
```

### 3. 核心配置

**inventory/hosts.ini**：
```ini
[redis_masters]
10.0.0.102
10.0.0.103
10.0.0.104

[redis_slaves]
10.0.0.105
10.0.0.106
10.0.0.107
```

**roles/redis/templates/redis.conf.j2**：
```jinja2
bind 127.0.0.1 {{ ansible_host }}
port {{ redis_port }}
cluster-enabled yes
cluster-config-file nodes-{{ redis_port }}.conf
cluster-node-timeout 5000
appendonly yes
daemonize no              # 关键：与 systemd 配合
supervised systemd        # 关键：systemd 管理
dir /var/lib/redis
```

**遇到的问题与解决**：
- **SSH 密钥认证问题**
  - ✅ 解决：使用 cloud-init 预配置 SSH 公钥

- **Redis 服务启动失败**
  - 原因：`daemonize yes` 与 `supervised systemd` 冲突
  - ✅ 解决：改为 `daemonize no`

---

## 阶段三：集群初始化与验证（04-28）

### 4. 集群初始化

```bash
redis-cli --cluster create \
  10.0.0.102:6379 10.0.0.103:6379 10.0.0.104:6379 \
  10.0.0.105:6379 10.0.0.106:6379 10.0.0.107:6379 \
  --cluster-replicas 1
```

### 5. 验证结果

```
# cluster nodes 输出
f3382cceb... 10.0.0.102:6379 master - 0-5460
9cf199c97... 10.0.0.103:6379 master - 5461-10922
b85d900c7... 10.0.0.104:6379 master - 10923-16383
13cb65b05... 10.0.0.106:6379 slave f3382cceb...
943a49472... 10.0.0.107:6379 slave 9cf199c97...
a99ba63a1... 10.0.0.105:6379 slave b85d900c7...
```

✅ **3 Master + 3 Slave，16384 槽位全部分配**

**遇到的问题与解决**：
- **MOVED 重定向不理解**
  - 现象：`redis-cli get key1` 返回 `MOVED 9189 10.0.0.103:6379`
  - ✅ 解决：学习哈希槽机制，使用 `redis-cli -c` 自动处理重定向

---

## 阶段四：RESP 协议深入学习（04-28）

### 6. RESP 协议核心理解

**设计哲学**：
- 极简优先：5种数据类型，统一格式
- 命令即类型：命令名隐含数据结构
- 预制结构：协议层不表达语义，命令层约定解析

**数据类型标记**：

| 前缀 | 类型 | 示例 |
|------|------|------|
| `+` | 简单字符串 | `+OK
` |
| `-` | 错误 | `-ERR unknown command
` |
| `:` | 整数 | `:100
` |
| `$` | 批量字符串 | `$5
hello
` |
| `*` | 数组 | `*2
$3
GET
$3
key
` |

**为什么用长度前缀？**
```
RESP: $5
hello
      ↑ 直接读5字节，O(1)

JSON: "hello"
      ↑ 需要遍历找结束符，O(n)
```

**关键洞察**：
- RESP 是"命令即类型系统"
- 协议不负责区分，命令负责约定，客户端负责解析
- 与 JSON 相比：更紧凑、解析更快、实现更简单

---

## 复盘总结

### 学到的

#### 1. DevOps 实践
- **Ansible**：Inventory、Playbook、Role、Template、Handler
- **PVE 虚拟化**：Cloud Image、VirtIO、cloud-init、批量管理
- **幂等性设计**：Ansible 通过状态检查实现幂等

#### 2. Redis 核心原理
- **RESP 协议**：简单即高效，命令即类型
- **Redis 集群**：哈希槽分片（16384 槽）、MOVED 重定向、Gossip 协议
- **故障转移**：Slave 发现 Master 失联 → 发起选举 → 投票 → 提升为 Master

#### 3. 系统设计思想
- **Zero-copy / 预解析**：Redis 多处使用预制结构提升性能
- **取舍艺术**：简单 > 灵活，够用 > 最优

### 关联知识
- [[RESP协议]] - 应用场景：理解 Redis 通信机制，掌握协议设计取舍
- [[Redis集群]] - 应用场景：分布式存储设计，理解分片和故障转移
- [[Ansible]] - 应用场景：自动化运维实践，基础设施即代码
- [[PVE虚拟化]] - 应用场景：私有云环境搭建，VM 生命周期管理
- [[systemd]] - 应用场景：Linux 服务管理，进程守护
- [[NIO-Selector]] - 联系：Ansible 并行管理多主机，类似 Selector 管理多 Channel

### 待深化
- [ ] 故障转移的实际测试（模拟 Master 宕机）
- [ ] Gossip 协议的深入理解（节点间状态同步）
- [ ] Ansible 更复杂的 playbook 编写（条件、循环、变量）
- [ ] 对比其他配置管理工具（Puppet、Chef、SaltStack）

---

## 🤖 AI评价

### 完成质量
- 功能实现：✅ 完整（部署 + 初始化 + 学习）
- 代码质量：良好
- 概念应用：深入（不仅会用，还理解原理）

### 对掌握度的影响
- [[RESP协议]]: +20分 (深入理解协议设计原理，能对比分析 RESP vs JSON)
- [[Redis集群]]: +20分 (成功部署并理解架构，掌握哈希槽和故障转移)
- [[Ansible]]: +15分 (完成完整部署，理解核心概念和幂等性)
- [[PVE虚拟化]]: +10分 (解决实际问题，掌握 Cloud Image 和 VirtIO)

### 建议
1. 完成故障转移测试，验证高可用性
2. 整理 Ansible 最佳实践笔记（Role 设计模式、变量优先级）
3. 学习 Redis 性能调优（内存策略、持久化配置）
4. 探索 Redis 在其他场景的应用（缓存、消息队列、限流）

---

```dataview
TABLE difficulty, created, related_concepts
FROM #drill AND (#redis OR #devops)
SORT created DESC
```
