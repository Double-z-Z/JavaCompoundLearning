---
type: atomic-note
id: CONCEPT-Docker容器实现原理
created: 2026-05-19
updated: 2026-05-19
tags: [docker, 容器, linux, devops]
status: 🌿
mastery: 50
related_emrg: [EMRG-Docker]
related_goal: [GOAL-DevOps]
---

# Docker 容器实现原理

## 一句话定义
Docker 容器本质是一个**受限制的进程**，通过 Linux 内核的 Namespace（隔离视图）、Cgroups（限制资源）、UnionFS（叠加文件系统）三大技术实现轻量级隔离，不拥有独立内核，只拥有独立的用户空间。

## 核心理解

### 三大支柱

| 技术 | 作用 | 类比 |
|------|------|------|
| **Namespace** | 隔离资源视图——让进程以为自己是独立的 | 笼子：让进程看到自己的独立世界 |
| **Cgroups** | 限制资源使用——防止进程占用过多 CPU/内存 | 紧箍咒：限制资源配额 |
| **UnionFS** | 提供文件系统——叠加只读层 + 可写层 | 地板：提供文件系统底座 |

### Namespace 类型

| Namespace | 隔离内容 | 效果 |
|-----------|---------|------|
| `PID` | 进程 ID | 容器内 PID 从 1 开始，看不到宿主机进程 |
| `NET` | 网络设备 | 容器有独立的网络接口和 IP |
| `IPC` | 进程间通信 | 容器间不能直接共享内存 |
| `UTS` | 主机名 | 容器有独立的 hostname |
| `MOUNT` | 文件系统挂载点 | 容器有独立的挂载视图 |
| `USER` | 用户/组 ID | 容器内的 root 不是宿主机的 root |

### 容器 vs 虚拟机

| 维度 | 容器 | 虚拟机 |
|------|------|--------|
| **内核** | 共享宿主机内核 | 独立内核 |
| **隔离级别** | 进程级（Namespace） | 硬件级（Hypervisor） |
| **启动速度** | 秒级 | 分钟级 |
| **资源开销** | 极低（无独立内核/驱动） | 高（完整 OS） |
| **内核版本约束** | 必须兼容宿主机内核 | 无约束 |
| **硬件直通** | 不支持 | 支持（PVE 等） |

### PID 1 与进程管理

**关键规则**：
- 容器以 **PID 1** 进程启动（如 `nginx`、`postgres`）
- **PID 1 退出 → 容器停止**
- 其他子进程退出 → 容器继续运行（除非主进程监控到并重启）

**孤儿进程回收**：
- Linux 内核将孤儿进程的父进程设为 PID 1
- 如果 PID 1 不是真正的 init 系统（如 systemd），可能不会正确回收孤儿进程
- 导致**僵尸进程**堆积，占用进程表项

**最佳实践**：
- 一个容器一个进程（Docker 推荐）
- 多进程场景用 `supervisord` 或拆分为多个容器
- 避免自定义脚本管理进程（容易忽略信号处理和孤儿进程回收）

### 容器健康检查

| 检查方式 | 适用场景 | 优先级 |
|---------|---------|--------|
| **HTTP 检查** | 业务级健康验证 | 首选 |
| **TCP 检查** | 快速连通性测试 | 辅助 |
| **进程检查** | 最基础存活检测 | 兜底 |

**原则**：对应用层进行监控，后两者用于出现问题后的 debug 和恢复。

## 关键关联

- [[Docker镜像分层]] - 关联原因：UnionFS 是容器三大支柱中的文件系统部分，镜像分层是 UnionFS 的具体实现
- [[PVE虚拟化]] - 关联原因：容器共享宿主机内核（进程级隔离），PVE 虚拟机拥有独立内核（硬件级隔离），两者是不同层次的虚拟化
- [[用户态与内核态切换]] - 关联原因：容器共享宿主机内核，容器内进程的系统调用直接穿越到宿主机内核，不经过虚拟化层
- [[线程池]] - 关联原因：Cgroups 对容器 CPU 的限制类似线程池对线程数的控制——都是资源配额管理

## 我的误区与疑问

- ❌ 误区：以为容器是"迷你操作系统"（实际是隔离的进程环境，不拥有独立内核）
- ❌ 误区：以为容器内任何进程崩溃都会导致容器停止（实际只有 PID 1 退出才会停止容器）
- ❓ 疑问：容器如何管理 CPU？对内核来说容器是一个进程还是一组进程？（答：容器是一组受 Cgroups 限制的进程，Cgroups 控制整组的 CPU 配额）
- ❓ 疑问：为什么 Docker 不默认清理已删除文件的残留数据？（答：因为会破坏层的不可变性和缓存复用机制）

## 代码与实践

```bash
# 查看容器的 Namespace
ls -la /proc/<pid>/ns/

# 查看容器的 Cgroups 限制
cat /sys/fs/cgroup/memory/docker/<container-id>/memory.limit_in_bytes
cat /sys/fs/cgroup/cpu/docker/<container-id>/cpu.cfs_quota_us

# 查看容器内进程树
docker top <container_id>

# 健康检查配置
# HEALTHCHECK --interval=5s CMD curl -f http://localhost/health || exit 1
```

## 深入思考

💡 如果容器共享宿主机内核，那内核漏洞是否会影响所有容器？
💡 容器的 Namespace 隔离是否足够安全？有哪些已知的逃逸方式？
💡 Kubernetes 如何利用健康检查进行自动恢复？（liveness probe vs readiness probe）

## 来源

- 对话：[[2026-05-19-Docker镜像与容器原理对话]]

---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-05-19: mastery=50 (理解容器三大支柱、PID 1 机制、容器 vs VM 区别、健康检查策略)

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #docker
SORT mastery DESC
```