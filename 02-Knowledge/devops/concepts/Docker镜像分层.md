---
type: atomic-note
id: CONCEPT-Docker镜像分层
created: 2026-05-19
updated: 2026-05-19
tags: [docker, 容器, 文件系统, devops]
status: 🌿
mastery: 55
related_emrg: [EMRG-Docker]
related_goal: [GOAL-DevOps]
---

# Docker 镜像分层结构

## 一句话定义
Docker 镜像由多个**只读层**叠加构成，每层只记录相对于上层的差异增量；容器启动时在所有只读层之上叠加一个**可写层**，通过 UnionFS 实现统一的文件系统视图。

## 核心理解

### 分层叠加模型

```
postgres:15-alpine
├─ Layer 3: PostgreSQL 配置脚本、初始化入口    (只读, ~5MB)
├─ Layer 2: 编译 PostgreSQL 二进制文件         (只读, ~40MB)
├─ Layer 1: 编译依赖 (libpq, openssl 等 C 库)  (只读, ~30MB)
└─ Layer 0: Alpine Linux 根文件系统             (只读, ~5MB)

容器运行时:
├─ Container Layer (可写层)                     (容器独有)
├─ Layer 3 (只读, 共享)
├─ Layer 2 (只读, 共享)
├─ Layer 1 (只读, 共享)
└─ Layer 0 (只读, 共享)
```

### 基础镜像的角色

基础镜像（Alpine/Debian/Scratch）提供容器运行所需的**用户空间底座**：

| 提供的东西 | 例子 | 为什么需要 |
|-----------|------|----------|
| C 标准库 | Alpine 用 `musl`，Debian 用 `glibc` | 几乎所有程序都依赖 |
| 基础工具 | `sh`、`ls`、`cp` | 启动脚本、调试 |
| 系统配置 | `/etc/passwd`、`/etc/timezone` | 用户、权限、时区 |
| 包管理器 | `apk`（Alpine）、`apt`（Debian） | 构建时安装依赖 |

**关键区别**：基础镜像提供的是**用户空间**，不是内核。容器共享宿主机的 Linux 内核。

### 基础镜像选择

| 基础镜像 | 体积 | 特点 | 适用场景 |
|---------|------|------|---------|
| `scratch` | 0 MB | 空镜像，无任何文件系统 | Go 静态编译程序 |
| Alpine | ~5 MB | musl libc、BusyBox、apk | 生产环境、CI/CD |
| Debian (默认) | ~120 MB | glibc、生态成熟 | 通用场景 |
| Distroless | ~2 MB | 只有运行时库，无 shell | 安全敏感场景 |

### Copy-on-Write（写时复制）

当容器修改只读层的文件时：
1. **不修改原始层**——只读层保持不变
2. **复制到可写层**——整个文件被复制到容器的可写层
3. **修改作用于副本**——后续读取从可写层返回

### Whiteout 文件（删除标记）

当容器删除只读层的文件时：
1. **不删除原始文件**——只读层不可变
2. **创建 `.wh.<filename>` 标记**——在可写层创建一个 Whiteout 文件
3. **UnionFS 查找时过滤**——看到 Whiteout 就"假装"文件不存在

### 层的共享与隔离

- **多个镜像共享同一基础层**：Alpine 层只存储一份，所有基于 Alpine 的镜像共享
- **容器修改不影响共享层**：修改只写入容器独占的可写层
- **docker commit 的陷阱**：即使删除了文件，新镜像层仍包含原始数据 + Whiteout 标记，体积不会减少

## 关键关联

- [[Docker容器实现原理]] - 关联原因：镜像分层是容器三大支柱（Namespace/Cgroups/UnionFS）中的文件系统部分
- [[Redis-Copy-On-Write]] - 关联原因：Docker 的 CoW 和 Redis RDB 的 CoW 是同一机制在不同场景的应用——都是"修改时才复制"，但粒度不同（Docker 是文件级，OS 是页级）
- [[快照与备份]] - 关联原因：Docker 镜像层类似快照链，每层是前一层的增量差异，共享未修改的数据

## 我的误区与疑问

- ❌ 误区：以为 `-alpine` 后缀意味着 PostgreSQL 变成了 Alpine 的一部分（实际 Alpine 只是用户空间底座）
- ❌ 误区：以为容器内删除文件会真正删除只读层的数据（实际只创建 Whiteout 标记）
- ❌ 误区：以为 `latest` 标签有特殊语义（实际只是默认标签别名，指向维护者设定的"当前最新稳定版"）
- ❓ 疑问：`docker commit` 后镜像变大但文件已删除，如何优化？（答：Dockerfile 中同一 `RUN` 指令内创建并清理，或使用 `--squash` 合并层）

## 代码与实践

```bash
# 查看镜像分层
docker history --no-trunc postgres:15-alpine

# 查看镜像实际基础系统
docker inspect portainer/portainer-ce:latest --format='{{.Os}}/{{.Architecture}}'

# 进入容器查看基础系统（基于 scratch 的镜像会失败）
docker run --rm -it postgres:15-alpine cat /etc/os-release

# 验证 Whiteout 文件
docker diff <container_id>
# C = Changed, A = Added, D = Deleted
```

## 深入思考

💡 如果 Docker 默认自动清理"已删除文件的残留数据"，会破坏什么？（提示：层的不可变性和缓存复用）
💡 基于 `scratch` 的镜像能运行什么程序？Go 静态编译程序为什么不需要基础镜像？
💡 `docker commit` 和 `docker build` 产出的镜像有什么本质区别？

## 来源

- 对话：[[2026-05-19-Docker镜像与容器原理对话]]

---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-05-19: mastery=55 (理解镜像分层结构、CoW/Whiteout 机制、基础镜像角色、层共享与隔离)

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #docker
SORT mastery DESC
```