---
created: 2026-05-02
updated: 2026-05-02
tags: [project, redis, ansible, devops, cluster]
status: completed
---

# Ansible Redis Cluster 部署项目

> 项目目标：使用 Ansible 自动化部署 Redis 6 节点集群（3 Master + 3 Slave），实践 Redis Cluster 模式的高可用架构
> 项目类型：综合应用型（DevOps + 分布式系统）
> **状态：✅ 已完成**


## 涉及知识点

| 知识点 | 在项目中的角色 | 相关练习 | 掌握度变化 |
|-------|--------------|---------|-----------|
| [[Redis-Cluster模式]] | 核心架构：数据分片、Gossip 协议、故障转移 | [[2026-05-02-ansible-redis-cluster-phase3]] | 🌿 65 → 🍎 85 |
| [[Ansible]] | 自动化部署工具：Playbook、Role、Inventory | [[2026-05-02-ansible-redis-cluster-phase1]] | 🌿 40 → 🍎 70 |
| [[PVE虚拟化]] | 基础设施：创建 6 台虚拟机作为集群节点 | [[2026-05-02-ansible-redis-cluster-phase1]] | 🌱 30 → 🌿 50 |
| [[Redis-主从复制]] | 集群基础：理解复制在故障转移中的作用 | [[2026-05-02-ansible-redis-cluster-phase3]] | 🌿 60 → 🍎 70 |
| [[Redis-持久化]] | 数据安全：AOF 配置实践 | [[2026-05-02-ansible-redis-cluster-phase2]] | 🌿 65 → 🍎 70 |
| [[Redis-哨兵模式]] | 对比学习：Cluster vs Sentinel 故障转移 | [[2026-05-02-ansible-redis-cluster-phase3]] | 🌿 65 → 🌿 70 |


## 架构设计

### 项目结构
```
ansible-redis-cluster/
├── ansible.cfg              # Ansible 配置文件
├── site.yml                 # 主 Playbook：部署 Redis
├── bootstrap.yml            # 初始化 Playbook：SSH 密钥配置
├── check-cluster.yml        # 集群状态检查 Playbook
├── vm-redis-cluster.sh      # PVE 虚拟机创建脚本
├── inventory/
│   └── hosts.ini            # 主机清单（3 Master + 3 Slave）
├── group_vars/
│   └── all.yml              # 全局变量（版本、端口、超时）
└── roles/
    └── redis/
        ├── handlers/
        │   └── main.yml     # 重启 Redis 处理器
        ├── tasks/
        │   └── main.yml     # 安装、配置、启动任务
        └── templates/
            └── redis.conf.j2 # Redis 配置文件模板
```

### 关键设计决策

- **决策1**：使用 3 Master + 3 Slave 架构
  - 原因：Redis Cluster 要求至少 3 个 Master 才能形成多数派（quorum=2）
  - 每个 Master 挂 1 个 Slave，保证高可用
  
- **决策2**：使用 Ansible Role 组织代码
  - 原因：职责分离，tasks/handlers/templates 各自独立，便于复用
  
- **决策3**：Jinja2 模板化 redis.conf
  - 原因：不同节点需要不同的 `bind` 地址（`{{ ansible_host }}`），模板化避免硬编码

- **决策4**：分阶段部署（Bootstrap → Deploy → Check）
  - 原因：SSH 密钥配置只需一次，部署可重复执行，检查独立进行

### 组件交互流程

```
┌─────────────────────────────────────────────────────────────┐
│                     Ansible Control Node                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │ bootstrap.yml│→ │  site.yml    │→ │check-cluster │       │
│  │  (SSH密钥)    │  │ (部署Redis)   │  │  (状态检查)   │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
└─────────────────────────────────────────────────────────────┘
         ↓                    ↓                    ↓
    ┌────────────────────────────────────────────────────────┐
    │              Redis Cluster (6 Nodes)                    │
    │  ┌─────────┐  ┌─────────┐  ┌─────────┐                │
    │  │Master-1 │  │Master-2 │  │Master-3 │  (Slots 0-16383)│
    │  │10.0.0.102│  │10.0.0.103│  │10.0.0.104│                │
    │  └────┬────┘  └────┬────┘  └────┬────┘                │
    │       ↓            ↓            ↓                      │
    │  ┌─────────┐  ┌─────────┐  ┌─────────┐                │
    │  │Slave-1  │  │Slave-2  │  │Slave-3  │  (复制)         │
    │  │10.0.0.105│  │10.0.0.106│  │10.0.0.107│                │
    │  └─────────┘  └─────────┘  └─────────┘                │
    │                                                        │
    │  ◄────── Gossip 协议（PING/PONG）──────►               │
    └────────────────────────────────────────────────────────┘
```


## 实现阶段

### Phase 1: 基础环境准备 ✅
**目标**: 
- 在 PVE 上创建 6 台虚拟机（Ubuntu 22.04）
- 配置 SSH 免密登录（bootstrap.yml）
- 验证 Ansible 连通性

**验证方式**: 
```bash
# 测试连通性
ansible all -m ping

# 检查主机变量
ansible redis_cluster -m setup | grep ansible_host
```

**关联练习**: [[2026-05-02-ansible-redis-cluster-phase1]]

**实际完成**: 
- ✅ 6 台 VM 创建成功（2vCPU/2GB/20GB）
- ✅ SSH 免密配置完成
- ✅ Ansible 连通性测试通过

**遇到的问题**: 无

---

### Phase 2: Redis 安装与配置 ✅
**目标**: 
- 编写完整的 redis.conf.j2 模板
- 实现 Redis 服务安装、配置、启动
- 理解 cluster-enabled 参数的作用

**验证方式**: 
```bash
# 部署 Redis
ansible-playbook site.yml

# 检查 Redis 状态
ansible redis_cluster -m shell -a "systemctl status redis-server"

# 检查集群模式是否启用
ansible redis_cluster -m shell -a "redis-cli INFO cluster"
```

**关联练习**: [[2026-05-02-ansible-redis-cluster-phase2]]

**实际完成**: 
- ✅ redis.conf.j2 模板完善（bind 127.0.0.1 + ansible_host）
- ✅ Ansible Role 实现（tasks/handlers/templates）
- ✅ 所有节点 Redis 服务正常启动
- ✅ 集群模式已启用（cluster_enabled:1）

**遇到的问题**: 
- `bind` 配置如果只写 `{{ ansible_host }}`，本地 redis-cli 无法连接
- 解决：改为 `bind 127.0.0.1 {{ ansible_host }}`

---

### Phase 3: 集群初始化与故障转移测试 ✅
**目标**: 
- 使用 `redis-cli --cluster create` 初始化集群
- 验证数据分片（slots 分配）
- 测试故障转移（手动停止一个 Master）

**验证方式**: 
```bash
# 创建集群
redis-cli --cluster create \
  10.0.0.102:6379 10.0.0.103:6379 10.0.0.104:6379 \
  10.0.0.105:6379 10.0.0.106:6379 10.0.0.107:6379 \
  --cluster-replicas 1

# 检查集群状态
redis-cli CLUSTER INFO
redis-cli CLUSTER NODES

# 测试数据分片
redis-cli -c SET key1 value1  # -c 启用集群模式
redis-cli -c GET key1
```

**关联练习**: [[2026-05-02-ansible-redis-cluster-phase3]]

**实际完成**: 
- ✅ 6 节点集群初始化成功
- ✅ slots 均匀分配（0-5460, 5461-10922, 10923-16383）
- ✅ 故障转移测试成功（102 停止 → 106 提升为 Master）
- ✅ 服务恢复测试成功（102 重启后以 Slave 身份加入）

**故障转移观察**：
```
初始状态：102(Master) ← 106(Slave)
停止 102 后：106 提升为 Master，接管 slots 0-5460
重启 102 后：102 变成 106 的 Slave（角色互换）
```

**遇到的问题**: 无


## 性能测试与对比

### 测试环境
- **虚拟化平台**: Proxmox VE (PVE)
- **VM 配置**: 2 vCPU / 2GB RAM / 20GB Disk
- **OS**: Ubuntu 22.04 LTS
- **Redis 版本**: 7.0
- **网络**: 10.0.0.0/24 内网

### 对比方案

| 方案 | 吞吐量 | 延迟(P99) | 可用性 | 适用场景 |
|-----|-------|----------|--------|---------|
| 单机 Redis | 最高 | 最低 | 单点故障 | 开发测试 |
| Redis 主从 | 高 | 低 | 手动切换 | 读多写少 |
| Redis Sentinel | 高 | 中 | 自动故障转移 | 高可用需求 |
| **Redis Cluster** | 线性扩展 | 中 | 分区容错 | **大规模数据** |

### 关键发现
<!-- 测试后记录 -->
- 


## 项目特有的坑与解决方案

### 问题1: `cluster-enabled yes` 后 Redis 无法启动
**现象**: Redis 服务启动失败，日志显示 `Can't open cluster config file`
**根因**: `cluster-config-file` 指定的路径没有写权限
**解决**: 确保 `dir /var/lib/redis` 存在且 redis 用户可写
**预防**: 在 tasks 中添加目录创建和权限设置

### 问题2: 节点间 Gossip 通信失败
**现象**: `CLUSTER NODES` 显示所有节点都是 `handshake` 状态
**根因**: 防火墙阻止了 16379 端口（集群总线端口 = 数据端口 + 10000）
**解决**: 开放 6379 和 16379 端口
**预防**: 在 Ansible 中添加 ufw/firewalld 配置任务

### 问题3: 客户端连接报错 `MOVED`
**现象**: 客户端执行命令时收到 `MOVED 12345 10.0.0.103:6379`
**根因**: 客户端没有启用集群模式，不知道 key 被重定向到哪个节点
**解决**: 使用 `redis-cli -c` 启用集群模式，或在客户端代码中处理 MOVED/ASK 重定向
**预防**: 文档中明确说明客户端连接方式


## 跨概念综合洞察

### 概念间的协同效应

- [[Redis-Cluster模式]] + [[Ansible]]: 
  - 理论上学到的 Gossip 协议、slots 分配，通过 Ansible 自动化部署变成可操作的实践
  - 理解 "为什么 Cluster 需要 3 个 Master" → 在 Inventory 中配置 3+ 节点

- [[Redis-主从复制]] + [[Redis-Cluster模式]]:
  - Cluster 内部使用主从复制实现数据冗余
  - 理解复制缓冲区有助于排查同步延迟问题

- [[PVE虚拟化]] + [[Ansible]]:
  - PVE 提供基础设施（VM），Ansible 提供配置管理
  - 基础设施即代码（IaC）的完整实践

### 与单一概念理解的差异

- **理论上**: Redis Cluster 是"无中心"架构，所有节点平等
- **实践上**: 初始化时必须指定一个节点执行 `redis-cli --cluster create`，这是"临时中心"
- **启示**: "无中心"是指运行时状态，不是指部署过程


## 复盘总结

### 架构层面的收获
<!-- 项目完成后总结 -->
1. 

### 待深入的方向
<!-- 项目暴露出的知识盲区 -->
- [[Redis-性能调优]] 在生产环境的最佳实践
- [[Linux-网络调优]] 高并发场景下的内核参数
- [[监控与告警]] Redis Cluster 的监控指标设计


## 相关链接
- 主题地图: [[MOC-Redis]]（待创建）
- 项目路径: `01-Projects/ansible-redis-cluster/`
- 错误记录: 
  - [[MISTAKE-005-Ansible-SSH-Permission]]（待创建）
  - [[MISTAKE-006-Redis-Cluster-Firewall]]（待创建）
- 学习路线图: [[LEARNING-ROADMAP-分布式系统]]


---
📊 **项目完成度**: 100% ✅
🎯 **核心收获**: 
1. 掌握了 Ansible Role 的完整开发流程（tasks/handlers/templates）
2. 理解了 Redis Cluster 故障转移的实际过程（Slave 提升、角色互换）
3. 实践了从基础设施（PVE）到应用部署（Redis）的完整 DevOps 流程
4. 验证了 Gossip 协议在故障检测和选举中的作用

🔗 **关联练习数**: 3
- [[2026-05-02-ansible-redis-cluster-phase1]] - 基础环境准备
- [[2026-05-02-ansible-redis-cluster-phase2]] - Redis 安装与配置
- [[2026-05-02-ansible-redis-cluster-phase3]] - 集群初始化与故障转移测试

📈 **涉及知识点掌握度提升**: 
- [[Ansible]]: 40 → 70 (+30) 🍎应用
- [[Redis-Cluster模式]]: 65 → 85 (+20) 🍎应用
- [[PVE虚拟化]]: 30 → 50 (+20) 🌿理解
- [[Redis-主从复制]]: 60 → 70 (+10) 🍎应用
- [[Redis-持久化]]: 65 → 70 (+5) 🍎应用
- [[Redis-哨兵模式]]: 65 → 70 (+5) 🌿理解

**总学习时长**: 约 2-3 小时
**项目状态**: 集群运行正常，故障转移已验证
