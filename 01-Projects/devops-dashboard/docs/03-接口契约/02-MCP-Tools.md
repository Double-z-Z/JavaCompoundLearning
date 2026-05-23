# DevOps Dashboard MCP Tools — 完整定义

> **版本**: v2.0 (MCP AI-Native)
> **协议**: Model Context Protocol (MCP)
> **传输方式**: SSE / HTTP
> **依赖**: [REST API 文档](./01-REST-API.md)

---

## 📖 目录

1. [概述](#概述)
2. [发现类 Resources（只读）](#发现类-resources只读)
3. [环境类 Tools](#环境类-tools)
4. [测试类 Tools](#测试类-tools)
5. [诊断类 Tools](#诊断类-tools)
6. [会话类 Tools](#会话类-tools)
7. [使用流程](#使用流程)
8. [错误处理](#错误处理)

---

## 概述

### MCP 协议架构

```
┌─────────────────────────────────────────────┐
│              AI Client (Claude/GPT/Trae)      │
│                     │                        │
│              MCP Protocol                    │
│                     │                        │
├─────────────────────┼────────────────────────┤
│           DevOps MCP Server                  │
│  ┌──────────────────┴──────────────────┐     │
│  │         Tool Handler Layer          │     │
│  │  ┌─────────┐ ┌─────────┐ ┌────────┐ │     │
│  │  │Env Handler│ │Test Hdlr│ │Diag Hdl│ │     │
│  │  └─────────┘ └─────────┘ └────────┘ │     │
│  ├──────────────────┬──────────────────┤     │
│  │    Resource Layer (Read-only)        │     │
│  │  ┌─────────┐ ┌─────────┐ ┌────────┐ │     │
│  │  │hosts://  │ │templates│ │envs:// │ │     │
│  │  │topology │ │://list  │ │list   │ │     │
│  │  └─────────┘ └─────────┘ └────────┘ │     │
│  └──────────────────┬──────────────────┘     │
│            Domain Service Layer               │
│  ┌──────────┐ ┌──────────┐ ┌────────────┐   │
│  │Environment│ │ Loadgen  │ │  Evidence   │   │
│  │ Service   │ │ Service  │ │  Collector  │   │
│  └──────────┘ └──────────┘ └────────────┘   │
└─────────────────────────────────────────────┘
```

### Tool 分类说明

| 类别 | 类型 | 用途 | 示例 |
|------|------|------|------|
| **发现类** | Resource (只读) | 查询基础设施状态、可用资源 | `hosts://topology` |
| **环境类** | Tool (写操作) | 环境生命周期管理 | `env_create`, `env_destroy` |
| **测试类** | Tool (读写) | 健康检查、功能测试、压测 | `test_load`, `test_health_check` |
| **诊断类** | Tool (只读) | 网络路径分析、故障定位 | `analyze_network_path` |
| **会话类** | Tool (读写) | 实验全生命周期管理 | `session_create`, `session_conclude` |

---

## 发现类 Resources（只读）

Resources 是 MCP 的只读数据源，AI 在执行任何操作前应先查询这些 Resource 获取上下文。

### `hosts://topology` — 主机拓扑

**用途**: 返回 MCP 可支配的完整主机层次拓扑。

**何时调用**: 
- 任何部署/压测操作前（必须）
- 需要了解当前基础设施状态时
- 选择部署目标或压测机时

```json
{
  "uri": "hosts://topology",
  "mimeType": "application/json",
  "name": "主机拓扑",
  "description": "返回 MCP 可支配的完整主机层次拓扑，包括 PVE 宿主机、VM、角色、能力、资源余量。AI 必须在任何部署/压测操作前查询此 Resource。",
  "text": {
    "schema": {
      "type": "object",
      "properties": {
        "mcp_host_id": { 
          "type": "string", 
          "description": "当前运行 MCP Server 的主机 ID" 
        },
        "hosts": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "id": { "type": "string" },
              "type": { 
                "type": "string", 
                "enum": ["pve-hypervisor", "vm", "bare-metal", "local"] 
              },
              "parent": { 
                "type": ["string", "null"], 
                "description": "父节点 ID，PVE 宿主机为 null" 
              },
              "label": { "type": "string" },
              "network_zone": { "type": "string" },
              "capabilities": { 
                "type": "array", 
                "items": { 
                  "type": "string", 
                  "enum": ["docker", "native", "vm"] 
                } 
              },
              "roles": { 
                "type": "array", 
                "items": { 
                  "type": "string", 
                  "enum": ["mcp-host", "target", "loadgen"] 
                } 
              },
              "resources": {
                "type": "object",
                "properties": {
                  "cpu_total": { "type": "integer" },
                  "cpu_free": { "type": "integer" },
                  "mem_total_mb": { "type": "integer" },
                  "mem_free_mb": { "type": "integer" }
                }
              },
              "loadgen_tools": { 
                "type": "array", 
                "items": { "type": "string" }, 
                "description": "仅 loadgen 角色有效" 
              }
            },
            "required": ["id", "type", "label", "roles", "capabilities"]
          }
        }
      }
    }
  }
}
```

**返回示例**:
```json
{
  "mcp_host_id": "vm-fedora-dev",
  "hosts": [
    {
      "id": "pve-01",
      "type": "pve-hypervisor",
      "parent": null,
      "label": "PVE 虚拟化宿主机",
      "network_zone": "lan-10.0.0",
      "capabilities": ["vm"],
      "roles": [],
      "resources": {
        "cpu_total": 32,
        "cpu_free": 20,
        "mem_total_mb": 128000,
        "mem_free_mb": 80000
      }
    },
    {
      "id": "vm-fedora-dev",
      "type": "vm",
      "parent": "pve-01",
      "label": "Fedora 开发环境 (MCP Server 运行于此)",
      "network_zone": "lan-10.0.0",
      "capabilities": ["docker", "native"],
      "roles": ["mcp-host", "target"],
      "resources": {
        "cpu_total": 8,
        "cpu_free": 6,
        "mem_total_mb": 16384,
        "mem_free_mb": 12000
      }
    },
    {
      "id": "vm-ubuntu-test",
      "type": "vm",
      "parent": "pve-01",
      "label": "Ubuntu 测试环境",
      "network_zone": "lan-10.0.0",
      "capabilities": ["docker", "native"],
      "roles": ["target"],
      "resources": {
        "cpu_total": 4,
        "cpu_free": 3,
        "mem_total_mb": 8192,
        "mem_free_mb": 6144
      }
    },
    {
      "id": "vm-loadgen-01",
      "type": "vm",
      "parent": "pve-01",
      "label": "专用压测机",
      "network_zone": "lan-10.0.0",
      "capabilities": ["native"],
      "roles": ["loadgen"],
      "resources": {
        "cpu_total": 8,
        "cpu_free": 8,
        "mem_total_mb": 8192,
        "mem_free_mb": 8192
      },
      "loadgen_tools": ["wrk", "hey", "ab"]
    }
  ]
}
```

---

### `templates://list` — 服务模板列表

**用途**: 返回所有可用的服务模板。

**何时调用**: 
- 部署服务前（必须）
- 查询系统支持哪些中间件时

```json
{
  "uri": "templates://list",
  "mimeType": "application/json",
  "name": "服务模板列表",
  "description": "返回所有可用的服务模板，包含版本、默认端口、资源需求、可配置参数。",
  "text": {
    "schema": {
      "type": "object",
      "properties": {
        "templates": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "id": { "type": "string" },
              "display_name": { "type": "string" },
              "category": { "type": "string" },
              "versions": { "type": "array", "items": { "type": "string" } },
              "default_ports": {
                "type": "array",
                "items": {
                  "type": "object",
                  "properties": {
                    "container": { "type": "integer" },
                    "host": { "type": "integer" },
                    "desc": { "type": "string" }
                  }
                }
              },
              "requirements": {
                "type": "object",
                "properties": {
                  "min_memory_mb": { "type": "integer" },
                  "recommend_memory_mb": { "type": "integer" }
                }
              },
              "configurable_params": { "type": "array", "items": { "type": "string" } },
              "dependencies": { "type": "array", "items": { "type": "string" } }
            }
          }
        }
      }
    }
  }
}
```

---

### `templates://{template_id}` — 服务模板详情

**用途**: 返回指定模板的完整配置。

**何时调用**: 
- 向用户展示可覆盖的参数时
- 需要了解服务的详细配置选项时

```json
{
  "uri": "templates://nacos-server",
  "mimeType": "application/json",
  "name": "Nacos 服务模板详情",
  "description": "返回指定模板的完整配置，用于 AI 向用户展示可覆盖的参数。",
  "text": {
    "id": "nacos-server",
    "display_name": "Nacos 注册中心 & 配置中心",
    "category": "service-discovery",
    "versions": ["v2.2.3", "v2.3.0"],
    "default_config": {
      "image": "nacos/nacos-server:v2.2.3",
      "ports": [
        { "container": 8848, "host": 8848, "desc": "Console & OpenAPI" },
        { "container": 9848, "host": 9848, "desc": "gRPC" },
        { "container": 9849, "host": 9849, "desc": "gRPC" }
      ],
      "environment_variables": {
        "MODE": "standalone",
        "NACOS_AUTH_ENABLE": "true",
        "JVM_XMS": "512m",
        "JVM_XMX": "1024m"
      },
      "health_check": {
        "endpoint": "/nacos/v1/console/health/readiness",
        "initial_delay_seconds": 40,
        "period_seconds": 10,
        "timeout_seconds": 5
      }
    },
    "configurable_params": ["MODE", "NACOS_AUTH_ENABLE", "JVM_XMS", "JVM_XMX", "MYSQL_HOST"],
    "dependencies": ["mysql(optional,集群模式必需)"]
  }
}
```

---

### `envs://list` — 环境列表

**用途**: 返回当前活跃的环境列表。

**何时调用**: 
- 创建新环境前（避免端口冲突、资源耗尽）
- 查看当前有哪些环境在运行

```json
{
  "uri": "envs://list",
  "mimeType": "application/json",
  "name": "环境列表",
  "description": "返回当前活跃的环境列表，用于避免端口冲突、资源耗尽。",
  "text": {
    "schema": {
      "type": "object",
      "properties": {
        "environments": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "id": { "type": "string" },
              "name": { "type": "string" },
              "status": { 
                "type": "string", 
                "enum": ["CREATING", "RUNNING", "STOPPED", "DESTROYED", "FAILED"] 
              },
              "target_host_id": { 
                "type": "string", 
                "description": "环境所在的 Target Host" 
              },
              "target_host_label": { "type": "string" },
              "runtime": { 
                "type": "string", 
                "enum": ["docker", "native"] 
              },
              "services": {
                "type": "array",
                "items": {
                  "type": "object",
                  "properties": {
                    "name": { "type": "string" },
                    "template_id": { "type": "string" },
                    "status": { "type": "string" },
                    "ports": { "type": "array", "items": { "type": "integer" } }
                  }
                }
              },
              "resource_usage": {
                "type": "object",
                "properties": {
                  "cpu_percent": { "type": "number" },
                  "mem_mb": { "type": "integer" }
                }
              }
            }
          }
        }
      }
    }
  }
}
```

---

## 环境类 Tools

环境的完整生命周期管理：创建 → 部署服务 → 获取访问信息 → 销毁。

### `env_create` — 创建环境

**用途**: 在指定的 Target Host 上创建一个新的空环境。

**前置条件**:
1. 必须先调用 `hosts://topology` 查询可用节点
2. 必须先调用 `envs://list` 检查资源冲突
3. Target Host 必须具有 `target` 角色且资源充足

**⚠️ 安全提示**: 此操作会分配资源，建议向用户展示参数清单后确认再执行。

```json
{
  "name": "env_create",
  "description": "在指定的 Target Host 上创建一个新的空环境。Target Host 必须具有 target 角色且资源充足。创建前建议查询 hosts://topology 和 envs://list。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "target_host_id": {
        "type": "string",
        "description": "目标宿主机 ID。必须是 hosts://topology 中 roles 包含 'target' 的节点。"
      },
      "env_name": {
        "type": "string",
        "description": "环境名称，用于标识。如 'nacos-perf-01'。"
      },
      "type": {
        "type": "string",
        "enum": ["DEV", "TEST", "STAGING", "EXPERIMENT"],
        "description": "环境类型。实验场景建议 EXPERIMENT。"
      },
      "runtime": {
        "type": "string",
        "enum": ["docker", "native"],
        "default": "docker",
        "description": "运行时类型。docker=容器化部署，native=直接进程。必须与 Target Host 的 capabilities 匹配。"
      },
      "resource_limit": {
        "type": "object",
        "description": "资源限制，不填则使用 Target Host 默认值。",
        "properties": {
          "cpu": { "type": "string", "description": "如 '2' 或 '2000m'" },
          "memory": { "type": "string", "description": "如 '2Gi' 或 '2048Mi'" }
        }
      },
      "auto_destroy_duration": {
        "type": "string",
        "default": "2h",
        "description": "自动销毁时长，如 '2h'、'30m'。到达后自动清理资源。"
      }
    },
    "required": ["target_host_id", "env_name", "type"]
  }
}
```

**返回示例**:
```json
{
  "env_id": "env-20260522-001",
  "name": "nacos-perf-01",
  "target_host_id": "vm-ubuntu-test",
  "target_host_label": "Ubuntu 测试环境",
  "status": "CREATING",
  "runtime": "docker",
  "auto_destroy_at": "2026-05-22T19:22:00Z",
  "created_at": "2026-05-22T17:22:00Z"
}
```

---

### `env_deploy_service` — 部署服务

**用途**: 向已有环境部署一个服务实例。

**前置条件**:
1. 环境必须处于 `RUNNING` 或 `CREATING` 状态
2. 必须先通过 `templates://list` 查询可用模板
3. 建议通过 `templates://{id}` 了解可配置参数

```json
{
  "name": "env_deploy_service",
  "description": "向已有环境部署一个服务实例。环境必须处于 RUNNING 或 CREATING 状态。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "env_id": { "type": "string" },
      "template_id": {
        "type": "string",
        "description": "服务模板 ID。必须先通过 templates://list 查询可用模板。"
      },
      "version": {
        "type": "string",
        "description": "模板版本，不填则使用默认版本。"
      },
      "config_overrides": {
        "type": "object",
        "description": "覆盖模板默认配置。如 {\"MODE\": \"standalone\", \"JVM_XMX\": \"2g\"}。可用覆盖项查询 templates://{id}。",
        "additionalProperties": { "type": "string" }
      },
      "port_mapping": {
        "type": "array",
        "description": "显式指定端口映射，不填则使用模板默认。格式: [{\"container\": 8848, \"host\": 8848}]",
        "items": {
          "type": "object",
          "properties": {
            "container": { "type": "integer" },
            "host": { "type": "integer" }
          },
          "required": ["container", "host"]
        }
      }
    },
    "required": ["env_id", "template_id"]
  }
}
```

---

### `env_get_access` — 获取访问端点

**用途**: 获取环境的访问端点（控制台地址、API 地址、SSH 信息）。

**何时调用**: 部署完成后**必须**调用此 Tool 告知用户如何访问。

```json
{
  "name": "env_get_access",
  "description": "获取环境的访问端点（控制台地址、API 地址、SSH 信息）。部署完成后必须调用此 Tool 告知用户如何访问。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "env_id": { "type": "string" }
    },
    "required": ["env_id"]
  }
}
```

**返回示例**:
```json
{
  "env_id": "env-20260522-001",
  "target_host_id": "vm-ubuntu-test",
  "access_endpoints": {
    "nacos_console": "http://10.0.0.103:8848/nacos",
    "nacos_api": "http://10.0.0.103:8848",
    "mysql": "tcp://10.0.0.103:3306"
  },
  "services": [
    {
      "name": "nacos-server",
      "status": "RUNNING",
      "ports": [8848, 9848, 9849],
      "health_check_status": "healthy"
    },
    {
      "name": "mysql",
      "status": "RUNNING",
      "ports": [3306],
      "health_check_status": "healthy"
    }
  ]
}
```

---

### `env_destroy` — 销毁环境

**用途**: 销毁指定环境及其中所有服务。

**⚠️ 危险操作警告**:
- 此操作**不可逆**
- 数据将丢失
- 建议向用户明确确认后再执行

```json
{
  "name": "env_destroy",
  "description": "销毁指定环境及其中所有服务。不可逆，数据将丢失。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "env_id": { "type": "string" },
      "force": {
        "type": "boolean",
        "default": false,
        "description": "是否强制销毁，即使环境状态为 RUNNING。"
      }
    },
    "required": ["env_id"]
  }
}
```

---

## 测试类 Tools

提供健康检查、功能验证、负载压测、指标采集等测试能力。

### `test_health_check` — 健康检查

**用途**: 对指定环境中的指定服务执行健康检查。

```json
{
  "name": "test_health_check",
  "description": "对指定环境中的指定服务执行健康检查。返回 HTTP 状态码、响应时间、响应体摘要。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "env_id": { "type": "string" },
      "service_name": { 
        "type": "string", 
        "description": "如 'nacos-server'" 
      },
      "endpoint": {
        "type": "string",
        "description": "健康检查端点路径，如 '/nacos/v1/console/health/readiness'。不填则使用模板默认。"
      },
      "timeout_seconds": { 
        "type": "integer", 
        "default": 30,
        "description": "超时时间（秒）"
      }
    },
    "required": ["env_id", "service_name"]
  }
}
```

**返回示例**:
```json
{
  "status": "healthy",
  "http_status": 200,
  "response_time_ms": 23,
  "response_body_summary": "{\"status\":\"UP\"}",
  "checked_at": "2026-05-22T17:25:00Z"
}
```

---

### `test_functional` — 功能验证测试

**用途**: 执行预置的功能测试用例或自定义脚本。

**内置测试用例**:

| test_case | 说明 |
|-----------|------|
| `nacos-register-instance` | Nacos 服务注册 |
| `nacos-discover-instance` | Nacos 服务发现 |
| `nacos-config-publish` | Nacos 配置发布 |
| `redis-ping` | Redis 连通性测试 |
| `redis-set-get` | Redis 读写测试 |
| `mysql-connect` | MySQL 连接测试 |
| `mysql-crud` | MySQL CRUD 测试 |
| `rabbitmq-publish-consume` | RabbitMQ 消息收发 |
| `custom` | 自定义脚本 |

```json
{
  "name": "test_functional",
  "description": "执行功能验证测试。MCP 内置常见中间件的功能测试脚本库。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "env_id": { "type": "string" },
      "service_name": { "type": "string" },
      "test_case": {
        "type": "string",
        "enum": [
          "nacos-register-instance",
          "nacos-discover-instance",
          "nacos-config-publish",
          "redis-ping",
          "redis-set-get",
          "mysql-connect",
          "mysql-crud",
          "rabbitmq-publish-consume",
          "custom"
        ],
        "description": "选择预置测试用例，或选 custom 提供自定义脚本。"
      },
      "custom_script": {
        "type": "string",
        "description": "当 test_case=custom 时，提供 Shell 脚本内容。脚本将在目标服务容器/主机中执行。"
      },
      "params": {
        "type": "object",
        "description": "测试用例参数。如 nacos-register-instance 需要 {\"serviceName\": \"test-svc\", \"ip\": \"127.0.0.1\", \"port\": 8080}",
        "additionalProperties": { "type": "string" }
      }
    },
    "required": ["env_id", "service_name", "test_case"]
  }
}
```

**返回示例**:
```json
{
  "passed": true,
  "test_case": "nacos-register-instance",
  "details": {
    "registered": 5,
    "discovered": 5,
    "consistency": true
  },
  "duration_ms": 145,
  "executed_at": "2026-05-22T17:26:00Z"
}
```

---

### `test_load` — 负载/压测

**用途**: 在指定的 Loadgen Host 上执行负载/压测。

**前置条件**:
1. Loadgen Host 必须具有 `loadgen` 角色
2. 建议先调用 `analyze_network_path` 分析网络路径
3. 压测端与被测端应分离以保证结果可信度

**支持的压测工具**:

| 工具 | 特点 | 适用场景 |
|------|------|---------|
| `wrk` | 多线程、高性能 | HTTP/HTTPS 压测 |
| `hey` | Go 实现、支持 POST body | REST API 压测 |
| `ab` | Apache Bench、简单易用 | 快速基准测试 |

```json
{
  "name": "test_load",
  "description": "在指定的 Loadgen Host 上执行负载/压测。Loadgen Host 必须具有 loadgen 角色且已安装对应工具。压测端与被测端应分离以保证结果可信度。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "target_url": {
        "type": "string",
        "description": "被测目标完整 URL，如 'http://10.0.0.103:8848/nacos/v1/ns/instance'"
      },
      "loadgen_host_id": {
        "type": "string",
        "description": "压测执行机 ID。必须是 hosts://topology 中 roles 包含 'loadgen' 的节点。"
      },
      "tool": {
        "type": "string",
        "enum": ["wrk", "hey", "ab"],
        "description": "压测工具。MCP 会检查 loadgen_host_id 节点的 loadgen_tools 列表确认可用性。"
      },
      "method": {
        "type": "string",
        "enum": ["GET", "POST", "PUT"],
        "default": "GET",
        "description": "HTTP 方法"
      },
      "duration_seconds": {
        "type": "integer",
        "default": 60,
        "description": "压测持续时间（秒）"
      },
      "connections": {
        "type": "integer",
        "default": 10,
        "description": "并发连接数"
      },
      "requests_per_second": {
        "type": "integer",
        "description": "RPS 上限，不填则不限速由工具决定"
      },
      "payload": {
        "type": "string",
        "description": "POST/PUT 时的请求体。wrk 对 POST body 支持有限，复杂场景建议用 hey。"
      },
      "headers": {
        "type": "object",
        "description": "HTTP 请求头，如 {\"Content-Type\": \"application/json\"}",
        "additionalProperties": { "type": "string" }
      }
    },
    "required": ["target_url", "loadgen_host_id", "tool"]
  }
}
```

**返回示例**:
```json
{
  "tool": "wrk",
  "target_url": "http://10.0.0.103:8848/nacos/v1/ns/instance",
  "duration_seconds": 60,
  "summary": {
    "total_requests": 76830,
    "avg_qps": 1280.5,
    "avg_latency_ms": 14.8,
    "p50_latency_ms": 12.0,
    "p99_latency_ms": 38.0,
    "max_latency_ms": 98.0,
    "error_rate_percent": 0.0
  },
  "thread_stats": {
    "avg_req_per_sec": 1280.5,
    "stdev_req_per_sec": 142.3,
    "max_req_per_sec": 1520
  },
  "raw_output": "Running 1m test @ ...",
  "executed_at": "2026-05-22T17:30:00Z"
}
```

**关键指标解读**:

| 指标 | 含义 | 健康阈值参考 |
|------|------|-------------|
| `avg_qps` | 平均每秒请求数 | 越高越好 |
| `avg_latency_ms` | 平均延迟 | < 100ms 优秀 |
| `p99_latency_ms` | 99分位延迟 | < 500ms 可接受 |
| `error_rate_percent` | 错误率 | < 1% 健康 |

---

### `test_collect_metrics` — 收集性能指标

**用途**: 从环境/服务收集指定时间段的性能指标。

```json
{
  "name": "test_collect_metrics",
  "description": "从环境/服务收集指定时间段的性能指标（CPU、内存、网络 IO）。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "env_id": { "type": "string" },
      "service_name": {
        "type": "string",
        "description": "不填则收集整个环境的聚合指标"
      },
      "metrics": {
        "type": "array",
        "items": {
          "type": "string",
          "enum": [
            "cpu_percent",
            "memory_mb",
            "memory_percent",
            "network_rx_mb",
            "network_tx_mb",
            "disk_io_mbps"
          ]
        },
        "description": "要采集的指标列表"
      },
      "duration_seconds": {
        "type": "integer",
        "default": 30,
        "description": "采集时长（秒）"
      }
    },
    "required": ["env_id"]
  }
}
```

**返回示例**:
```json
{
  "env_id": "env-20260522-001",
  "service_name": "nacos-server",
  "collected_at": "2026-05-22T17:31:00Z",
  "samples": [
    {
      "timestamp": "2026-05-22T17:30:30Z",
      "cpu_percent": 72.5,
      "memory_mb": 1680,
      "memory_percent": 82.0,
      "network_rx_mb": 12.3,
      "network_tx_mb": 8.1
    }
  ]
}
```

---

### `test_stream_logs` — 流式获取日志

**用途**: 获取服务的最近日志，用于故障排查。

```json
{
  "name": "test_stream_logs",
  "description": "获取服务的最近日志，用于故障排查。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "env_id": { "type": "string" },
      "service_name": { "type": "string" },
      "tail_lines": { 
        "type": "integer", 
        "default": 100,
        "description": "返回最后 N 行日志"
      },
      "since": {
        "type": "string",
        "description": "如 '5m'，获取最近 5 分钟日志"
      },
      "grep": {
        "type": "string",
        "description": "过滤关键词，如 'ERROR'"
      }
    },
    "required": ["env_id", "service_name"]
  }
}
```

---

### `test_exec_command` — 执行远程命令

**用途**: 在指定环境的服务容器/虚拟机中执行命令。

**⚠️ 高风险操作**: 此 Tool 具有较高安全风险，执行前必须：
1. 向用户展示完整命令
2. 等待用户显式确认
3. 使用受限用户权限（非 root）

```json
{
  "name": "test_exec_command",
  "description": "在指定环境的服务容器/虚拟机中执行命令。用于深度诊断或自定义操作。注意：此 Tool 具有较高风险，执行前必须向用户展示完整命令并确认。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "env_id": { "type": "string" },
      "service_name": { "type": "string" },
      "command": {
        "type": "string",
        "description": "要执行的命令，如 'curl -s http://localhost:8848/nacos/v1/ns/operator/metrics'"
      },
      "timeout_seconds": { 
        "type": "integer", 
        "default": 30,
        "description": "命令超时时间（秒）"
      }
    },
    "required": ["env_id", "service_name", "command"]
  }
}
```

---

## 诊断类 Tools

### `analyze_network_path` — 网络路径分析

**用途**: 分析从 Loadgen Host 到 Target Host 的网络路径。

**何时调用**: 执行 `test_load` 前**强烈建议**先调用此 Tool，用于判断压测结果是否受网络层干扰。

**路径类型说明**:

| path_type | 含义 | 压测可信度 |
|-----------|------|-----------|
| `same-host` | 同一机器 | ⚠️ 低（本地回环影响） |
| `same-hypervisor` | 同虚拟化宿主机 | ⚠️ 中低（仅经过虚拟交换机） |
| `same-lan` | 同局域网 | ✅ 高 |
| `wan` | 跨广域网 | ⚠️ 中（延迟显著） |

```json
{
  "name": "analyze_network_path",
  "description": "分析从 Loadgen Host 到 Target Host 的网络路径，返回拓扑信息、预估延迟、是否经过 NAT/Bridge。帮助用户判断压测结果是否受网络层干扰。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "source_host_id": {
        "type": "string",
        "description": "压测端/源 Host ID"
      },
      "target_host_id": {
        "type": "string",
        "description": "被测端/目标 Host ID"
      },
      "target_port": {
        "type": "integer",
        "description": "目标端口"
      }
    },
    "required": ["source_host_id", "target_host_id", "target_port"]
  }
}
```

**返回示例**:
```json
{
  "source": {
    "id": "vm-loadgen-01",
    "ip": "10.0.0.104",
    "type": "vm",
    "parent": "pve-01"
  },
  "target": {
    "id": "vm-ubuntu-test",
    "ip": "10.0.0.103",
    "type": "vm",
    "parent": "pve-01"
  },
  "path_type": "same-hypervisor",
  "path_type_display": "同虚拟化宿主机",
  "hops": 1,
  "estimated_rtt_ms": 0.2,
  "nat_traversal": false,
  "goes_through_bridge": true,
  "physical_nic_involved": false,
  "warning": "两 VM 位于同一 PVE 宿主机，流量仅经过虚拟交换机。压测结果反映的是虚拟化内网性能，不能代表跨物理机的网络瓶颈。",
  "recommendation": "如需测试真实网络性能，建议将 Target 或 Loadgen 之一迁移到不同 PVE 宿主机，或通过 tc/netem 模拟延迟。"
}
```

---

## 会话类 Tools

完整的实验生命周期管理：创建会话 → 记录证据 → 提交结论。

### `session_create` — 创建实验会话

**用途**: 创建一个新的实验会话，用于聚合本次实验的所有证据和结论。

```json
{
  "name": "session_create",
  "description": "创建一个新的实验会话，用于聚合本次实验的所有证据和结论。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "intent": {
        "type": "string",
        "description": "用户原始意图描述，如 '验证 Nacos 单机能否支撑 1000 QPS'"
      },
      "target_host_id": { 
        "type": "string",
        "description": "目标部署主机 ID（可选）"
      },
      "loadgen_host_id": { 
        "type": "string",
        "description": "压测机 ID（可选）"
      },
      "environment_id": { 
        "type": "string",
        "description": "已关联的环境 ID（可选）"
      }
    },
    "required": ["intent"]
  }
}
```

**返回示例**:
```json
{
  "session_id": "sess-20260522-001",
  "intent": "验证 Nacos 单机能否支撑 1000 QPS",
  "status": "ACTIVE",
  "created_at": "2026-05-22T17:00:00Z",
  "evidence_count": 0
}
```

---

### `session_record_evidence` — 记录证据

**用途**: 向实验会话添加一条证据。

**证据类型**:

| evidence_type | 说明 | 来源示例 |
|---------------|------|---------|
| `metric` | 数值型指标 | QPS、延迟、CPU 使用率 |
| `artifact` | 产物文件 | 日志文件、截图、报告 |
| `observation` | 观察笔记 | 人工观察到的现象 |
| `check_result` | 检查结果 | 健康检查、功能测试结果 |

```json
{
  "name": "session_record_evidence",
  "description": "向实验会话添加一条证据。可由 AI 自动调用（压测结果、指标）或用户手动触发（观察笔记）。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "session_id": { "type": "string" },
      "evidence_type": {
        "type": "string",
        "enum": ["metric", "artifact", "observation", "check_result"]
      },
      "name": { 
        "type": "string",
        "description": "证据名称，如 'QPS', 'P99延迟', '错误率'"
      },
      "value": { 
        "type": "string",
        "description": "证据值，如 '1280.5', '38ms', '0.0%'"
      },
      "unit": { 
        "type": "string",
        "description": "单位，如 'req/s', 'ms', '%'"
      },
      "source": {
        "type": "string",
        "enum": [
          "load_test_tool",
          "monitoring_agent",
          "health_check",
          "functional_test",
          "manual_input",
          "command_exec"
        ],
        "description": "证据来源"
      },
      "metadata": { 
        "type": "object",
        "description": "附加元数据",
        "additionalProperties": {}
      }
    },
    "required": ["session_id", "evidence_type", "name", "value"]
  }
}
```

**使用示例**:
```json
// 记录压测 QPS 指标
{
  "session_id": "sess-20260522-001",
  "evidence_type": "metric",
  "name": "平均QPS",
  "value": "1280.5",
  "unit": "req/s",
  "source": "load_test_tool",
  "metadata": {
    "tool": "wrk",
    "duration_seconds": 60,
    "connections": 10
  }
}

// 记录人工观察
{
  "session_id": "sess-20260522-001",
  "evidence_type": "observation",
  "name": "Nacos Console响应速度",
  "value": "页面加载约2秒，无明显卡顿",
  "source": "manual_input"
}
```

---

### `session_conclude` — 提交结论

**用途**: 提交实验结论并归档，自动生成 Markdown 报告到 `docs/spikes/` 目录。

**决策类型**:

| decision | 含义 | 后续动作 |
|----------|------|---------|
| `ACCEPT` | 接受假设 | 可进入生产验证 |
| `REJECT` | 拒绝假设 | 需重新设计技术方案 |
| `NEED_MORE_DATA` | 数据不足 | 需补充实验 |
| `INCONCLUSIVE` | 结论不明确 | 需调整实验方法 |

```json
{
  "name": "session_conclude",
  "description": "提交实验结论并归档。会自动生成 Markdown 报告到 docs/spikes/ 目录。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "session_id": { "type": "string" },
      "decision": {
        "type": "string",
        "enum": ["ACCEPT", "REJECT", "NEED_MORE_DATA", "INCONCLUSIVE"]
      },
      "summary": { 
        "type": "string",
        "description": "结论摘要，简要说明决策理由"
      },
      "lessons_learned": {
        "type": "array",
        "items": { "type": "string" },
        "description": "经验教训列表"
      },
      "next_steps": {
        "type": "array",
        "items": { "type": "string" },
        "description": "后续行动建议"
      }
    },
    "required": ["session_id", "decision", "summary"]
  }
}
```

**返回示例**:
```json
{
  "session_id": "sess-20260522-001",
  "decision": "ACCEPT",
  "status": "CONCLUDED",
  "concluded_at": "2026-05-22T18:00:00Z",
  "report_path": "docs/spikes/nacos-single-node-performance-20260522.md",
  "evidence_count": 8,
  "summary": "Nacos 单机在 10 并发下达到 1280 QPS，P99 延迟 38ms，满足 1000 QPS 目标。"
}
```

---

## 使用流程

### 典型实验工作流

```
┌─────────────────────────────────────────────────────────────┐
│                    实验完整工作流                              │
└─────────────────────────────────────────────────────────────┘

1. 意图表达
   用户: "我想验证 Nacos 单机能否支撑 1000 QPS"
   ↓
2. 查询基础设施（AI 自动调用）
   ├── hosts://topology          → 获取可用主机列表
   └── envs://list               → 检查现有环境
   ↓
3. 选择部署位置（AI 展示选项，用户确认）
   AI: "检测到以下 Target Host："
      - vm-ubuntu-test (CPU 3/4 free, Mem 6GB/8GB free)
      - vm-fedora-dev (CPU 6/8 free, Mem 12GB/16GB free)
      请选择部署位置？
   用户: "用 vm-ubuntu-test"
   ↓
4. 创建环境
   AI: "即将创建环境，参数如下："
      - target_host_id: vm-ubuntu-test
      - env_name: nacos-perf-01
      - type: EXPERIMENT
      - auto_destroy_duration: 2h
      确认执行？[y/N]
   用户: y
   ↓
   AI 调用: env_create → 返回 env_id
   ↓
5. 部署服务
   AI 调用: templates://list → 查询可用模板
   AI: "检测到 nacos-server v2.2.3，是否部署？"
   用户: "部署 Nacos 和 MySQL"
   AI 调用: env_deploy_service(nacos-server)
   AI 调用: env_deploy_service(mysql)
   ↓
6. 获取访问信息
   AI 调用: env_get_access → 返回 Console URL
   AI: "✅ Nacos Console: http://10.0.0.103:8848/nacos"
   ↓
7. 健康检查
   AI 调用: test_health_check(nacos-server) → healthy
   ↓
8. 选择压测机 + 网络分析
   AI: "检测到 Loadgen Host: vm-loadgen-01 (工具: wrk, hey, ab)"
   AI 调用: analyze_network_path(vm-loadgen-01 → vm-ubuntu-test:8848)
   AI: "⚠️ 路径类型: same-hypervisor (同虚拟化宿主机)"
      "压测结果可能偏乐观，是否继续？"
   用户: "继续"
   ↓
9. 执行压测
   AI: "压测参数："
      - tool: wrk
      - connections: 10
      - duration: 60s
      - target: http://10.0.0.103:8848/nacos/v1/ns/instance
      确认执行？[y/N]
   用户: y
   ↓
   AI 调用: test_load → 返回 QPS/延迟/错误率
   ↓
10. 收集指标
    AI 调用: test_collect_metrics(nacos-server) → CPU/Memory
    ↓
11. 创建会话 + 记录证据
    AI 调用: session_create(intent="验证 Nacos 单机 1000 QPS")
    AI 调用: session_record_evidence(metric, QPS, 1280.5)
    AI 调用: session_record_evidence(metric, P99延迟, 38ms)
    ↓
12. 提交结论
    AI: "实验结果："
       - 平均 QPS: 1280.5 (目标 1000 ✅)
       - P99 延迟: 38ms (< 500ms ✅)
       - 错误率: 0% (< 1% ✅)
       结论建议: ACCEPT
       是否提交？[ACCEPT/REJECT/NEED_MORE_DATA/INCONCLUSIVE]
    用户: ACCEPT
    ↓
    AI 调用: session_conclude(decision=ACCEPT)
    ↓
13. 归档完成
    AI: "✅ 实验报告已生成:"
       docs/spikes/nacos-single-node-performance-20260522.md
```

---

## 错误处理

### MCP 错误格式

所有 MCP Tool 调用的错误都遵循统一格式：

```json
{
  "isError": true,
  "content": [
    {
      "type": "text",
      "text": "{\"code\":\"HOST_NOT_FOUND\",\"message\":\"Host not found: vm-invalid\",\"details\":{}}"
    }
  ]
}
```

### 常见错误码

| 错误码 | HTTP 映射 | 含义 | 解决方法 |
|--------|----------|------|---------|
| `HOST_NOT_FOUND` | 404 | 指定的 Host ID 不存在 | 检查 `hosts://topology` 返回的 ID |
| `INVALID_HOST_ROLE` | 400 | Host 角色不匹配 | Target 操作需 `target` 角色，Loadgen 需 `loadgen` 角色 |
| `HOST_CAPABILITY_MISMATCH` | 400 | Host 不支持所需能力 | docker runtime 需 `docker` capability |
| `ENVIRONMENT_NOT_FOUND` | 404 | 环境不存在 | 检查 env_id 是否正确 |
| `INVALID_ENVIRONMENT_STATUS` | 409 | 环境状态不允许操作 | 检查环境当前状态 |
| `LOADGEN_TOOL_NOT_AVAILABLE` | 400 | 压测机上未安装指定工具 | 查看 `hosts://topology` 的 `loadgen_tools` 字段 |
| `RESOURCE_QUOTA_EXCEEDED` | 409 | 资源配额超限 | 减少资源需求或选择其他 Host |
| `PORT_CONFLICT` | 409 | 端口冲突 | 更换端口或销毁占用端口的環境 |

### 错误恢复策略

```
错误发生
    ↓
判断错误类型
    ├── 可重试错误（临时性）
    │   └── 重试 1-3 次，指数退避
    ├── 参数错误
    │   └── 向用户展示错误信息 + 正确格式示例
    ├── 权限/角色错误
    │   └── 提示用户更换 Host 或联系管理员
    └── 资源不足
        └── 建议：减少资源需求 / 销毁旧环境 / 更换 Host
```

---

## 相关文档

- [REST API 使用指南](./01-REST-API.md) — V1 双轨运行的 REST 接口
- [Java 接口定义](./03-Java-接口定义.md) — 后端实现接口
- [迁移路线图](../04-实施计划/01-迁移路线图.md) — V1→V2 改造计划
- [当前任务清单](../04-实施计划/02-当前任务清单.md) — Phase 1 任务拆解

---

**文档结束**。
