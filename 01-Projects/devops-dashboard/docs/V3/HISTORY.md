# V3 历史记录

> 记录 V3 引入但**已被放弃或延后**的设计思路。
> 仅保留思路概要，不记录具体细节。

---

## 已放弃的设计决策

### type 语义迁移
**思路**: V2 的 `type` 表示环境业务类型（DEV/TEST/EXPERIMENT），V3 计划将其改为运行时隔离类型（docker/native），由 `type` 承担原 `runtime` 的分类职责。
**放弃原因**: 与领域模型中 `EnvironmentType` 枚举（DEV/TEST/STAGING/PROD/EXPERIMENT）产生严重冲突。`type` 一词已在 Host 上下文表示物理形态（pve-hypervisor/vm），在 Environment 上下文同时表示业务用途和运行时隔离会导致统一语言崩塌。最新设计恢复了清晰的语义边界：`type` 在 Host 中表示物理形态，在 Environment 中由专门的词汇表达不同概念。

### runtime 作为版本约束
**思路**: V3 计划将 `runtime` 从运行时类型枚举（docker/native）改为版本约束字符串（如 `docker:26.0`、`openjdk:21-jre-slim`）。
**放弃原因**: 与 `RuntimeType` 枚举（DOCKER/NATIVE）的职责冲突。运行时"是什么"（分类）和"具体要求"（版本）是两个不同的领域概念，不应挤在同一个字段中。最新设计拆分为两个独立概念。

### 完全独立的 LogAggregate 领域包
**思路**: 在 `domain/mcp/` 下建立独立的 `LogAggregate` 领域对象，与 Environment 聚合根平级。
**状态**: 设计概念保留，但作为完整领域包的实现被简化。当前 `env_get_logs` 主要由 Handler 层实现格式统一和脱敏，未形成独立的聚合根领域包。

---

## 已延后的设计决策

### ExperimentSession 聚合根 Tool 暴露
**思路**: V2 的 `session_create`、`session_record_evidence`、`session_conclude` 在 V3 中计划延后。
**状态**: 继续延后。V3 聚焦核心部署与测试闭环，实验会话的完整生命周期管理（PLANNING → PROVISIONING → ... → CONCLUDED → ARCHIVED）尚未以 MCP Tool 形式暴露。

### Evidence 聚合根 Tool 暴露
**思路**: `submit_evidence_note` 等证据录入工具。
**状态**: 继续延后。证据收集目前由 `test_health_check`、`test_load` 等工具内部自动完成，尚未暴露独立的证据管理接口。

---

## 已演进的命名规范

- `serviceName`（Tool Schema）与 `templateName`（DTO/领域模型）的命名分歧。最新设计统一为 `serviceName` 作为外部契约词汇，`templateName` 作为内部兼容概念在防腐层做映射。
- `envType`（deploy_pipeline 参数）与 `type`（env_create 参数）的同义异名。最新设计统一词汇。

---

*本文件只记录思路，具体实现细节已归档至代码历史。*
