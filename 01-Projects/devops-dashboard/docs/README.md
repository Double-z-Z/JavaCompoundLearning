# DevOps Dashboard — 文档中心

> **项目定位**: 基于 MCP (Model Context Protocol) 的 AI-Native DevOps 实验平台
> **当前版本**: v2.0 (MCP 化改造中) | **最后更新**: 2026-05-22

---

## 📁 文档结构

```
docs/
├── V3/                              # V3 设计蓝图（聚合根入口 + 反退化约束）
│   ├── 00-V3概览与阅读指南.md         # V3 定位、与 V2 关系、阅读顺序
│   ├── 01-设计决策记录.md             # ADR-021~028（新增决策）
│   ├── 02-领域模型改造.md             # DeployPipeline / LogAggregate / 状态机精化
│   ├── 03-接口契约.md                 # 10 个 Tools 完整 JSON Schema（三段式）
│   ├── 04-边界约束规范.md             # 描述规范、错误响应、服务目录校验
│   ├── 05-实施计划.md                 # V3 改造任务清单
│   └── 07-V2-V3差异对照表.md          # 工具/状态机/参数变更速查
│
├── V2/                              # V2 设计蓝图（原始设计文档，不修改）
│   ├── 01-设计决策与原则.md          # 10 条 ADR-MCP 架构决策
│   ├── 02-整体设计.md                # 架构全景 + 核心概念
│   ├── 03-各层次设计.md              # 领域层详细设计 + 时序图
│   ├── 04-具体接口实现.md            # MCP Tool JSON Schema + Handler 代码
│   ├── 05-改造方案.md                # Phase 1~5 迁移计划
│   └── 06-Trae配置与启停指南.md       # 运维操作手册
│
├── 01-入门/                          # 📖 新手必读
│   ├── 00-项目概览.md                # 项目定位、设计原则、当前状态
│   └── 01-快速启动.md                # 从零到 MCP 连接的完整流程
│
├── 02-设计/                          # 🏗️ 架构设计（V1+V2+V3 融合）
│   ├── 01-架构决策记录.md            # 全部 ADR：V1 DD(1-10) + V2(11-20) + V3(21-28)
│   ├── 02-领域模型.md                # Host / Environment / Experiment / Evidence + V3 聚合根
│   └── 03-整体架构.md                # 分层架构图 + 数据流 + 部署模式
│
├── 03-接口契约/                      # 🔌 接口定义（双轨：REST + MCP）
│   ├── 01-REST-API.md                # 现有 REST API 使用指南（仍有效，双轨运行）
│   ├── 02-MCP-Tools.md               # MCP Tool JSON Schema 定义（V2 + V3 标注）
│   └── 03-Java-接口定义.md           # 核心 Java Interface 定义
│
├── 04-实施计划/                      # 📋 执行路线
│   ├── 01-迁移路线图.md              # Phase 1~5 改造计划与验收标准
│   └── 02-当前任务清单.md            # 可勾选的执行 checklist
│
├── 05-参考/                          # 📚 详细参考
│   ├── 01-服务模板.md                # Nacos/RabbitMQ/MySQL/Redis 模板库
│   ├── 02-数据结构.md                # 完整 YAML Schema（Host/Env/Session/Evidence）
│   └── 03-配置模板.md                # hosts.yml + application-mcp.yml
│
├── 06-运维/                          # 🔧 运维操作
│   └── 01-Trae配置与运维指南.md       # Trae/Claude MCP 配置 + 启停 + 排查
│
└── 07-归档/                          # 📦 历史文档（不再维护，供参考）
    ├── Phase1完成报告.md             # V1 Phase 1 完成总结
    ├── 变更日志.md                   # V1 版本变更历史
    └── 流水线设计.md                 # V1 Pipeline 设计（V2 未采纳）
```

---

## 🎯 项目核心定位

DevOps Dashboard 正在从 **传统 REST API 平台** 进化为 **AI-Native 的 DevOps 实验平台**：

| 维度 | V1 (已完成) | V2 (已完成) | V3 (设计中) |
|------|------------|------------|------------|
| **交互方式** | Swagger UI + 手写 JSON/YAML | 自然语言 → AI → MCP Tools | 自然语言 → AI → MCP 聚合根入口 |
| **核心能力** | 环境 CRUD + 实验 CRUD | Host 管理 + 远程压测 + 证据收集 | 部署流水线编排 + 反退化约束 |
| **基础设施认知** | 扁平节点列表 | 层次化拓扑 (PVE → VM → Docker) | 层次化拓扑 + 状态机精化 |
| **部署模型** | 单机 Docker Compose | 多角色分离 (MCP/Target/Loadgen) | 多角色分离 + 聚合根编排 |
| **AI 约束** | 无 | 保守式交互（发现优先、逐步确认） | 唯一合法路径（禁止本地替代） |
| **前端** | Vue 3（未实现） | 无需前端，AI 即界面 | 无需前端，AI 即界面 |

**V2 一句话**: 你对 AI 说 "验证 Nacos 1000 QPS"，AI 自动完成环境搭建→服务部署→健康检查→远程压测→证据收集→报告生成。

**V3 一句话**: AI 无法绕过 MCP 进行本地 docker/ssh/curl 操作，所有路径必须经过聚合根入口，错误响应封堵替代方案。

---

## 📖 阅读顺序

### 第一次打开项目

1. [01-入门/00-项目概览.md](./01-入门/00-项目概览.md) → 了解项目全貌
2. [02-设计/03-整体架构.md](./02-设计/03-整体架构.md) → 理解分层架构
3. [02-设计/02-领域模型.md](./02-设计/02-领域模型.md) → 理解核心业务概念

### 了解 V3 设计（当前重点）

4. [V3/00-V3概览与阅读指南.md](./V3/00-V3概览与阅读指南.md) → V3 核心目标与变化
5. [V3/01-设计决策记录.md](./V3/01-设计决策记录.md) → V3 的 8 条 ADR
6. [V3/03-接口契约.md](./V3/03-接口契约.md) → V3 的 10 个 Tools

### 开始编码前

7. [04-实施计划/01-迁移路线图.md](./04-实施计划/01-迁移路线图.md) → 查看 Phase 任务
8. [V3/05-实施计划.md](./V3/05-实施计划.md) → V3 改造任务清单
9. [03-接口契约/03-Java-接口定义.md](./03-接口契约/03-Java-接口定义.md) → 接口定义参考

### 编码时参考

| 需求 | 参考文档 |
|------|---------|
| 写 MCP Handler (V3) | [V3/03-接口契约.md](./V3/03-接口契约.md) |
| 写 MCP Handler (V2) | [03-接口契约/02-MCP-Tools.md](./03-接口契约/02-MCP-Tools.md) |
| 写领域对象 | [02-设计/02-领域模型.md](./02-设计/02-领域模型.md) + [V3/02-领域模型改造.md](./V3/02-领域模型改造.md) |
| 部署中间件 | [05-参考/01-服务模板.md](./05-参考/01-服务模板.md) |
| 配置 hosts.yml | [05-参考/03-配置模板.md](./05-参考/03-配置模板.md) |
| 配置 Trae MCP | [06-运维/01-Trae配置与运维指南.md](./06-运维/01-Trae配置与运维指南.md) |

---

## 🔑 核心设计原则

| 原则 | 一句话 | 来源 |
|------|--------|------|
| **保守交互** | AI 只做向导不做决策；每步写操作需用户确认 | V2 ADR-012 |
| **原子工具** | 一 Tool 一职责，禁止粗粒度"一键完成"封装 | V2 ADR-015 |
| **发现优先** | 先查 Resources 再呈现选项，不基于静态知识推断 | V2 ADR-012 |
| **角色分离** | MCP Host / Target Host / Loadgen Host 三者显式区分 | V2 ADR-013 |
| **层次拓扑** | PVE → VM → Docker 父子关系必须表达 | V2 ADR-014 |
| **网络感知** | 压测前分析路径类型，提示结果可信度 | V2 ADR-016 |
| **响应式保持** | WebFlux 非阻塞，流式日志支持背压 | V2 ADR-019 + V1 DD-7 |
| **渐进改造** | MCP 是附加层，不破坏现有 REST API | V2 ADR-020 |
| **聚合根优先** | Environment / Experiment / Host 是核心业务实体 | V1 DD-1,2 |
| **插件化基础设施** | Docker Compose 可互换 K8s | V1 DD-4 |
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
| **V3 设计** | 聚合根入口 + 反退化约束 | 📝 进行中 | 设计文档编写中 |
| **V3 Phase A** | 新增领域对象 | ⏳ 待开始 | DeployPipeline + LogAggregate |
| **V3 Phase B** | 状态机改造 | ⏳ 待开始 | READY/DEPLOYING/ERROR |
| **V3 Phase C** | Handlers 重写 | ⏳ 待开始 | 10 个 Tools |
| **V3 Phase D** | 错误响应封堵 | ⏳ 待开始 | forbidden + nextSteps |

### 已有代码统计

| 指标 | 数值 |
|------|------|
| Java 源文件 | ~70 |
| 测试用例 | 58（全部通过） |
| REST API 端点 | 12 |
| 设计决策 (ADR) | 20 条（V1: 10 + V2: 10） |
| MCP Tools（设计中） | 15 |
| MCP Resources（设计中） | 4 |

---

## 🔄 V1 → V2 演进关系

```
V1 (REST API 平台)                    V2 (AI-Native 平台)
=================                    ==================
用户 → Swagger UI → REST API         用户 → AI (Trae/Claude) → MCP Tools → REST API
                                    ↓
                           新增: Host 层 / Loadgen 层 / Evidence 层
                           新增: 网络路径分析 / 保守式交互向导
                           保留: Environment / Experiment / 服务模板
```

**关键**: V2 不是推翻重写，而是在 V1 之上**附加 MCP 层**。现有 `domain`、`infrastructure` 核心包不修改。

---

## 📋 V3 蓝图索引

V3 设计文档位于 [V3/](./V3/) 目录：

1. [V3/00-V3概览与阅读指南.md](./V3/00-V3概览与阅读指南.md) → V3 定位与核心变化
2. [V3/01-设计决策记录.md](./V3/01-设计决策记录.md) → 为什么做 V3（8 条 ADR）
3. [V3/02-领域模型改造.md](./V3/02-领域模型改造.md) → 新增/改造了哪些聚合根
4. [V3/03-接口契约.md](./V3/03-接口契约.md) → 10 个 Tools 的完整 Schema
5. [V3/04-边界约束规范.md](./V3/04-边界约束规范.md) → description 三段式 + 错误响应格式
6. [V3/05-实施计划.md](./V3/05-实施计划.md) → V3 改造任务清单
7. [V3/07-V2-V3差异对照表.md](./V3/07-V2-V3差异对照表.md) → 从 V2 迁移时的变更速查

## 📋 V2 蓝图索引

完整的 V2 原始设计文档保留在 [V2/](./V2/) 目录，按依赖关系阅读：

1. [V2/01-设计决策与原则.md](./V2/01-设计决策与原则.md) → 为什么做（10 条 ADR）
2. [V2/02-整体设计.md](./V2/02-整体设计.md) → 做什么（架构 + 概念）
3. [V2/03-各层次设计.md](./V2/03-各层次设计.md) → 怎么做（领域设计 + 时序图）
4. [V2/04-具体接口实现.md](./V2/04-具体接口实现.md) → 接口细节（JSON Schema + Java 代码）
5. [V2/05-改造方案.md](./V2/05-改造方案.md) → 怎么迁移（Phase 计划 + 文件清单）
6. [V2/06-Trae配置与启停指南.md](./V2/06-Trae配置与启停指南.md) → 怎么运维
