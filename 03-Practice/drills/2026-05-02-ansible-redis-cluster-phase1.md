---
created: 2026-05-02
tags: [drill, redis, ansible, devops]
difficulty: 🌿
related_concepts:
  - [[Ansible]]
  - [[PVE虚拟化]]
  - [[Redis-Cluster模式]]
---

# Ansible Redis Cluster - Phase 1: 基础环境准备

> 🎯 目标：在 PVE 上创建 6 台虚拟机并配置 SSH 免密登录，为 Redis Cluster 部署准备基础设施


## 练习内容

### 题目/需求
1. 在 PVE 上创建 6 台 Ubuntu 22.04 虚拟机
   - 3 台作为 Master（10.0.0.102-104）
   - 3 台作为 Slave（10.0.0.105-107）
2. 配置 SSH 免密登录（使用 bootstrap.yml）
3. 验证 Ansible 连通性

### 我的实现

#### 1. 虚拟机创建
使用 `vm-redis-cluster.sh` 脚本创建 6 台 VM：

```bash
# VM 配置
- CPU: 2 vCPU
- 内存: 2GB
- 磁盘: 20GB
- OS: Ubuntu 22.04 LTS
- 网络: 10.0.0.0/24
```

#### 2. SSH 免密配置 (bootstrap.yml)

```yaml
---
- name: Bootstrap Redis Cluster Nodes
  hosts: redis_cluster
  become: yes
  vars:
    admin_user: ubuntu
    redis_user: redis
  tasks:
    - name: Ensure redis user exists
      user:
        name: "{{ redis_user }}"
        state: present
        shell: /bin/bash
        create_home: yes

    - name: Ensure .ssh directory exists for redis user
      file:
        path: "/home/{{ redis_user }}/.ssh"
        state: directory
        owner: "{{ redis_user }}"
        group: "{{ redis_user }}"
        mode: '0700'

    - name: Add SSH public key to redis user
      authorized_key:
        user: "{{ redis_user }}"
        state: present
        key: "{{ lookup('file', '/home/dz-fedora/workspace/JavaLearning/.ssh/id_rsa.pub') }}"
```

执行命令：
```bash
ansible-playbook bootstrap.yml -u ubuntu --ask-pass
```

#### 3. 验证连通性

```bash
$ ansible all -m ping
10.0.0.102 | SUCCESS => {"changed": false, "ping": "pong"}
10.0.0.103 | SUCCESS => {"changed": false, "ping": "pong"}
10.0.0.104 | SUCCESS => {"changed": false, "ping": "pong"}
10.0.0.105 | SUCCESS => {"changed": false, "ping": "pong"}
10.0.0.106 | SUCCESS => {"changed": false, "ping": "pong"}
10.0.0.107 | SUCCESS => {"changed": false, "ping": "pong"}
```

### 遇到的困难
- 无显著困难，PVE 虚拟机创建顺利
- SSH 密钥配置一次成功


## 验证与测试

### 测试方案
1. `ansible all -m ping` - 测试连通性
2. `ansible redis_cluster -m setup | grep ansible_host` - 验证主机变量

### 测试结果
✅ 所有 6 台节点连通性正常
✅ Ansible 可以正常获取主机 facts


## 复盘总结

### 学到的
- Ansible `authorized_key` 模块的使用
- PVE 虚拟机批量创建脚本编写
- Inventory 分组配置（masters/slaves/cluster）

### 关联知识
- [[Ansible]] - Playbook 结构、模块使用
- [[PVE虚拟化]] - VM 创建、网络配置
- [[Redis-Cluster模式]] - 为什么需要 6 个节点（3 Master + 3 Slave）

### 待深化
- Ansible Vault 用于加密敏感信息（如密码）
- PVE Cloud-Init 模板化部署


---

## 🤖 AI评价

### 完成质量
- 功能实现：✅ 完整
- 代码质量：✅ 良好
- 概念应用：✅ 正确

### 对掌握度的影响
- [[Ansible]]: +15分 (正确应用 authorized_key、file 模块)
- [[PVE虚拟化]]: +10分 (完成 VM 创建和配置)

### 建议
1. 后续可以尝试使用 Ansible Vault 管理 SSH 密码
2. 探索 PVE 的 Cloud-Init 功能，实现完全自动化的 VM 初始化

---

```dataview
TABLE difficulty, created, related_concepts
FROM #drill AND #redis
SORT created DESC
```
