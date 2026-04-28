---
created: 2026-04-27
tags: [devops, virtualization, pve, proxmox]
status: 🌿
mastery: 50
---

# PVE虚拟化

## 一句话定义
Proxmox VE（PVE）是基于Debian的开源虚拟化平台，支持KVM虚拟机和LXC容器，提供Web界面管理，是企业级私有云基础设施。


## 核心理解

### 架构层次

```
物理硬件
    │
    ├── CPU/Memory/Storage
    │
    └── PVE内核（基于Debian）
            │
            ├── KVM（硬件虚拟化）
            │       ├── VM1 (Ubuntu)
            │       ├── VM2 (CentOS)
            │       └── ...
            │
            └── LXC（操作系统级虚拟化）
                    ├── Container1
                    └── Container2
```

### 存储系统

| 存储类型 | 说明 | 适用场景 |
|---------|------|---------|
| **LVM-Thin** | 精简置备，用多少占多少 | VM磁盘（默认推荐） |
| **LVM** | 厚置备，立即分配全部空间 | 需要稳定性能的场景 |
| **ZFS** | 高级文件系统，支持快照/压缩 | 企业级存储 |
| **Ceph** | 分布式存储 | 多节点集群 |

**精简置备 vs 厚置备**：
- 精简置备：分配100G，实际只用20G → 占用20G物理空间
- 厚置备：分配100G → 立即占用100G物理空间

### 网络架构

```
物理网卡 (nic0)
    │
    └── Linux Bridge (vmbr0)
            │
            ├── VM1 (virtio-net)
            ├── VM2 (virtio-net)
            └── VM3 (virtio-net)
```

**VirtIO**：半虚拟化驱动，性能接近物理机

### Cloud-Init集成

**工作原理**：
```
PVE生成配置 → 挂载为虚拟CD-ROM
                    │
                    └── VM启动时读取
                            │
                            └── Cloud-Init服务执行初始化
                                    ├── 设置IP地址
                                    ├── 创建用户
                                    └── 配置SSH
```

**这让我想起你之前学的** [[服务启停设计模式]]——Cloud-Init是VM的"初始化服务"。


## 关键关联

- [[Ansible]] - 关联原因：PVE创建VM后，使用Ansible批量配置
- [[服务启停设计模式]] - 关联原因：VM的生命周期管理（创建、启动、停止、删除）
- [[NIO-Selector]] - 关联原因：PVE的Web界面需要高效管理多VM状态
- [[Cloud-Init]] - 关联原因：PVE与Cloud-Init集成实现VM自动化初始化


## 我的误区与疑问

- ❓ PVE集群（多节点）与单节点的架构差异？
- ❓ 什么场景应该选择LXC而不是KVM？


## 代码与实践

### 常用命令

```bash
# 创建VM
qm create 102 --name ubuntu-vm --memory 2048 --cores 2

# 导入磁盘
qm importdisk 102 ubuntu.img local-lvm

# 设置网络
qm set 102 --net0 virtio,bridge=vmbr0

# 启动/停止/重启
qm start 102
qm stop 102
qm reset 102

# 查看状态
qm status 102
qm list
```

### 创建模板和克隆

```bash
# 将VM转换为模板
qm template 102

# 从模板克隆（链接克隆）
qm clone 102 103 --name vm-clone --full 0

# 从模板克隆（完整克隆）
qm clone 102 104 --name vm-full --full 1
```


## 深入思考

💡 PVE与VMware vSphere、Hyper-V的优劣势对比？
💡 如何设计PVE集群实现高可用（HA）？
💡 PVE的备份策略（快照、备份、复制）如何选择？


## 来源
- 项目：[[ansible-redis-cluster]]
- 对话：[[2026-04-27-环境选择与部署方案-dialogue]]


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-04-27: mastery=50 (创建6节点集群，解决实际问题)

### 建议下一步
1. 学习PVE集群配置（多节点HA）
2. 了解PVE与Ceph集成
3. 对比PVE与云厂商虚拟化方案

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #virtualization
SORT mastery DESC
```
