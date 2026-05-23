# V2 变更日志

> 记录从 V2 引入且**当前仍然有效**的设计决策与原则。
> 已被后续版本放弃的决策见 [HISTORY.md](./HISTORY.md)。

---

## 设计决策（仍然有效）

### ADR-001: MCP 协议作为 AI 交互层
引入 MCP Server 层，作为 AI Agent 与 DevOps 能力的标准化交互协议。MCP 作为 AI 交互的唯一协议逐步替代旧有接口。

### ADR-002: 保守式交互
AI 角色限定为"交互式向导"，所有写操作必须经过用户显式确认。先查 Resources 获取真实状态，再呈现选项。

### ADR-003: 三层角色分离
Host 角色模型：MCP_HOST（运行 Server）、TARGET（承载环境）、LOADGEN（执行压测）。`env_create` 必须绑定 TARGET，`test_load` 必须独立 LOADGEN。

### ADR-004: 层次化主机拓扑
Host 引入 `parent_id` 构建树形拓扑，表达 PVE → VM 嵌套关系。`analyze_network_path` 基于拓扑推理路径类型。

### ADR-006: 网络路径感知
压测前进行网络拓扑分析，区分 same-host / same-hypervisor / same-lan / wan，提示压测可信度。

### ADR-007: 证据收集
分层证据机制：指标自动采、功能验证自动采、人工观察点用户确认。证据必须关联 env_id 确保可追溯。

### ADR-009: WebFlux 响应式保持
MCP Server 层继续使用 WebFlux，所有异步操作返回 Mono/Flux。远程命令、日志流全部非阻塞。

### ADR-010: 渐进改造
MCP Server 可分独立进程（8081）或同进程（8080/mcp）部署。

---

## 领域概念（仍然有效）

### Host 聚合根
基础设施节点，高于 Environment。身份标识 HostId，属性包括 type、roles、capabilities、networkZone、resources、access。

### Environment 聚合根（状态已精化，见 V3 CHANGELOG）
运行空间生命周期管理。绑定到 Target Host，包含 ServiceInstance 列表、ResourceQuota、LifecyclePolicy。

### ExperimentSession 聚合根（Tool 暴露延后，见 V3 HISTORY）
实验全流程管理与证据聚合。包含 intent、evidenceLog、conclusion。

### Evidence 聚合根（Tool 暴露延后，见 V3 HISTORY）
结构化证据存储。分类：METRIC、ARTIFACT、OBSERVATION、CHECK_RESULT。

---

## 接口风格（已演进，见 V3 CHANGELOG）

- snake_case 参数命名 → V3 改为 camelCase
- `template_id` → V3 演进为 `serviceName`
- 15 个原子化 Tools → V3 精简为 10 个，增加聚合根入口

---

*本文件只记录思路，具体实现细节见代码与最新设计文档。*
