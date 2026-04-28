---
created: 2026-04-28
tags: [drill, redis, ansible, cluster]
difficulty: 🌿
related_concepts:
  - [[RESP协议]]
  - [[Redis集群]]
  - [[Ansible]]
  - [[PVE虚拟化]]
---

# Ansible 部署 Redis 集群

> 🎯 目标：使用 Ansible 在 6 个 Ubuntu VM 上部署 Redis Cluster（3 Master + 3 Slave）

---

## 练习内容

### 题目/需求
1. 在 PVE 上创建 6 个 Ubuntu Server VM
2. 配置网络（10.0.0.102-107）
3. 使用 Ansible 自动化部署 Redis
4. 初始化 Redis Cluster
5. 验证集群状态

### 我的实现

#### 1. PVE VM 创建脚本

```bash
# vm-redis-cluster.sh
# 使用 q35 + VirtIO SCSI
qm create $VMID \
    --machine q35 \
    --scsihw virtio-scsi-single \
    --ciuser redis \
    --cipassword redis123456 \
    --sshkeys <(echo "$SSH_PUBLIC_KEY")
```

#### 2. Ansible 项目结构

```
ansible-redis-cluster/
├── ansible.cfg
├── inventory/hosts.ini
├── group_vars/all.yml
├── site.yml
├── roles/redis/
│   ├── tasks/main.yml
│   ├── handlers/main.yml
│   └── templates/redis.conf.j2
└── vm-redis-cluster.sh
```

#### 3. Redis 配置模板

```jinja2
# redis.conf.j2
bind 127.0.0.1 {{ ansible_host }}
port {{ redis_port }}
cluster-enabled yes
cluster-config-file nodes-{{ redis_port }}.conf
cluster-node-timeout 5000
appendonly yes
daemonize no
supervised systemd
dir /var/lib/redis
```

#### 4. 集群初始化

```bash
redis-cli --cluster create \
  10.0.0.102:6379 10.0.0.103:6379 10.0.0.104:6379 \
  10.0.0.105:6379 10.0.0.106:6379 10.0.0.107:6379 \
  --cluster-replicas 1
```

### 遇到的困难

1. **SSH 密钥认证问题**
   - 解决：使用 cloud-init 预配置 SSH 公钥

2. **Redis 服务启动失败**
   - 原因：`daemonize yes` 与 `supervised systemd` 冲突
   - 解决：改为 `daemonize no`

3. **MOVED 重定向不理解**
   - 解决：学习哈希槽机制，使用 `redis-cli -c` 自动处理

---

## 验证与测试

### 测试方案
1. 检查所有节点状态：`redis-cli cluster nodes`
2. 测试数据写入：`redis-cli -c set key1 value1`
3. 验证槽位分配：确认 16384 个槽全部分配

### 测试结果
```
# cluster nodes 输出
f3382cceb... 10.0.0.102:6379 master - 0-5460
9cf199c97... 10.0.0.103:6379 master - 5461-10922
b85d900c7... 10.0.0.104:6379 master - 10923-16383
13cb65b05... 10.0.0.106:6379 slave f3382cceb...
943a49472... 10.0.0.107:6379 slave 9cf199c97...
a99ba63a1... 10.0.0.105:6379 slave b85d900c7...
```

✅ 3 Master + 3 Slave，所有槽位已分配

---

## 复盘总结

### 学到的
1. **RESP 协议设计**：简单即高效，命令即类型
2. **Redis 集群原理**：哈希槽分片、MOVED 重定向、Gossip 协议
3. **Ansible 实践**：Role 组织、模板渲染、Handler 触发
4. **PVE 虚拟化**：cloud-init、VirtIO SCSI、批量管理

### 关联知识
- [[RESP协议]] - 应用场景：理解 Redis 通信机制
- [[Redis集群]] - 应用场景：分布式存储设计
- [[Ansible]] - 应用场景：自动化运维实践
- [[systemd]] - 应用场景：服务管理配置

### 待深化
- 故障转移的实际测试
- Gossip 协议的深入理解
- Ansible 更复杂的 playbook 编写

---

## 🤖 AI评价

### 完成质量
- 功能实现：✅ 完整
- 代码质量：良好
- 概念应用：正确

### 对掌握度的影响
- [[RESP协议]]: +15分 (正确理解协议设计原理)
- [[Redis集群]]: +15分 (成功部署并理解架构)
- [[Ansible]]: +10分 (掌握基本 Role 结构)

### 建议
1. 完成故障转移测试，加深对高可用性的理解
2. 整理 Ansible 最佳实践笔记
3. 对比其他配置管理工具（Puppet、Chef）

---

```dataview
TABLE difficulty, created, related_concepts
FROM #drill AND #redis
SORT created DESC
```
