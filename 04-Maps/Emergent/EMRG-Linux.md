---
type: emrg
id: EMRG-Linux
title: Linux 系统管理
maturity: theoretical
created: 2026-05-29
updated: 2026-05-29
related_goals: [GOAL-Linux系统管理, GOAL-Java核心深化]
subtopics:
  - "进程监控"
  - "内存管理"
  - "IO 子系统"
  - "网络工具"
  - "Shell 工具链"
---

# EMRG-Linux 系统管理

> 成熟度: 🟡 theoretical

## 一句话定义
Linux 系统管理的核心是**通过观测工具（top/free/iostat/ss）建立从现象到根因的诊断路径**，过程序员视角理解内核的进程调度、内存回收、IO 栈和网络协议。

## 知识拓扑

进程监控
  ├─ [[Linux进程监控]] (45) — top/ps//proc，三步定位法的第一环
  │   └─ 关联 [[虚拟内存与物理内存]] — VIRT vs RES 的根因在 OS 内存模型
  └─ 关联 [[Linux内存监控]] — 进程 RES 需要系统级 available 做对照

内存管理
  ├─ [[Linux内存监控]] (45) — free/vmstat//proc/meminfo，Active/Inactive LRU
  │   └─ 关联 [[虚拟内存与物理内存]] — anon vs file 页是内核 LRU 的分类基础
  └─ [[Swap与zram]] (40) — swappiness/zram 压缩/与 Java GC 的冲突
      └─ 关联 [[Linux内存监控]] — si/so 列是 swap 活动的观测窗口

IO 子系统
  ├─ [[Linux-IO监控]] (40) — iostat -x/await/%util/DMA 零拷贝链路
  │   └─ 关联 [[DMA与IOMMU]] — DMA 是 IO 数据流绕过 CPU 的硬件基础
  └─ 关联 [[NIO-Buffer]] — DirectByteBuffer 是 DMA 在 Java 层的体现

网络工具
  └─ [[Linux网络工具]] (35) — ss/lsof，端口→进程的定位链

Shell 工具链
  ├─ [[Shell管道与工具链]] (50) — 管道思维，find/xargs/grep/awk，bash 边界
  └─ [[shell重定向]] (45) — 管道横向串联进程，重定向纵向连接文件

## 关键缺口（待补充）
- [ ] /etc/fstab 写法与挂载选项
- [ ] LVM 实操 (pvcreate/vgcreate/lvcreate)
- [ ] btrfs 子卷管理 (create/snapshot/rollback)
- [ ] systemd 服务管理

## 项目实战
| 项目 | 状态 | 关联笔记 |
|------|------|---------|
| 无专项项目 | — | W1 冲刺为纯诊断能力训练 |

## 关联领域
- [[EMRG-NIO网络编程]] — DMA/DirectByteBuffer 是 NIO 零拷贝的 OS 层基础
- [[EMRG-并发编程]] — futex/用户态-内核态切换依赖 OS 调度机制
- [[EMRG-Docker]] — 容器本质是 namespace/cgroup，依赖 Linux 内核特性

---

## 🤖 AI 工作区（以下由 Dataview 自动维护，请勿手动编辑）

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

### 边界声明

#### 核心成员（纳入本 EMRG）
- [[Linux进程监控]] — 进程层观测，是诊断的起点
- [[Linux内存监控]] — 内存层观测，LRU 回收机制
- [[Linux-IO监控]] — IO 层观测，DMA/零拷贝链路
- [[Linux网络工具]] — 网络层观测，端口→进程定位
- [[Swap与zram]] — 内存回收的后备存储
- [[Shell管道与工具链]] — 观测工具的组合方式
- [[shell重定向]] — 数据流控制的另一维度

#### 边缘关联（链接但不纳入）
- [[虚拟内存与物理内存]] → 归属 OS 内核概念（暂无 EMRG），是 Linux 内存监控的理论基础
- [[TLB与CPU缓存层级]] → 归属 OS 内核概念，是性能优化的 CPU 侧知识
- [[DMA与IOMMU]] → 归属 OS 内核概念，是 NIO 零拷贝的硬件基础

#### 跨界枢纽（被多个 EMRG 引用）
- [[DMA与IOMMU]] — 同时关联 [[EMRG-NIO网络编程]]（DirectByteBuffer）和本 EMRG（IO 监控）

### 涌现历史
- 2026-05-29: 因密度溢出创建（W1 冲刺完成 7 篇 Linux 原子笔记，Linux进程监控 被同标签 5 篇引用，交叉链接密度达标）

### 成熟度说明
7 篇笔记全部为实操+理论讨论产出，mastery 35-50。当前无生产 Incident 或专项项目验证（你的 ansible-redis-cluster 项目可升级为实战证据）。待有项目实战后升级为 verified。

### 检查点
- [x] 子主题数: 5（未超过 7，暂不触发裂变）
- [x] 最后更新: 2026-05-29
