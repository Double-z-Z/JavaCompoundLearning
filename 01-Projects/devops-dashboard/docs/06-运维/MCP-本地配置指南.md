# MCP Server 本地配置与使用指南

## 📋 概述

DevOps Dashboard MCP Server 提供标准化的 Tool/Resource 接口，支持 AI 客户端（如 Claude Desktop、Cursor）通过 MCP 协议访问 DevOps 能力。

## 🚀 快速启动

### 方式一：使用启动脚本（推荐）

```bash
# 启动 MCP Server
./scripts/start-mcp.sh start

# 先编译再启动
./scripts/start-mcp.sh build

# 测试端点
./scripts/start-mcp.sh test

# 停止服务器
./scripts/start-mcp.sh stop

# 查看状态
./scripts/start-mcp.sh status

# 查看日志
./scripts/start-mcp.sh logs
```

### 方式二：Maven 直接运行

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mcp
```

### 方式三：JAR 包运行

```bash
# 打包
mvn package -DskipTests

# 运行
java -jar target/devops-dashboard-1.0.0-SNAPSHOT.jar --spring.profiles.active=mcp
```

## 🔧 配置说明

### MCP 专用配置文件

**文件位置**: `src/main/resources/application-mcp.yml`

```yaml
server:
  port: 8081  # MCP Server 端口（与主应用 8080 分离）

mcp:
  server:
    enabled: true           # 启用 MCP Server
    transport: sse          # 使用 SSE 传输协议
    path: /mcp/sse          # SSE 端点路径
    sse:
      timeout: 300000ms     # SSE 超时时间（5分钟）
      heartbeat: 15000ms    # 心跳间隔（15秒）

devops:
  hosts:
    config-path: classpath:hosts.yml  # 主机拓扑配置
    refresh-interval: 60s             # 配置刷新间隔
```

### 数据库配置

MCP 模式使用 **H2 内存数据库**，无需外部数据库：

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:devops-mcp;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    username: sa
    password:
  h2:
    console:
      enabled: true       # 启用 H2 控制台（调试用）
      path: /h2-console   # 控制台路径
```

## 🌐 端点列表

### 基础端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/mcp/sse` | SSE 长连接端点 |

### Resource 端点（只读资源）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/mcp/resources/hosts/topology` | 获取主机拓扑结构 |
| GET | `/mcp/resources/templates/list` | 获取服务模板列表 |

### Tool 端点（环境管理）

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| POST | `/mcp/tools/env/create` | 创建环境 | JSON body (EnvCreateRequest) |
| POST | `/mcp/tools/env/deploy` | 部署服务 | JSON body (EnvDeployRequest) |
| GET | `/mcp/tools/env/access` | 获取访问端点 | `?envId=xxx` |
| POST | `/mcp/tools/env/destroy` | 销毁环境 | `?envId=xxx` |
| GET | `/mcp/tools/env/list` | 列出所有环境 | - |

### Tool 端点（测试工具）

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| POST | `/mcp/tools/test/load` | 执行负载测试 | JSON body (LoadTestRequest) |
| POST | `/mcp/tools/test/health` | 健康检查 | JSON body (HealthCheckRequest) |
| POST | `/mcp/tools/test/exec` | 远程命令执行 | JSON body (ExecCommandRequest) |

### Tool 端点（诊断工具）

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| GET | `/mcp/tools/diagnosis/network_path` | 网络路径分析 | `?sourceHostId=xxx&targetHostId=xxx&targetPort=80` |

## 🧪 测试示例

### 1. 获取主机拓扑

```bash
curl http://localhost:8081/mcp/resources/hosts/topology
```

**响应示例**：
```json
{
  "mcpHostId": "vm-fedora-dev",
  "hosts": [
    {
      "id": "pve-01",
      "type": "pve-hypervisor",
      "label": "PVE 虚拟化宿主机",
      "capabilities": ["vm"],
      "roles": [],
      "resources": { ... }
    },
    {
      "id": "vm-fedora-dev",
      "type": "vm",
      "label": "Fedora 开发环境",
      "roles": ["mcp-host", "target"],
      "isMcpHost": true,
      "isTarget": true,
      ...
    }
  ]
}
```

### 2. 创建环境

```bash
curl -X POST http://localhost:8081/mcp/tools/env/create \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-env",
    "type": "DEV",
    "hostId": "vm-fedora-dev",
    "runtime": "DOCKER"
  }'
```

### 3. 列出所有环境

```bash
curl http://localhost:8081/mcp/tools/env/list
```

### 4. 执行健康检查

```bash
curl -X POST http://localhost:8081/mcp/tools/test/health \
  -H "Content-Type: application/json" \
  -d '{
    "targetHostId": "vm-ubuntu-test",
    "targetPort": 8080,
    "checkType": "HTTP",
    "timeout": 10
  }'
```

## 🔌 AI 客户端配置

### Claude Desktop 配置

**文件位置**: `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS)
或 `%APPDATA%\Claude\claude_desktop_config.json` (Windows)

```json
{
  "mcpServers": {
    "devops-dashboard": {
      "command": "curl",
      "args": ["-s", "-N", "http://localhost:8081/mcp/sse"],
      "env": {}
    }
  }
}
```

**注意**: 当前实现使用自定义 SSE 协议，非标准 MCP SDK。如需标准 MCP 支持，需集成官方 SDK。

### Cursor 配置

在 `.cursor/settings.json` 中添加：

```json
{
  "mcpServers": {
    "devops": {
      "url": "http://localhost:8081/mcp/sse",
      "transport": "sse"
    }
  }
}
```

## 🛠️ 故障排查

### 常见问题

#### 1. 端口被占用

```bash
# 检查端口占用
lsof -i :8081
netstat -tlnp | grep 8081

# 杀掉占用进程
kill -9 <PID>
```

#### 2. H2 数据库错误

如果遇到 `value` 字段相关 SQL 错误，确保已修复 ID 值对象的列名映射：

```java
// EnvironmentId.java, ExperimentId.java
@Column(name = "env_id")  // 或 exp_id
private final String value;
```

#### 3. Bean 缺失错误

确保以下类有 Spring 注解：

```java
@Component
public class McpExceptionTranslator { ... }
```

#### 4. 连接被拒绝

检查进程是否在运行：

```bash
ps aux | grep devops-dashboard
./scripts/start-mcp.sh status
```

### 日志查看

```bash
# 实时查看日志
tail -f ./logs/mcp-server.log

# 查看 ERROR 级别日志
grep ERROR ./logs/mcp-server.log

# 查看 MCP 相关日志
grep "mcp\|MCP" ./logs/mcp-server.log
```

## 📊 监控与调试

### H2 控制台

MCP 模式下启用 H2 控制台，可用于数据库调试：

- **URL**: http://localhost:8081/h2-console
- **JDBC URL**: `jdbc:h2:mem:devops-mcp`
- **用户名**: `sa`
- **密码**: （空）

### Actuator 端点（可选）

如需启用 Actuator，添加依赖和配置：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,mcp
```

## 🔄 与主应用的关系

| 特性 | 主应用 (8080) | MCP Server (8081) |
|------|--------------|-------------------|
| Profile | default | mcp |
| 数据库 | PostgreSQL | H2 内存 |
| 协议 | REST API + Swagger | MCP (SSE) |
| 用途 | Web UI / API 调用 | AI 客户端集成 |
| OpenAPI | ✅ 启用 | ❌ 禁用 |

## 📝 开发注意事项

1. **代码修改后重启**: 修改 Java 代码后需重新编译并重启 MCP Server
2. **热加载**: 可使用 Spring DevTools 实现热加载（需额外配置）
3. **Profile 切换**: 使用 `--spring.profiles.active=mcp` 激活 MCP 模式
4. **日志级别**: MCP 模式下自动开启 DEBUG 级别日志，便于调试

## 🎯 下一步

- [ ] 集成官方 MCP SDK（替代自定义 SSE 实现）
- [ ] 添加认证机制（生产环境必需）
- [ ] 实现 WebSocket 传输支持
- [ ] 添加速率限制和配额管理
- [ ] 集成 Prometheus 监控指标

---

**最后更新**: 2026-05-23
**适用版本**: devops-dashboard 1.0.0-SNAPSHOT
