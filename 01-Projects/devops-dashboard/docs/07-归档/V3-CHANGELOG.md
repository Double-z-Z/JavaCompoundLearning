# V3 变更日志

> 记录从 V3 引入且**当前仍然有效**的设计决策与原则。
> 已被后续版本放弃或延后的决策见 [HISTORY.md](./HISTORY.md)。

---

## 设计决策（仍然有效）

### ADR-021: 聚合根作为 MCP 入口
新增 `DeployPipeline`（编排聚合根）和 `LogAggregate`（日志聚合根）。`deploy_pipeline` 封装完整部署流水线的原子化编排；`env_get_logs` 成为唯一合法日志源。

### ADR-022: 禁止 AI 本地替代
Description 升级为三段式领域契约（【角色标签】+ 技术价值 + 错误示例）。错误响应强制包含 `forbidden` 和 `nextSteps` 字段，在 AI 上下文中植入"禁止本地操作"的心理锚点。

### ADR-023: Environment 状态机精化
引入 READY（环境就绪允许部署）、DEPLOYING（部署锁定中）、ERROR（统一异常态）。移除 STOPPED（直接 DESTROYED）、FAILED（统一为 ERROR）、NOT_FOUND（查询不存在直接返回错误）。支持覆盖部署（RUNNING → DEPLOYING）和修复后重新部署（ERROR → READY）。

### ADR-024: 参数数据源约束
每个参数描述必须包含"数据源指向"（必须来自 XXXX 返回的 YYYY 字段）。可枚举字段必须列出全部 enum 值。减少 AI 编造参数值的可能。

### ADR-025: 服务目录校验
Server 端维护 `ServiceRegistry` 白名单，`env_deploy_service` 的 `serviceName` 必须在注册表中。防止拼写错误和任意镜像部署。

### ADR-026: 错误响应封堵
统一错误响应格式，强制包含 `forbidden`（明确否定本地替代方案）和 `nextSteps`（合法的 MCP 内回退路径）。所有错误统一为整数 code。

### ADR-027: 编排聚合根原子性
`deploy_pipeline` 实现 Saga 模式：任何子步骤失败触发补偿清理。支持 `keepOnFailure` 参数保留失败环境用于排查。

### ADR-028: 日志聚合根闭环
`env_get_logs` 提供与 SSH+docker logs 完全对等的能力（实时/历史/按服务筛选/行数控制/时间范围），外加格式统一、敏感信息脱敏、审计追溯。

### ADR-029: teardown label 批量清理
`teardown()` 在 compose down 之前先按 `devops.env=<envId>` label 查询并 `docker rm -f` 所有关联容器。解决 `docker run` 直接创建的服务容器（`svc-*`）不被 compose down 覆盖的残留问题。

### ADR-030: 服务生命周期迁入 EnvironmentProvisioner
`deployService` / `stopService` / `startService` 从 `EnvironmentServiceImpl` 直接操作 Docker 命令改为通过 `EnvironmentProvisioner` 接口委托。Docker 命令移入 `DockerComposeEnvironment`，容器命名逻辑收敛为 `containerName()` 一处。为 K8s/Ansible 等未来 provisioner 提供统一扩展点。

---

## 接口演进（仍然有效）

### Tool 精简
从 15 个原子化 Tools 精简为 10 个：5 环境类 + 2 测试类 + 2 诊断类 + 1 编排类。

### 参数命名统一
snake_case → camelCase：`env_id` → `envId`，`target_host_id` → `targetHostId`，`resource_limit` → `resourceLimit`。

### 数据源引用方式
`env_create` 的 `hostId` 必须来自 `env_list` 返回的 `availableHosts.id`；`env_deploy_service` 的 `envId` 必须来自 `env_create` 返回；`test_load` 的 `envId` 必须来自 `env_list` 中 RUNNING 状态的环境。

---

## 领域概念（仍然有效）

### DeployPipeline 聚合根
部署领域的首选入口，封装 env_create → env_deploy_service → test_health_check → analyze_network_path 的原子化编排。内部 Saga 协调器管理失败补偿。

### LogAggregate 聚合根
环境日志的唯一合法来源，替代 SSH + docker logs。支持聚合日志、格式统一、敏感信息脱敏。

### Environment 状态机（精化后）
CREATING → READY → DEPLOYING → RUNNING → DESTROYED，异常统一为 ERROR。状态转换必须符合 VALID_TRANSITIONS，DEPLOYING 状态必须持有分布式锁。

---

*本文件只记录思路，具体实现细节见代码与最新设计文档。*
