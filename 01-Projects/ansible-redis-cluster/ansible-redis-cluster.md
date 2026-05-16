---
type: project
id: PROJECT-ansible-redis-cluster
created: 2026-04-29
tags: [redis, ansible, devops, cluster]
status: completed
mastery: 80
related_emrg:
  - [[EMRG-Redis]]
related_goal:
  - [[GOAL-Redis深入]]
---

# Ansible Redis Cluster

> 项目目标：使用 Ansible 自动化部署 Redis 6 节点集群（3 Master + 3 Slave），实践 Redis Cluster 模式的高可用架构
> 项目类型：综合应用型（DevOps + 分布式系统）
> **状态：✅ 已完成**


## 架构

- 3 Master: 10.0.0.102-104
- 3 Slave: 10.0.0.105-107

## 快速开始

```bash
# 测试连通性
ansible all -m ping

# 部署Redis
ansible-playbook site.yml

# 检查集群状态
ansible-playbook check-cluster.yml
```

## 项目结构

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
├── roles/
│   └── redis/
│       ├── handlers/
│       │   └── main.yml     # 重启 Redis 处理器
│       ├── tasks/
│       │   └── main.yml     # 安装、配置、启动任务
│       └── templates/
│           └── redis.conf.j2 # Redis 配置文件模板
└── docs/
    └── ansible-redis-cluster-design.md  # 详细设计文档
```

## 文档

- **设计详情**: [[docs/ansible-redis-cluster-design.md]] — 涉及知识点、架构决策、实现阶段、故障排查、综合洞察

## 关联链接

- 主题地图: [[EMRG-Redis]]
- 评估卡片: [[2026-05-15-ansible-redis-cluster-评估卡片]]
