---
type: emrg
id: EMRG-Docker
title: Docker与容器技术
maturity: emerging
created: 2026-05-19
updated: 2026-05-19
related_goals: [GOAL-DevOps]
subtopics:
  - 镜像与分层
  - 容器实现原理
  - 网络与编排
---

# Docker 与容器技术

> 成熟度: 🟡 emerging

## 一句话定义

Docker 容器本质是受限制的进程，通过 Linux 内核的 Namespace（隔离视图）、Cgroups（限制资源）、UnionFS（叠加文件系统）实现轻量级隔离，镜像分层机制使构建、分发和复用高效且可靠。

## 知识拓扑

Docker 与容器技术
  ├─ 镜像与分层
  │   └─ [[Docker镜像分层]]
  │       ├─ UnionFS 叠加机制
  │       ├─ Copy-on-Write（写时复制）
  │       ├─ Whiteout 文件（删除标记）
  │       └─ 基础镜像选择（Alpine/Debian/Scratch）
  ├─ 容器实现原理
  │   └─ [[Docker容器实现原理]]
  │       ├─ Namespace（6 种类型）
  │       ├─ Cgroups（资源限制）
  │       ├─ PID 1 与进程管理
  │       └─ 容器 vs 虚拟机
  └─ 网络与编排（待学习）
      ├─ Docker 网络模型
      ├─ 虚拟网桥与 veth pair
      └─ NAT 与容器间通信

## 关键缺口（待补充）

- [ ] Docker 网络模型（bridge/host/overlay/none）
- [ ] Dockerfile 优化实践
- [ ] Docker Compose 多容器编排
- [ ] 容器安全与逃逸防护

## 关联领域

- [[EMRG-Redis]] — Redis 部署在 Docker 中，RDB 的 COW 与 Docker 镜像的 COW 是同一机制的不同应用
- [[PVE虚拟化]] — 容器（进程级隔离）vs 虚拟机（硬件级隔离），不同层次的虚拟化

---

## 🤖 AI 工作区

### 核心成员

```dataviewjs
const emrgId = dv.current().id;
dv.table(
  ["笔记", "mastery", "验证状态"],
  dv.pages()
    .where(p => p.related_emrg && p.related_emrg.includes(emrgId))
    .sort(p => p.mastery, 'desc')
    .map(p => [
      p.file.link,
      p.mastery ?? ' ',
      (p.mastery >= 60) ? '🟢' : '🟡'
    ])
);
```

### 涌现历史

- **2026-05-19**: 因 Docker 镜像与容器原理学习创建，初始 2 篇笔记（Docker镜像分层、Docker容器实现原理）

### 成熟度说明

2/2 篇笔记 mastery 在 50-55 区间（🌿理解级），覆盖镜像分层和容器实现原理。网络与编排方向待学习。

### 检查点

- [ ] 子主题数: 3（超过 7 则触发裂变）
- [ ] 最后更新: 2026-05-19（超过 90 天则触发归档检查）