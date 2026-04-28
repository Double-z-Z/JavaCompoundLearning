---
created: 2026-04-27
tags: [devops, ansible, automation]
status: 🌿
mastery: 40
---

# Ansible

## 一句话定义
Ansible是一个基于Python的自动化运维工具，通过SSH协议批量管理服务器，使用YAML定义"期望状态"，实现基础设施即代码（IaC）。


## 核心理解

### 架构设计

```
控制节点 (Control Node)
    │
    ├── Inventory (主机清单)
    │       └── 定义管理哪些服务器
    │
    ├── Playbook (剧本)
    │       └── 定义要执行的任务
    │
    └── SSH连接
            │
            ├── 被管理节点1
            ├── 被管理节点2
            └── ... (并行执行)
```

### 核心组件

| 组件 | 作用 | 类比 |
|-----|------|------|
| **Inventory** | 定义管理的服务器列表 | [[NIO-Selector]]管理多个Channel |
| **Playbook** | YAML格式的任务定义 | Java的方法调用链 |
| **Role** | 可复用的任务集合 | Java的类/模块 |
| **Module** | 具体功能单元（apt、copy、systemd等） | Java的方法 |
| **Template** | Jinja2模板引擎生成动态配置 | String.format() |
| **Handler** | 事件触发器（配置变更后重启服务） | 观察者模式 |

### 幂等性（Idempotency）

**核心机制**：先检查当前状态，再决定是否执行

```yaml
- name: Install Redis
  apt:
    name: redis-server
    state: present  # "确保存在"，不是"安装"
```

执行流程：
1. 检查redis-server是否已安装
2. 已安装 → 跳过（ok状态）
3. 未安装 → 执行安装（changed状态）

**这让我想起你之前学的** [[状态机模式-服务生命周期]]——Ansible就是声明"期望状态"，系统自动调整到那个状态。


## 关键关联

- [[NIO-Selector]] - 关联原因：Ansible并行管理多主机，类似Selector单线程管理多Channel
- [[状态机模式-服务生命周期]] - 关联原因：Ansible的systemd模块实现服务状态管理
- [[CompletableFuture]] - 关联原因：Ansible异步并行执行多个任务，然后汇总结果
- [[PVE虚拟化]] - 关联原因：Ansible常用于管理虚拟机和容器


## 我的误区与疑问

- ❓ 大规模集群（1000+节点）时，Ansible的性能瓶颈在哪里？
- ❓ Ansible与Terraform的分工边界是什么？


## 代码与实践

### 项目结构

```
project/
├── ansible.cfg          # 全局配置
├── inventory/
│   └── hosts.ini        # 主机清单
├── group_vars/          # 组变量
├── site.yml             # 主Playbook
└── roles/               # 角色目录
    └── app/
        ├── tasks/
        ├── templates/
        └── handlers/
```

### 常用命令

```bash
# 测试连通性
ansible all -m ping

# 执行Playbook
ansible-playbook site.yml

# 检查模式（只看不执行）
ansible-playbook site.yml --check

# 显示详细输出
ansible-playbook site.yml -vvv
```


## 深入思考

💡 为什么Ansible选择SSH而不是Agent？
💡 如何在Ansible中实现滚动更新（Rolling Update）？
💡 Ansible Tower/AWX提供了哪些企业级功能？


## 来源
- 项目：[[ansible-redis-cluster]]
- 对话：[[2026-04-27-环境选择与部署方案-dialogue]]


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-04-27: mastery=40 (完成Redis集群部署实践)

### 建议下一步
1. 学习Ansible高级特性（条件判断、循环、变量优先级）
2. 对比Ansible与SaltStack/Puppet的差异
3. 了解Ansible Tower企业级功能

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #devops
SORT mastery DESC
```
