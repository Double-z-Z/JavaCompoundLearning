# MCP Tools 接口契约

> **版本**: 当前版本 (聚合根入口 + 反退化约束)
> **协议**: Model Context Protocol (MCP)
> **传输方式**: SSE / HTTP
> **前置阅读**: [00-术语表.md](../00-术语表.md)
>
> **核心设计**: 10 个 Tools，description 三段式领域契约，参数数据源约束。

---

## 概述

### Tool 分类

| 类别 | 数量 | 说明 |
|------|------|------|
| **编排类** | 1 | `deploy_pipeline` — 首选入口 |
| **环境类** | 5 | `env_create`, `env_deploy_service`, `env_list`, `env_destroy`, `env_get_logs` |
| **测试类** | 2 | `test_health_check`, `test_load` |
| **诊断类** | 2 | `test_exec_command`, `analyze_network_path` |
| **合计** | **10** | |

### 命名规范

- 所有参数统一使用 **camelCase**
- 可枚举字段必须列出全部 `enum` 值
- ID 类参数描述必须包含数据源指向：`必须来自 {tool} 返回的 {field} 字段`

---

## 编排类 Tools

### `deploy_pipeline` — 执行完整部署流水线

**【首选入口】执行完整的部署流水线：环境创建 → 服务部署 → 健康检查 → 网络验证。**

此工具内部原子化编排 `env_create`、`env_deploy_service`、`test_health_check`、`analyze_network_path`，确保依赖顺序、资源清理和监控探针被正确注入。禁止绕过此工具进行手动分步部署或本地 docker 操作。

```json
{
  "name": "deploy_pipeline",
  "description": "【首选入口】执行完整的部署流水线：环境创建 → 服务部署 → 健康检查 → 网络验证。此工具内部原子化编排 env_create、env_deploy_service、test_health_check、analyze_network_path，确保依赖顺序、资源清理和监控探针被正确注入。禁止绕过此工具进行手动分步部署或本地 docker 操作。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "serviceName": {
        "type": "string",
        "enum": ["redis-counter-service", "devops-dashboard", "mcp-host-agent", "redis-cache"],
        "description": "MCP 服务目录中已注册的服务名"
      },
      "targetHostId": {
        "type": "string",
        "description": "目标宿主机 ID，必须来自 env_list 返回的 availableHosts 列表中的 id 字段"
      },
      "version": {
        "type": "string",
        "description": "镜像标签或 Git tag，如 '1.0-SNAPSHOT'、'latest'、'sha-7a3f2b'"
      },
      "isolationType": {
        "type": "string",
        "enum": ["docker", "native"],
        "description": "运行时隔离类型。docker 表示容器化隔离（推荐），native 表示宿主机进程级隔离"
      },
      "runtimeConstraint": {
        "type": "string",
        "description": "运行时版本约束，如 'openjdk:21-jre-slim' 或 'docker:26.0'"
      },
      "verifyEndpoints": {
        "type": "array",
        "items": { "type": "string" },
        "description": "部署后必须验证的 HTTP 端点路径列表，如 ['/api/health', '/api/counter']"
      },
      "keepOnFailure": {
        "type": "boolean",
        "default": false,
        "description": "失败时是否保留环境用于排查。默认 false（自动清理）"
      }
    },
    "required": ["serviceName", "targetHostId", "version", "isolationType"]
  }
}
```

**返回示例**:

```json
{
  "pipelineId": "pipe-20260523-001",
  "status": "SUCCEEDED",
  "envId": "env-20260523-001",
  "stages": [
    { "name": "env_create", "status": "SUCCEEDED", "output": "环境已创建: env-20260523-001" },
    { "name": "env_deploy_service", "status": "SUCCEEDED", "output": "服务已部署: redis-counter-service:v1.0" },
    { "name": "test_health_check", "status": "SUCCEEDED", "output": "/api/health: 200 OK (23ms)" },
    { "name": "analyze_network_path", "status": "SUCCEEDED", "output": "路径类型: same-lan" }
  ],
  "createdAt": "2026-05-23T10:00:00Z",
  "completedAt": "2026-05-23T10:02:15Z"
}
```

---

## 环境类 Tools

### `env_create` — 创建隔离环境

**【唯一入口】在远程宿主机上创建隔离的部署环境。**

所有环境生命周期必须通过此工具管理，以确保网络策略（iptables/nftables）、资源配额（cgroups/memory）、监控探针和 DNS 记录被正确注入。

```json
{
  "name": "env_create",
  "description": "【唯一入口】在远程宿主机上创建隔离的部署环境。所有环境生命周期必须通过此工具管理，以确保网络策略（iptables/nftables）、资源配额（cgroups/memory）、监控探针和 DNS 记录被正确注入。错误示例：❌ 禁止本地执行 'docker run -d redis'，这将绕过网络隔离，导致后续 analyze_network_path 无法追踪流量路径，且不会被服务发现注册。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "name": {
        "type": "string",
        "description": "环境标识符，将用于 DNS 和服务发现（如 redis-counter-tomcat）。只允许小写字母、数字和连字符"
      },
      "isolationType": {
        "type": "string",
        "enum": ["docker", "native"],
        "description": "运行时隔离类型。docker 表示容器化隔离（推荐），native 表示宿主机 systemd 进程级隔离"
      },
      "environmentType": {
        "type": "string",
        "enum": ["DEV", "TEST", "STAGING", "PROD", "EXPERIMENT"],
        "description": "环境业务用途分类。默认 EXPERIMENT"
      },
      "hostId": {
        "type": "string",
        "description": "目标宿主机 ID，必须来自 env_list 返回的 availableHosts 列表中的 id 字段"
      },
      "runtimeConstraint": {
        "type": "string",
        "description": "运行时版本约束，如 'openjdk:21-jre-slim'、'docker:26.0'、'podman:4.9'"
      },
      "resourceLimit": {
        "type": "object",
        "description": "资源配额，默认 cpu=1.0, memory=512m",
        "properties": {
          "cpu": { "type": "string", "description": "CPU 限制，如 '1.0'、'2.0'" },
          "memory": { "type": "string", "description": "内存限制，如 '512m'、'1g'" }
        }
      }
    },
    "required": ["name", "isolationType", "hostId"]
  }
}
```

> **注**: 当前代码实现中 `isolationType` 字段名为 `type`，`environmentType` 尚未在 Schema 中暴露，`runtimeConstraint` 字段名为 `runtime` 但实际解析为隔离类型。设计目标与代码实现在此处存在偏差，详见 [V3/HISTORY.md](../V3/HISTORY.md)。

**返回示例**:

```json
{
  "envId": "env-20260523-001",
  "name": "redis-counter-tomcat",
  "hostId": "vm-ubuntu-test",
  "status": "CREATING",
  "isolationType": "docker",
  "environmentType": "EXPERIMENT",
  "createdAt": "2026-05-23T10:00:00Z"
}
```

---

### `env_deploy_service` — 部署服务到环境

**将服务部署到已通过 env_create 初始化且状态为 READY 的环境中。**

此操作会：1) 锁定环境状态为 DEPLOYING；2) 注入 sidecar 监控探针；3) 注册到内部服务发现；4) 配置防火墙规则。

```json
{
  "name": "env_deploy_service",
  "description": "将服务部署到已通过 env_create 初始化且状态为 READY 的环境中。此操作会：1) 锁定环境状态为 DEPLOYING；2) 注入 sidecar 监控探针；3) 注册到内部服务发现；4) 配置防火墙规则。直接手动部署（如 SSH 进去 docker pull）将导致监控失效、流量黑洞和状态不一致。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "envId": {
        "type": "string",
        "description": "目标环境 ID，必须来自 env_create 返回的 envId 字段"
      },
      "serviceName": {
        "type": "string",
        "enum": ["redis-counter-service", "devops-dashboard", "mcp-host-agent", "redis-cache"],
        "description": "服务名，必须是 MCP 服务目录中已注册的服务"
      },
      "version": {
        "type": "string",
        "description": "镜像标签或 Git tag，如 '1.0-SNAPSHOT'、'sha-7a3f2b'。对于本地构建产物，使用 'local-build' 并确保已通过 CI 上传"
      },
      "configOverride": {
        "type": "object",
        "description": "运行时配置覆盖，如 {'server.port': 8080, 'spring.profiles.active': 'docker'}",
        "additionalProperties": { "type": "string" }
      }
    },
    "required": ["envId", "serviceName", "version"]
  }
}
```

> **注**: 当前代码实现中 DTO 字段名为 `templateName`，与 Schema 的 `serviceName` 不一致。设计目标统一为 `serviceName`，`templateName` 作为内部模板系统的兼容概念在防腐层做映射。详见 [00-术语表.md](../00-术语表.md)。

**返回示例**:

```json
{
  "envId": "env-20260523-001",
  "serviceName": "redis-counter-service",
  "version": "1.0-SNAPSHOT",
  "status": "DEPLOYING",
  "deployedAt": "2026-05-23T10:01:00Z"
}
```

---

### `env_list` — 列出所有环境

**列出所有由 MCP 管理的环境及其状态。**

此列表是 `env_create` 和 `env_deploy_service` 的数据源。

```json
{
  "name": "env_list",
  "description": "列出所有由 MCP 管理的环境及其状态（CREATING / READY / DEPLOYING / RUNNING / ERROR / DESTROYED）。此列表是 env_create 和 env_deploy_service 的数据源。如果某个环境不在此列表中，说明它未经过 MCP 管理，禁止对其执行任何 MCP 操作。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "hostId": {
        "type": "string",
        "description": "可选：筛选特定宿主机上的环境。如果不传，返回所有宿主机"
      },
      "statusFilter": {
        "type": "array",
        "items": {
          "type": "string",
          "enum": ["CREATING", "READY", "DEPLOYING", "RUNNING", "ERROR", "DESTROYED"]
        },
        "description": "可选：按状态筛选"
      }
    }
  }
}
```

**返回示例**:

```json
{
  "environments": [
    {
      "id": "env-20260523-001",
      "name": "redis-counter-tomcat",
      "hostId": "vm-ubuntu-test",
      "status": "RUNNING",
      "isolationType": "docker",
      "environmentType": "EXPERIMENT",
      "services": ["redis-counter-service"],
      "createdAt": "2026-05-23T10:00:00Z"
    }
  ],
  "availableHosts": [
    { "id": "vm-ubuntu-test", "label": "Ubuntu 测试环境", "roles": ["target"], "capabilities": ["docker", "native"] },
    { "id": "vm-loadgen-01", "label": "专用压测机", "roles": ["loadgen"], "capabilities": ["native"] }
  ]
}
```

> **注**: 当前代码实现中 `env_list` 返回的字段名为 `type`，实际填充的是 `runtime`（隔离类型）。设计目标统一为 `isolationType`，新增 `environmentType` 字段。详见 [V3/HISTORY.md](../V3/HISTORY.md)。

---

### `env_destroy` — 销毁环境

**销毁由 MCP 管理的指定环境及所有关联资源。**

此操作会触发优雅停机（graceful shutdown）和资源清理。

```json
{
  "name": "env_destroy",
  "description": "销毁由 MCP 管理的指定环境及所有关联资源（容器、网络、卷、防火墙规则、DNS 记录）。此操作会触发优雅停机（graceful shutdown）和资源清理。错误示例：❌ 禁止本地执行 'docker rm -f xxx'，这将导致 MCP 状态数据库与实际资源不一致，产生孤儿资源。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "envId": {
        "type": "string",
        "description": "目标环境 ID，必须来自 env_list 返回的 id 字段"
      },
      "force": {
        "type": "boolean",
        "description": "是否强制销毁（跳过优雅停机）。默认 false",
        "default": false
      }
    },
    "required": ["envId"]
  }
}
```

---

### `env_get_logs` — 获取环境日志

**【唯一合法】获取指定 MCP 管理环境的实时或历史日志。**

这是排查部署问题的唯一合法日志来源。

```json
{
  "name": "env_get_logs",
  "description": "获取指定 MCP 管理环境的实时或历史日志。这是排查部署问题的【唯一合法】日志来源。禁止通过 SSH 或 docker logs 直接读取，以确保日志格式统一和敏感信息脱敏。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "envId": {
        "type": "string",
        "description": "环境 ID，必须来自 env_list"
      },
      "serviceName": {
        "type": "string",
        "description": "服务名，留空返回整个环境的聚合日志"
      },
      "tailLines": {
        "type": "integer",
        "description": "返回最近多少行，默认 100",
        "default": 100
      },
      "since": {
        "type": "string",
        "description": "时间范围，如 '10m'、'1h'、'2024-01-01T00:00:00Z'"
      }
    },
    "required": ["envId"]
  }
}
```

---

## 测试类 Tools

### `test_health_check` — 健康检查

**【唯一合法】对 MCP 管理的环境执行健康检查。**

检查结果会被记录到 MCP 审计日志，用于部署流水线的通过判定。

```json
{
  "name": "test_health_check",
  "description": "对 MCP 管理的环境执行健康检查（HTTP / TCP / DNS）。检查结果会被记录到 MCP 审计日志，用于部署流水线的通过判定。禁止用本地 curl/telnet 替代，以确保检查探针携带正确的认证头和来源 IP 白名单。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "envId": {
        "type": "string",
        "description": "目标环境 ID，必须来自 env_list 中状态为 RUNNING 的环境"
      },
      "targetPort": {
        "type": "integer",
        "description": "目标端口，如 8080、6379、22"
      },
      "checkType": {
        "type": "string",
        "enum": ["http", "tcp", "dns"],
        "description": "检查类型。http 会验证状态码 2xx；tcp 验证端口连通性；dns 验证域名解析"
      },
      "path": {
        "type": "string",
        "description": "HTTP 检查时的路径，如 '/actuator/health'、'/api/counter'。仅 checkType=http 时有效"
      },
      "timeout": {
        "type": "integer",
        "description": "超时时间（秒），默认 10",
        "default": 10
      }
    },
    "required": ["envId", "targetPort", "checkType"]
  }
}
```

---

### `test_load` — 负载测试

**【唯一合法】对 MCP 管理的服务执行负载测试。**

测试端由 MCP 宿主机调度，确保压测流量经过正确的网络路径和防火墙规则。

```json
{
  "name": "test_load",
  "description": "对 MCP 管理的服务执行负载测试（wrk / hey / ab）。测试端由 MCP 宿主机调度，确保压测流量经过正确的网络路径和防火墙规则。禁止在本地笔记本直接执行 wrk，以避免跨网络延迟干扰和带宽瓶颈导致的测试结果失真。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "envId": {
        "type": "string",
        "description": "目标环境 ID，必须来自 env_list 中状态为 RUNNING 的环境"
      },
      "targetPort": {
        "type": "integer",
        "description": "目标服务端口"
      },
      "duration": {
        "type": "integer",
        "description": "测试持续时间（秒），默认 30",
        "default": 30
      },
      "threads": {
        "type": "integer",
        "description": "并发线程数，默认 4",
        "default": 4
      },
      "connections": {
        "type": "integer",
        "description": "连接数，默认 100",
        "default": 100
      },
      "tool": {
        "type": "string",
        "enum": ["wrk", "hey", "ab"],
        "description": "压测工具"
      },
      "path": {
        "type": "string",
        "description": "压测路径，如 '/api/counter?action=increment'"
      }
    },
    "required": ["envId", "targetPort"]
  }
}
```

---

## 诊断类 Tools

### `test_exec_command` — 远程命令执行

**【唯一合法】在 MCP 管理的远程宿主机上执行命令。**

所有命令会被审计日志记录并受限于 RBAC 策略。

```json
{
  "name": "test_exec_command",
  "description": "在 MCP 管理的远程宿主机上执行命令。这是【唯一合法】的远程命令执行入口，所有命令会被审计日志记录并受限于 RBAC 策略。禁止通过本地 SSH 客户端直连宿主机，以避免绕过操作审计和权限管控。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "hostId": {
        "type": "string",
        "description": "目标宿主机 ID，必须来自 env_list 返回的宿主机池"
      },
      "command": {
        "type": "string",
        "description": "要执行的命令。禁止交互式命令（如 vim、top），只允许非交互式命令"
      },
      "workingDir": {
        "type": "string",
        "description": "命令执行的工作目录，默认 /opt/mcp",
        "default": "/opt/mcp"
      }
    },
    "required": ["hostId", "command"]
  }
}
```

---

### `analyze_network_path` — 网络路径分析

**【唯一合法】分析从 MCP 宿主机到目标环境的网络路径。**

此工具会展示经过的每一跳路由、延迟和防火墙规则命中情况。

```json
{
  "name": "analyze_network_path",
  "description": "分析从 MCP 宿主机到目标环境的网络路径（traceroute / mtr / tcptraceroute）。此工具会展示经过的每一跳路由、延迟和防火墙规则命中情况。禁止用本地 traceroute 替代，因为本地路径与 MCP 宿主机路径可能不同（NAT、VPN、SD-WAN 差异）。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "sourceHostId": {
        "type": "string",
        "description": "源宿主机 ID，必须来自 env_list 的宿主机池"
      },
      "targetEnvId": {
        "type": "string",
        "description": "目标环境 ID，必须来自 env_list"
      },
      "targetPort": {
        "type": "integer",
        "description": "目标端口，用于验证端到端连通性"
      },
      "protocol": {
        "type": "string",
        "enum": ["tcp", "udp", "icmp"],
        "description": "探测协议，默认 tcp",
        "default": "tcp"
      }
    },
    "required": ["sourceHostId", "targetEnvId", "targetPort"]
  }
}
```

---

## 工具总览表

| # | 工具名 | 类别 | 角色标签 | 前置条件 | 数据源依赖 |
|---|--------|------|---------|---------|-----------|
| 1 | `deploy_pipeline` | 编排 | 【首选入口】 | 无 | `env_list`（获取 targetHostId） |
| 2 | `env_create` | 环境 | 【唯一入口】 | hostId 是 TARGET 角色 | `env_list`（获取 availableHosts） |
| 3 | `env_deploy_service` | 环境 | — | 环境状态 READY/RUNNING | `env_create`（获取 envId） |
| 4 | `env_list` | 环境 | — | 无 | 无 |
| 5 | `env_destroy` | 环境 | — | envId 存在 | `env_list`（获取 envId） |
| 6 | `env_get_logs` | 环境 | 【唯一合法】 | envId 存在 | `env_list`（获取 envId） |
| 7 | `test_health_check` | 测试 | 【唯一合法】 | 环境状态 RUNNING | `env_list`（获取 RUNNING 环境） |
| 8 | `test_load` | 测试 | 【唯一合法】 | 环境状态 RUNNING | `env_list`（获取 RUNNING 环境） |
| 9 | `test_exec_command` | 诊断 | 【唯一合法】 | hostId 存在 | `env_list`（获取宿主机池） |
| 10 | `analyze_network_path` | 诊断 | 【唯一合法】 | source/target 存在 | `env_list`（获取宿主机池） |

---

## 错误处理

### 统一错误格式

```json
{
  "isError": true,
  "content": [
    {
      "type": "text",
      "text": "{\"error\":{\"code\":-32000,\"message\":\"...\",\"data\":{...}}}"
    }
  ]
}
```

### 错误 data 字段规范

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `currentStatus` | string | 条件 | 状态机错误时必填 |
| `requiredStatus` | string | 条件 | 状态机错误时必填 |
| `suggestion` | string | 是 | 人可读的错误解释和修复建议 |
| `forbidden` | string | **关键** | 明确否定本地替代方案 |
| `nextSteps` | string[] | **关键** | 合法的 MCP Tool 回退路径 |

### 常见错误码

| 错误码 | 触发场景 | forbidden 示例 | nextSteps 示例 |
|--------|---------|---------------|---------------|
| `SERVICE_NOT_REGISTERED` | serviceName 不在白名单 | 禁止部署未注册服务 | `["检查 serviceName 拼写", "联系管理员注册"]` |
| `INVALID_ENVIRONMENT_STATUS` | 状态转换非法 | 禁止绕过状态机直接操作 | `["env_list 确认状态", "env_get_logs 排查"]` |
| `ENVIRONMENT_LOCKED` | 分布式锁冲突 | 禁止并发操作同一环境 | `["等待 30 秒后重试", "env_list 查看状态"]` |
| `HOST_NOT_FOUND` | hostId 不存在 | 禁止手动指定未注册节点 | `["env_list 查看可用宿主机"]` |
| `INVALID_HOST_ROLE` | host 角色不匹配 | 禁止向非 TARGET 节点部署 | `["env_list 筛选 roles 包含 target 的节点"]` |

---

**相关文档**:
- [00-术语表.md](../00-术语表.md) — 统一语言词汇表
- [05-边界约束.md](../05-边界约束.md) — description 三段式与错误响应的详细规范
- [V3/CHANGELOG.md](../V3/CHANGELOG.md) — V3 仍然有效的设计决策
- [V3/HISTORY.md](../V3/HISTORY.md) — V3 已放弃/延后的设计思路
