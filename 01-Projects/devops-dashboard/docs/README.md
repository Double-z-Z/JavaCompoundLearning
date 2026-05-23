# DevOps Dashboard — 文档中心

> **项目定位**: 基于 MCP (Model Context Protocol) 的 AI-Native DevOps 实验平台
> **当前版本**: v2.0 (MCP 化改造中) | **最后更新**: 2026-05-22

---

## 📁 文档结构

```
docs/
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
├── 02-设计/                          # 🏗️ 架构设计（V1+V2 融合）
│   ├── 01-架构决策记录.md            # 全部 ADR：V1 DD(1-10) + V2 ADR-MCP(001-010)
│   ├── 02-领域模型.md                # Host / Environment / Experiment / Evidence
│   └── 03-整体架构.md                # 分层架构图 + 数据流 + 部署模式
│
├── 03-接口契约/                      # 🔌 接口定义（双轨：REST + MCP）
│   ├── 01-REST-API.md                # 现有 REST API 使用指南（仍有效，双轨运行）
│   ├── 02-MCP-Tools.md               # MCP Tool 完整 JSON Schema 定义
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

| 维度 | V1 (已完成) | V2 (进行中) |
|------|------------|------------|
| **交互方式** | Swagger UI + 手写 JSON/YAML | 自然语言 → AI → MCP Tools |
| **核心能力** | 环境 CRUD + 实验 CRUD | Host 管理 + 远程压测 + 证据收集 |
| **基础设施认知** | 扁平节点列表 | 层次化拓扑 (PVE → VM → Docker) |
| **部署模型** | 单机 Docker Compose | 多角色分离 (MCP/Target/Loadgen) |
| **前端** | Vue 3（未实现） | 无需前端，AI 即界面 |

**一句话**: 你对 AI 说 "验证 Nacos 1000 QPS"，AI 自动完成环境搭建→服务部署→健康检查→远程压测→证据收集→报告生成。你只需做选择题和确认。

---

## 📖 阅读顺序

### 第一次打开项目

1. [01-入门/00-项目概览.md](./01-入门/00-项目概览.md) → 了解项目全貌
2. [02-设计/03-整体架构.md](./02-设计/03-整体架构.md) → 理解分层架构
3. [02-设计/02-领域模型.md](./02-设计/02-领域模型.md) → 理解核心业务概念

### 开始编码前

4. [04-实施计划/01-迁移路线图.md](./04-实施计划/01-迁移路线图.md) → 查看 Phase 任务
5. [04-实施计划/02-当前任务清单.md](./04-实施计划/02-当前任务清单.md) → 当前可执行任务
6. [03-接口契约/03-Java-接口定义.md](./03-接口契约/03-Java-接口定义.md) → 接口定义参考

### 编码时参考

| 需求 | 参考文档 |
|------|---------|
| 写 MCP Handler | [03-接口契约/02-MCP-Tools.md](./03-接口契约/02-MCP-Tools.md) |
| 写领域对象 | [02-设计/02-领域模型.md](./02-设计/02-领域模型.md) |
| 部署中间件 | [05-参考/01-服务模板.md](./05-参考/01-服务模板.md) |
| 配置 hosts.yml | [05-参考/03-配置模板.md](./05-参考/03-配置模板.md) |
| 配置 Trae MCP | [06-运维/01-Trae配置与运维指南.md](./06-运维/01-Trae配置与运维指南.md) |

---

## 🔑 核心设计原则

| 原则 | 一句话 | 来源 |
|------|--------|------|
| **保守交互** | AI 只做向导不做决策；每步写操作需用户确认 | V2 ADR-002 |
| **原子工具** | 一 Tool 一职责，禁止粗粒度"一键完成"封装 | V2 ADR-005 |
| **发现优先** | 先查 Resources 再呈现选项，不基于静态知识推断 | V2 ADR-002 |
| **角色分离** | MCP Host / Target Host / Loadgen Host 三者显式区分 | V2 ADR-003 |
| **层次拓扑** | PVE → VM → Docker 父子关系必须表达 | V2 ADR-004 |
| **网络感知** | 压测前分析路径类型，提示结果可信度 | V2 ADR-006 |
| **响应式保持** | WebFlux 非阻塞，流式日志支持背压 | V2 ADR-009 + V1 DD-7 |
| **渐进改造** | MCP 是附加层，不破坏现有 REST API | V2 ADR-010 |
| **聚合根优先** | Environment / Experiment / Host 是核心业务实体 | V1 DD-1,2 |
| **插件化基础设施** | Docker Compose 可互换 K8s | V1 DD-4 |

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
| **V2 Phase 1** | Host 层 + MCP Server 骨架 | ⏳ 待开始 | 下一步目标 |
| **V2 Phase 2** | 环境 MCP 化 | ⏳ 待开始 | — |
| **V2 Phase 3** | 测试工具 MCP 化 | ⏳ 待开始 | — |
| **V2 Phase 4** | 实验会话与证据 | ⏳ 待开始 | — |
| **V2 Phase 5** | Prompt 优化 | ⏳ 待开始 | — |

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

## 📋 V2 蓝图索引

完整的 V2 原始设计文档保留在 [V2/](./V2/) 目录，按依赖关系阅读：

1. [V2/01-设计决策与原则.md](./V2/01-设计决策与原则.md) → 为什么做（10 条 ADR）
2. [V2/02-整体设计.md](./V2/02-整体设计.md) → 做什么（架构 + 概念）
3. [V2/03-各层次设计.md](./V2/03-各层次设计.md) → 怎么做（领域设计 + 时序图）
4. [V2/04-具体接口实现.md](./V2/04-具体接口实现.md) → 接口细节（JSON Schema + Java 代码）
5. [V2/05-改造方案.md](./V2/05-改造方案.md) → 怎么迁移（Phase 计划 + 文件清单）
6. [V2/06-Trae配置与启停指南.md](./V2/06-Trae配置与启停指南.md) → 怎么运维
