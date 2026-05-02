---
created: 2026-05-02
tags: [drill, redis, ansible, devops]
difficulty: 🌿
related_concepts:
  - [[Ansible]]
  - [[Redis-Cluster模式]]
  - [[Redis-持久化]]
---

# Ansible Redis Cluster - Phase 2: Redis 安装与配置

> 🎯 目标：使用 Ansible 自动化安装 Redis 并配置集群模式，确保所有节点正确启动


## 练习内容

### 题目/需求
1. 完善 `redis.conf.j2` 模板，支持集群模式配置
2. 编写 Ansible Role 实现 Redis 安装、配置、启动
3. 验证所有节点 Redis 服务正常运行

### 我的实现

#### 1. redis.conf.j2 模板

```jinja2
bind 127.0.0.1 {{ ansible_host }}
port {{ redis_port }}
cluster-enabled {{ redis_cluster_enabled }}
cluster-config-file nodes-{{ redis_port }}.conf
cluster-node-timeout {{ redis_cluster_node_timeout }}
appendonly yes
daemonize no
supervised systemd
dir /var/lib/redis
```

**关键配置说明**：
- `bind 127.0.0.1 {{ ansible_host }}` - 必须绑定本地 + 实际 IP，否则集群节点间无法通信
- `cluster-enabled yes` - 启用集群模式
- `cluster-config-file` - 集群状态自动保存文件
- `appendonly yes` - 启用 AOF 持久化
- `daemonize no` + `supervised systemd` - 使用 systemd 管理进程

#### 2. Ansible Role (roles/redis/)

**tasks/main.yml**:
```yaml
---
- name: Update apt cache
  apt:
    update_cache: yes
  become: yes

- name: Install Redis
  apt:
    name: redis-server
    state: present
  become: yes

- name: Configure Redis
  template:
    src: redis.conf.j2
    dest: /etc/redis/redis.conf
    mode: '0644'
  become: yes
  notify: Restart Redis

- name: Start Redis
  systemd:
    name: redis-server
    state: started
    enabled: yes
  become: yes
```

**handlers/main.yml**:
```yaml
---
- name: Restart Redis
  systemd:
    name: redis-server
    state: restarted
  become: yes
```

#### 3. 执行部署

```bash
ansible-playbook site.yml
```

### 遇到的困难
- **问题**: `bind` 配置如果只写 `{{ ansible_host }}`，本地 `redis-cli` 无法连接
- **解决**: 改为 `bind 127.0.0.1 {{ ansible_host }}`，同时绑定本地和外部 IP
- **启示**: 集群节点间通信需要外部 IP，但本地管理工具需要 127.0.0.1


## 验证与测试

### 测试方案
1. 检查 Redis 服务状态
2. 验证集群模式是否启用
3. 测试本地连接

### 测试结果

```bash
# 1. 检查服务状态
$ ansible redis_cluster -m shell -a "systemctl status redis-server"
10.0.0.102 | SUCCESS | rc=0 >>
● redis-server.service - Advanced key-value store
   Active: active (running) since ...

# 2. 验证集群模式
$ ansible redis_cluster -m shell -a "redis-cli INFO cluster"
10.0.0.102 | SUCCESS | rc=0 >>
# Cluster
cluster_enabled:1

# 3. 本地连接测试
$ redis-cli PING
PONG
```

✅ 所有节点 Redis 服务正常运行
✅ 集群模式已启用（cluster_enabled:1）
✅ 本地连接正常


## 复盘总结

### 学到的
- Jinja2 模板语法在 Ansible 中的应用
- Redis 集群模式的关键配置参数
- `bind` 配置对集群通信的影响
- Ansible Handlers 的触发机制（notify）

### 关联知识
- [[Ansible]] - Template 模块、Handlers 机制
- [[Redis-Cluster模式]] - 集群模式启用条件、节点通信机制
- [[Redis-持久化]] - AOF 配置、数据安全

### 待深化
- Redis 配置文件更多参数调优（maxmemory、淘汰策略等）
- Ansible 条件判断（when）在配置管理中的应用


---

## 🤖 AI评价

### 完成质量
- 功能实现：✅ 完整
- 代码质量：✅ 良好
- 概念应用：✅ 正确

### 对掌握度的影响
- [[Ansible]]: +15分 (掌握 Template、Handlers)
- [[Redis-Cluster模式]]: +10分 (理解集群配置)
- [[Redis-持久化]]: +5分 (AOF 配置实践)

### 建议
1. 尝试添加更多 Redis 调优参数到模板中
2. 探索使用 Ansible `lineinfile` 模块进行增量配置更新

---

```dataview
TABLE difficulty, created, related_concepts
FROM #drill AND #redis
SORT created DESC
```
