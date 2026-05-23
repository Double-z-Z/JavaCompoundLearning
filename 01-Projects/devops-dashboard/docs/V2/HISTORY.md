# V2 历史记录

> 记录 V2 引入但**已被后续版本放弃**的设计思路。
> 仅保留思路概要，不记录具体细节。

---

## 已放弃的设计决策

### 原子化 Tool 设计（ADR-005）
**思路**: 所有 MCP Tools 保持原子化、单一职责，禁止粗粒度封装。MCP Server 只提供"积木"，不组装"模型"。
**放弃原因**: AI 在需要完整部署流程时倾向于手动拆解步骤，甚至绕过 MCP 在本地执行 docker/ssh。V3 改为聚合根入口（deploy_pipeline）提供原子性保障与语义引导。

### Environment 6 状态机
**思路**: CREATING → RUNNING → STOPPED → DESTROYED，异常分支 FAILED / NOT_FOUND。
**放弃原因**: RUNNING 无法表达"环境空壳已好但未部署"的中间态；FAILED 语义模糊（创建失败？部署失败？）；NOT_FOUND 作为状态不合理。V3 精化为 CREATING → READY → DEPLOYING → RUNNING → DESTROYED，异常统一为 ERROR。

### 显式指定压测端与目标端
**思路**: `test_load` 必须显式传入 `loadgen_host_id` 和 `target_url`。
**放弃原因**: AI 容易编造 host_id 和 URL。V3 改为通过 `envId` + `targetPort` 定位，由 MCP Server 自动调度压测端。

### 独立日志工具 test_stream_logs
**思路**: `test_stream_logs` 作为测试诊断工具之一，按 service_name 获取日志。
**放弃原因**: 定位模糊（`test_` 前缀暗示"测试用"），能力不完整。V3 由 `env_get_logs` 替代，提升为日志聚合根，支持全环境聚合、格式统一、敏感信息脱敏。

### 独立证据收集工具 test_collect_metrics
**思路**: `test_collect_metrics` 作为独立 Tool，由 AI 显式调用收集监控指标。
**放弃原因**: 增加 AI 调用负担，且容易遗漏。V3 改为 Monitoring 自动采集，不暴露为独立 Tool。

### 实验会话直接暴露为 Tools
**思路**: `session_create`、`session_record_evidence`、`session_conclude` 作为独立 MCP Tools。
**放弃原因**: V3 聚焦核心部署与测试闭环，实验会话功能延后到 V3+。

### env_get_access 独立工具
**思路**: `env_get_access` 作为独立 Tool 返回环境访问端点。
**放弃原因**: 功能过于单薄，V3 由 `deploy_pipeline` 内部返回访问信息。

---

## 已演进的命名规范

- `target_host_id` / `loadgen_host_id` / `env_id` 等 snake_case → camelCase
- `template_id` → `serviceName`（从模板标识符演进为服务目录名）

---

*本文件只记录思路，具体实现细节已归档至代码历史。*
