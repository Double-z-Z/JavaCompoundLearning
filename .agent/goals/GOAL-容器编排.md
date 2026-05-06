---
type: goal
description: 容器编排：Docker深入、Kubernetes集群管理
driver: 晋升层
urgency: medium
deadline: 2026-10-06
review_date: 2026-07-15
exit_conditions:
  - 掌握Docker镜像构建、优化、安全加固
  - 理解Kubernetes核心概念（Pod/Service/Deployment/ConfigMap）
  - 掌握K8s集群部署与运维
  - 理解K8s网络与存储原理
evidence: 简历要求"熟悉Docker容器技术；了解Kubernetes的底层原理"，当前有Docker基础和Ansible部署经验，K8s待深入
status: in_progress
related_emrg: []
---

# GOAL: 容器编排

## 目标技能

| 技能 | 当前水平 | 目标水平 |
|------|---------|---------|
| Docker | 🌿理解 (40) | 🍎应用 (70) |
| Kubernetes | 🌱初识 (25) | 🌿理解 (60) |
| 容器网络 | 待学习 | 🌿理解 (50) |

## 学习路径

```
阶段1: Docker深入
├── 镜像构建最佳实践
├── Docker网络原理
├── 存储卷管理
├── 安全加固
└── Docker Compose

阶段2: Kubernetes核心
├── 核心概念（Pod/Service/Deployment/ConfigMap/Secret）
├── 集群架构
├── 调度机制
├── 资源配额
└── 探针与健康检查

阶段3: K8s网络与存储
├── Kubernetes网络模型
├── Service与Ingress
├── PersistentVolume/Claim
└乃 存储类与CSI

阶段4: K8s运维
├── 日志与监控
├── 扩缩容
├── 升级与回滚
└── 故障排查
```

## 简历要求回顾

> 熟悉 Docker 容器技术，掌握容器的创建、管理与镜像操作。
> 了解 Kubernetes 的底层原理，包括 Pod、Service、Deployment 等核心概念，以及集群管理与资源调度机制。

## 已完成学习

- [x] Docker基础
- [x] Ansible部署经验
- [ ] K8s核心概念
- [ ] K8s网络原理
- [ ] 生产级集群部署

## 关联项目

- [[ansible-redis-cluster]] - 基础设施即代码
- 新冠核酸检测系统（Docker/K8s生产经验）
