# DevOps Dashboard — 文档中心

> **项目定位**: 基于 MCP (Model Context Protocol) 的 AI-Native DevOps 实验平台
> **当前版本**: 统一版本 (聚合根入口 + 反退化约束) | **最后更新**: 2026-05-23

---

## 📁 文档结构

```
docs/
├── 00-术语表.md                      # 📖 统一语言词汇表（所有文档的前置阅读）
│
├── 02-设计/                          # 🏗️ 最新架构设计
│   ├── 01-架构决策记录.md            # 全部 ADR：V1(1-10) + V2(11-20) + V3(21-28)
│   ├── 02-领域模型.md                # Host / Environment / DeployPipeline / LogAggregate
│   └── 03-整体架构.md                # 分层架构图 + 数据流 + 部署模式
│
├── 03-接口契约/                      # 🔌 MCP 接口定义
│   └── 02-MCP-Tools.md               # MCP Tool JSON Schema 定义（当前版本）
│
├── 04-实施计划/                      # 📋 执行路线
│   ├── 01-迁移路线图.md              # Phase 改造计划与验收标准
│   ├── 02-当前任务清单.md            # 可勾选的执行 checklist
│   └── 03-代码对齐计划.md            # 按术语表统一命名的文件级修改计划
│
├── 05-边界约束.md                    # 🛡️ 约束规范：description 三段式 + 错误响应 + 状态机
│
├── 05-参考/                          # 📚 详细参考
│   ├── 01-服务模板.md                # Nacos/RabbitMQ/MySQL/Redis 模板库
│   ├── 02-数据结构.md                # 完整 YAML Schema（Host/Env/Session/Evidence）
│   └── 03-配置模板.md                # hosts.yml + application-mcp.yml
│
├── 06-运维/                          # 🔧 运维操作
│   └── 01-Trae配置与运维指南.md       # Trae/Claude MCP 配置 + 启停 + 排查
│
├── V2/                               # 📦 V2 归档（仅保留变更日志与历史记录）
│   ├── CHANGELOG.md                  # V2 引入且仍然有效的设计决策
│   └── HISTORY.md                    # V2 引入但已放弃的设计思路
│
├── V3/                               # 📦 V3 归档（仅保留变更日志与历史记录）
│   ├── CHANGELOG.md                  # V3 引入且仍然有效的设计决策
│   └── HISTORY.md                    # V3 引入但已放弃/延后的设计思路
│
└── 07-归档/                          # 📦 更早的历史文档（不再维护）
    ├── Phase1完成报告.md
    ├── 变更日志.md
    └── 流水线设计.md
```

---

## 🎯 项目核心定位

DevOps Dashboard 是一个 **AI-Native 的 DevOps 实验平台**：

| 维度 | V1 (已完成) | V2 (已完成) | 当前版本 (统一设计) |
|------|------------|------------|-------------------|
| **交互方式** | Swagger UI + 手写 JSON/YAML | 自然语言 → AI → MCP Tools | 自然语言 → AI → MCP 聚合根入口 |
| **核心能力** | 环境 CRUD + 实验 CRUD | Host 管理 + 远程压测 + 证据收集 | 部署流水线编排 + 反退化约束 + 统一语言 |
| **基础设施认知** | 扁平节点列表 | 层次化拓扑 (PVE → VM → Docker) | 层次化拓扑 + 状态机精化 |
| **部署模型** | 单机 Docker Compose | 多角色分离 (MCP/Target/Loadgen) | 多角色分离 + 聚合根编排 |
| **AI 约束** | 无 | 保守式交互（发现优先、逐步确认） | 唯一合法路径（禁止本地替代） |
| **前端** | Vue 3（未实现） | 无需前端，AI 即界面 | 无需前端，AI 即界面 |

**当前版本一句话**: AI 无法绕过 MCP 进行本地 docker/ssh/curl 操作，所有路径必须经过聚合根入口，错误响应封堵替代方案，命名统一消除歧义。

---

## 📖 阅读顺序

### 第一次打开项目

1. [00-术语表.md](./00-术语表.md) → **必须先读**。统一语言是理解所有设计文档的基础。
2. [02-设计/03-整体架构.md](./02-设计/03-整体架构.md) → 理解分层架构与防腐层
3. [02-设计/02-领域模型.md](./02-设计/02-领域模型.md) → 理解核心业务概念与状态机

### 了解接口契约

4. [03-接口契约/02-MCP-Tools.md](./03-接口契约/02-MCP-Tools.md) → 10 个 Tools 的完整 Schema
5. [05-边界约束.md](./05-边界约束.md) → description 三段式、错误响应、状态机约束

### 了解版本演进

6. [V2/CHANGELOG.md](./V2/CHANGELOG.md) → V2 引入且仍然有效的设计决策
7. [V2/HISTORY.md](./V2/HISTORY.md) → V2 引入但已放弃的设计思路
8. [V3/CHANGELOG.md](./V3/CHANGELOG.md) → V3 引入且仍然有效的设计决策
9. [V3/HISTORY.md](./V3/HISTORY.md) → V3 引入但已放弃/延后的设计思路

### 开始编码前

10. [04-实施计划/01-迁移路线图.md](./04-实施计划/01-迁移路线图.md) → 查看 Phase 任务
11. [03-接口契约/03-Java-接口定义.md](./03-接口契约/03-Java-接口定义.md) → 接口定义参考

### 编码时参考

| 需求 | 参考文档 |
|------|---------|
| 写 MCP Handler | [03-接口契约/02-MCP-Tools.md](./03-接口契约/02-MCP-Tools.md) + [05-边界约束.md](./05-边界约束.md) |
| 写领域对象 | [02-设计/02-领域模型.md](./02-设计/02-领域模型.md) + [00-术语表.md](./00-术语表.md) |
| 命名有歧义 | [00-术语表.md](./00-术语表.md) — 唯一权威 |
| 部署中间件 | [05-参考/01-服务模板.md](./05-参考/01-服务模板.md) |
| 配置 hosts.yml | [05-参考/03-配置模板.md](./05-参考/03-配置模板.md) |
| 配置 Trae MCP | [06-运维/01-Trae配置与运维指南.md](./06-运维/01-Trae配置与运维指南.md) |

---

## 🔑 核心设计原则

| 原则 | 一句话 | 来源 |
|------|--------|------|
| **统一语言** | 同一词汇在同一上下文内只能有一个含义 | 当前版本新增 |
| **保守交互** | AI 只做向导不做决策；每步写操作需用户确认 | V2 ADR-012 |
| **发现优先** | 先查 Resources 再呈现选项，不基于静态知识推断 | V2 ADR-012 |
| **角色分离** | MCP Host / Target Host / Loadgen Host 三者显式区分 | V2 ADR-013 |
| **层次拓扑** | PVE → VM → Docker 父子关系必须表达 | V2 ADR-014 |
| **网络感知** | 压测前分析路径类型，提示结果可信度 | V2 ADR-016 |
| **响应式保持** | WebFlux 非阻塞，流式日志支持背压 | V2 ADR-019 + V1 DD-7 |
| **渐进改造** | MCP 作为 AI 交互的唯一协议，逐步替代旧有接口 | V2 ADR-020 |
| **聚合根优先** | Environment / Experiment / Host 是核心业务实体 | V1 DD-1,2 |
| **唯一入口** | MCP 是 AI 操作基础设施的唯一合法路径 | V3 ADR-021 |
| **反退化** | Description 三段式 + 错误响应封堵本地替代 | V3 ADR-022 |
| **状态机精化** | CREATING→READY→DEPLOYING→RUNNING/ERROR→DESTROYED | V3 ADR-023 |
| **服务白名单** | 只允许部署注册表中的服务 | V3 ADR-025 |

---

## ⚡ 快速启动

```bash
# 1. 启动数据库
docker compose -f docker-compose.devtools.yml up -d postgres

# 2. 编译打包
./mvnw clean package -DskipTests

# 3. 启动 MCP Server（独立进程模式）
java -Dspring.profiles.active=mcp -Dserver.port=8081 \
  -jar target/devops-dashboard-0.0.1-SNAPSHOT.jar

# 4. 在 Trae 中配置 MCP（见 06-运维/ 目录）

# 5. 测试连接：在 Trae 中问 "我现在有哪些可用主机？"
```

详细步骤见 [01-入门/01-快速启动.md](./01-入门/01-快速启动.md)

---

## 📊 当前进度

| 阶段 | 内容 | 状态 | 说明 |
|------|------|------|------|
| **V1 Phase 1** | 基础设施 + 核心 API | ✅ 完成 | DDD 分层、Environment/Experiment CRUD、58 个测试 |
| **V1 Phase 2** | Experiment Service + Docker Provider | ✅ 部分 | Service 已实现，Vue 前端未做（被 V2 替代） |
| **V2 Phase 1** | Host 层 + MCP Server 骨架 | ✅ 已完成 | 17 个测试通过 |
| **V2 Phase 2** | 环境 MCP 化 | ✅ 已完成 | 环境 CRUD + 部署 |
| **V2 Phase 3** | 测试工具 MCP 化 | ✅ 已完成 | 压测 + 网络分析 |
| **V2 Phase 4** | 实验会话与证据 | ⏳ 部分完成 | Session 模型就绪，Tool 延后 |
| **V2 Phase 5** | Prompt 优化 | ⏳ 待开始 | — |
| **V3 设计** | 聚合根入口 + 反退化约束 | ✅ 已完成 | 设计文档已编写 |
| **统一语言重构** | 术语表 + 文档重写 | ✅ 已完成 | 消除 type/runtime/serviceName 歧义 |
| **代码对齐** | 按术语表修正命名 | ⏳ 待开始 | — |

### 已有代码统计

| 指标 | 数值 |
|------|------|
| Java 源文件 | ~70 |
| 测试用例 | 58（全部通过） |
| 设计决策 (ADR) | 28 条（V1: 10 + V2: 10 + V3: 8） |
| MCP Tools | 10 |
| MCP Resources | 4 |

---

## 📋 版本归档索引

### V2 归档

V2 的详细设计文档已归档，核心思路提炼在：
- [V2/CHANGELOG.md](./V2/CHANGELOG.md) → 仍然有效的设计决策
- [V2/HISTORY.md](./V2/HISTORY.md) → 已放弃的设计思路

### V3 归档

V3 的详细设计文档已归档，核心思路提炼在：
- [V3/CHANGELOG.md](./V3/CHANGELOG.md) → 仍然有效的设计决策
- [V3/HISTORY.md](./V3/HISTORY.md) → 已放弃/延后的设计思路

---

## 🔄 演进关系

```
V1 (基础设施 + 核心领域) —— 建立 DDD 分层、Environment/Experiment 聚合根
    ↓
V2 (AI-Native 原子化 Tools) —— 新增 Host/Loadgen/Evidence 层
    ↓
V3 (聚合根入口 + 反退化约束) —— 新增 DeployPipeline / LogAggregate，状态机精化
    ↓
当前版本 (统一语言 + 文档重写) —— 消除命名歧义，建立术语表权威
```

**关键**: 每次演进都是**附加层**，不推翻重来。现有 `domain`、`infrastructure` 核心包遵循渐进改造原则。
