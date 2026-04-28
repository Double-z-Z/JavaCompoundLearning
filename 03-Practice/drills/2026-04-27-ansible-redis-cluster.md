---
created: 2026-04-27
tags: [drill, devops, redis, ansible]
difficulty: 🌿
related_concepts:
  - [[Ansible]]
  - [[PVE虚拟化]]
  - [[Redis-Cluster]]
  - [[Cloud-Init]]
---

# Ansible部署Redis集群实践

> 🎯 目标：使用Ansible自动化部署6节点Redis集群（3主3从）


## 练习内容

### 题目/需求
1. 在PVE虚拟化平台上创建6个Ubuntu VM
2. 使用Ansible自动化安装和配置Redis
3. 为后续Redis Cluster初始化做准备


### 我的实现

#### 1. PVE环境准备

```bash
# 创建6个VM的脚本
for i in 102 103 104 105 106 107; do
  qm create $i --name redis-node$i --memory 2048 --cores 2
  # ... 其他配置
done
```

**关键配置**：
- 使用VirtIO SCSI控制器（解决启动卡住问题）
- 使用Ubuntu Cloud Image快速部署
- 网络：10.0.0.102-107，桥接到vmbr1

#### 2. Ansible项目结构

```
ansible-redis-cluster/
├── ansible.cfg          # Ansible配置
├── inventory/hosts.ini  # 主机清单（6节点分组）
├── group_vars/all.yml   # 变量定义
├── site.yml             # 主Playbook
└── roles/redis/         # Redis角色
    ├── tasks/main.yml
    ├── templates/redis.conf.j2
    └── handlers/main.yml
```

#### 3. 核心配置

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

**roles/redis/tasks/main.yml**：
```yaml
- name: Install Redis
  apt:
    name: redis-server
    state: present
  become: yes

- name: Configure Redis
  template:
    src: redis.conf.j2
    dest: /etc/redis/redis.conf
  become: yes
  notify: Restart Redis
```


### 遇到的困难

1. **VM启动卡住**：使用LSI SCSI控制器时，Ubuntu Cloud Image启动卡住
   - 解决：改用VirtIO SCSI控制器

2. **Console无输出**：Cloud Image默认输出到serial console
   - 解决：添加serial设备或改用VGA输出

3. **网络规划**：需要确保所有VM在同一网段，且能互相通信
   - 解决：统一使用10.0.0.x/24网段，通过WRT软路由连接


## 验证与测试

### 测试方案
1. `ansible all -m ping` - 测试连通性
2. `ansible-playbook site.yml --check` - 检查模式
3. `ansible-playbook site.yml` - 正式执行
4. `redis-cli -h 10.0.0.102 ping` - 验证Redis服务

### 测试结果
- ✅ 6个节点全部ping通
- ✅ Ansible成功部署Redis到所有节点
- ✅ Redis服务正常启动
- ⏳ 等待集群初始化


## 复盘总结

### 学到的
1. **Ansible核心概念**：
   - Inventory管理主机清单
   - Playbook定义执行流程
   - Role实现任务复用
   - Template动态生成配置

2. **PVE虚拟化技巧**：
   - Cloud Image快速部署
   - VirtIO设备性能优化
   - 网络桥接配置

3. **幂等性设计**：
   - Ansible通过状态检查实现幂等
   - `state: present`确保存在而非重复安装

### 关联知识
- [[Ansible]] - 应用场景：自动化部署6节点集群
- [[PVE虚拟化]] - 应用场景：创建和管理VM
- [[NIO-Selector]] - 联系：Ansible并行管理多主机，类似Selector管理多Channel
- [[服务启停设计模式]] - 联系：Ansible的systemd模块实现服务生命周期管理

### 待深化
- Redis Cluster初始化命令
- 哈希槽分配原理
- Gossip协议工作机制


---

## 🤖 AI评价

### 完成质量
- 功能实现：完整
- 代码质量：良好
- 概念应用：正确

### 对掌握度的影响
- [[Ansible]]: +20分 (完成完整部署，理解核心概念)
- [[PVE虚拟化]]: +15分 (解决实际问题)
- [[Cloud-Init]]: +10分 (理解工作原理)

### 建议
1. 继续完成Redis Cluster初始化
2. 深入学习Redis Cluster原理
3. 对比Ansible与Docker/K8s的部署方式

---

## 📊 相关练习统计

```dataview
TABLE difficulty, created, related_concepts
FROM #drill AND #devops
SORT created DESC
```
