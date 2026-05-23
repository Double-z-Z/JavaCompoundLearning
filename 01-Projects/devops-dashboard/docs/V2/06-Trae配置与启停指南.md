# DevOps MCP Server — Trae 配置与启停指南

> 文档编号: OPS-MCP-001
> 日期: 2026-05-22

---

## 一、Trae 中配置 MCP 的两种方式

你的 DevOps MCP Server 是 **Spring Boot + WebFlux** 应用，运行在 `vm-fedora-dev` (10.0.0.102) 上，暴露 **SSE** 端点。Trae 支持 SSE 远程连接，因此不需要在本地（Windows）安装任何 Java 环境。

### 方式一：全局配置（推荐，所有项目可用）

**步骤：**

1. **打开 Trae MCP 面板**
   - IDE 模式：点击右上角「设置」图标 → 左侧导航栏选择「MCP」
   - SOLO 模式：点击对话面板右上角「设置」图标 → 选择「MCP」

2. **添加 MCP Server**
   - 点击「添加」→「手动添加」
   - 在配置框中粘贴以下 JSON：

```json
{
  "mcpServers": {
    "devops-dashboard": {
      "type": "sse",
      "url": "http://10.0.0.102:8081/mcp/sse",
      "headers": {
        "Authorization": "Bearer optional-token-if-you-add-auth"
      }
    }
  }
}
```

3. **确认配置**
   - 点击「确认」保存
   - MCP 列表中应显示 `devops-dashboard`，状态为绿色 ✅
   - 如果显示红色 ❌，检查：
     - `vm-fedora-dev` 的 8081 端口是否开放
     - MCP Server 是否已启动（见下文「启停」部分）
     - 防火墙是否拦截

### 方式二：项目级配置（仅当前项目可用）

如果你希望 MCP 配置随项目代码一起版本控制，使用项目级 MCP。

**步骤：**

1. **启用项目级 MCP**
   - 打开 Trae 设置 → MCP
   - 打开「启用项目级 MCP」开关

2. **创建配置文件**
   在项目根目录（`JavaCompoundLearning/01-Projects/devops-dashboard`）创建：

```
.trae/
└── mcp.json
```

3. **写入配置**

```json
{
  "mcpServers": {
    "devops-dashboard": {
      "type": "sse",
      "url": "http://10.0.0.102:8081/mcp/sse"
    }
  }
}
```

4. **重启 Trae 或刷新 MCP 面板**
   - 项目级 MCP 会在打开项目时自动加载
   - 修改 `mcp.json` 后，在 MCP 面板点击「刷新」

---

## 二、MCP Server 本身的配置与启停

### 2.1 配置文件

MCP Server 与主应用共用 Spring Boot 环境，通过 `application-mcp.yml` 控制。

**文件位置：** `src/main/resources/application-mcp.yml`

```yaml
# ============================================
# DevOps Dashboard MCP Server 配置
# ============================================

server:
  port: 8081          # MCP Server 独立端口，避免与主应用 8080 冲突

spring:
  application:
    name: devops-mcp-server
  profiles:
    active: mcp       # 激活 mcp profile

# MCP 协议配置
mcp:
  server:
    enabled: true
    transport: sse    # sse | stdio | http
    path: /mcp/sse    # SSE 端点路径

    # 可选：认证（如果你需要简单保护）
    # auth:
    #   type: bearer
    #   token: your-secret-token

# DevOps 基础设施拓扑配置
devops:
  hosts:
    config-path: classpath:hosts.yml
    refresh-interval: 60s   # 动态刷新间隔

  # 可选：限制 MCP 可执行的危险命令
  security:
    exec-command:
      allowed-patterns:
        - "^curl\s+-s\s+http://.*"     # 允许 curl 诊断
        - "^netstat\s+-tlnp"             # 允许 netstat
        - "^ps\s+aux"                    # 允许 ps
      blocked-patterns:
        - "rm\s+-rf"                     # 禁止删除
        - "mkfs"                           # 禁止格式化
        - ">\s*/etc/passwd"                # 禁止写系统文件

# 日志
logging:
  level:
    devops.dashboard.mcp: DEBUG
    io.modelcontextprotocol: INFO
```

### 2.2 主机拓扑配置

**文件位置：** `src/main/resources/hosts.yml`

```yaml
hosts:
  - id: pve-01
    type: pve-hypervisor
    label: "PVE 虚拟化宿主机"
    network_zone: lan-10.0.0
    capabilities: [vm]
    roles: []
    resources:
      cpu_total: 32
      mem_total_mb: 128000

  - id: vm-fedora-dev
    type: vm
    parent: pve-01
    label: "Fedora 开发环境 (MCP Server 运行于此)"
    network_zone: lan-10.0.0
    capabilities: [docker, native]
    roles: [mcp-host, target]
    resources:
      cpu_total: 8
      cpu_free: 6
      mem_total_mb: 16384
      mem_free_mb: 12000
    access:
      ssh: 10.0.0.102
      port: 22
      user: dev
      key_path: /home/dev/.ssh/id_rsa

  - id: vm-ubuntu-test
    type: vm
    parent: pve-01
    label: "Ubuntu 测试环境"
    network_zone: lan-10.0.0
    capabilities: [docker, native]
    roles: [target]
    resources:
      cpu_total: 4
      cpu_free: 3
      mem_total_mb: 8192
      mem_free_mb: 6144
    access:
      ssh: 10.0.0.103
      port: 22
      user: test
      key_path: /home/dev/.ssh/id_rsa

  - id: vm-loadgen-01
    type: vm
    parent: pve-01
    label: "专用压测机"
    network_zone: lan-10.0.0
    capabilities: [native]
    roles: [loadgen]
    resources:
      cpu_total: 8
      cpu_free: 8
      mem_total_mb: 8192
      mem_free_mb: 8192
    access:
      ssh: 10.0.0.104
      port: 22
      user: loadgen
      key_path: /home/dev/.ssh/id_rsa
    loadgen_tools: [wrk, hey, ab]
```

### 2.3 启动 MCP Server

#### 方式 A：独立进程模式（推荐）

MCP Server 作为独立 Spring Boot 应用运行，与主应用分离。

```bash
# 在 vm-fedora-dev 上执行
# 1. 打包
./mvnw clean package -DskipTests

# 2. 启动 MCP Server（激活 mcp profile）
java -jar \
  -Dspring.profiles.active=mcp \
  -Dserver.port=8081 \
  target/devops-dashboard-0.0.1-SNAPSHOT.jar

# 或使用 nohup 后台运行
nohup java -jar \
  -Dspring.profiles.active=mcp \
  -Dserver.port=8081 \
  target/devops-dashboard-0.0.1-SNAPSHOT.jar \
  > logs/mcp-server.log 2>&1 &
```

#### 方式 B：同进程模式（简化部署）

MCP Server 与主应用共用一个 JVM，通过 `/mcp/sse` 路径暴露。

```bash
# 修改主应用的 application.yml，添加：
# spring.profiles.include: mcp

# 启动主应用（自动包含 MCP）
java -jar target/devops-dashboard-0.0.1-SNAPSHOT.jar

# 此时：
# REST API: http://10.0.0.102:8080/api/v1/*
# MCP SSE:  http://10.0.0.102:8080/mcp/sse
```

**修改主应用 `application.yml`：**

```yaml
spring:
  profiles:
    include: mcp    # 追加 mcp profile
```

### 2.4 停止 MCP Server

```bash
# 查找进程
ps aux | grep devops-dashboard

# 优雅停止（发送 SIGTERM）
kill -15 <PID>

# 强制停止（如果优雅停止失败）
kill -9 <PID>
```

### 2.5 验证 MCP Server 运行状态

```bash
# 在 vm-fedora-dev 本地测试
curl -N http://localhost:8081/mcp/sse

# 应返回 SSE 流式响应（持续连接）

# 从 Windows 开发机测试（确保网络可达）
curl -N http://10.0.0.102:8081/mcp/sse

# 如果无法访问，检查防火墙：
# Fedora 上执行
sudo firewall-cmd --add-port=8081/tcp --permanent
sudo firewall-cmd --reload
```

---

## 三、完整启动流程（从 0 到可用）

### 场景：首次在 vm-fedora-dev 上部署并连接 Trae

```bash
# ========== 步骤 1: 在 vm-fedora-dev 上准备环境 ==========
ssh dev@10.0.0.102

# 确保 JDK 17+ 已安装
java -version

# 确保 wrk/hey 已在 vm-loadgen-01 上安装
# （压测工具需要在 loadgen host 上可用）
ssh loadgen@10.0.0.104 "which wrk && which hey"

# ========== 步骤 2: 配置 hosts.yml ==========
# 编辑 src/main/resources/hosts.yml
# 确保 IP、SSH key 路径、用户名与你的环境一致

# ========== 步骤 3: 编译打包 ==========
cd /path/to/devops-dashboard
./mvnw clean package -DskipTests

# ========== 步骤 4: 启动 MCP Server ==========
nohup java -jar \
  -Dspring.profiles.active=mcp \
  -Dserver.port=8081 \
  target/devops-dashboard-0.0.1-SNAPSHOT.jar \
  > logs/mcp-server.log 2>&1 &

# 记录 PID
echo $! > mcp-server.pid

# ========== 步骤 5: 验证启动 ==========
tail -f logs/mcp-server.log
# 应看到: "MCP Server started on port 8081"
# 应看到: "Registered 15 tools, 4 resources, 3 prompts"

# ========== 步骤 6: 开放防火墙 ==========
sudo firewall-cmd --add-port=8081/tcp --permanent
sudo firewall-cmd --reload

# ========== 步骤 7: 在 Trae 中配置 ==========
# 回到 Windows，打开 Trae
# 设置 → MCP → 手动添加
# 粘贴 SSE 配置（见上文「方式一」）

# ========== 步骤 8: 测试连接 ==========
# 在 Trae 的 AI 对话框中输入：
# "我现在有哪些可用的主机？"
# AI 应调用 hosts://topology 并返回你的 PVE/VM 列表
```

---

## 四、常见问题排查

### 4.1 Trae 显示红色 ❌（连接失败）

| 现象 | 原因 | 解决 |
|------|------|------|
| `Connection refused` | MCP Server 未启动 | 检查 `vm-fedora-dev:8081` 是否有进程监听 |
| `Timeout` | 防火墙拦截 | 在 Fedora 上执行 `firewall-cmd --add-port=8081/tcp` |
| `404 Not Found` | SSE 路径错误 | 检查 `application-mcp.yml` 中的 `mcp.server.path` |
| `Invalid JSON` | Trae 配置格式错误 | 确保 JSON 中无注释，逗号正确 |

### 4.2 MCP Server 日志排查

```bash
# 查看启动日志
tail -f logs/mcp-server.log | grep -E "ERROR|WARN|MCP"

# 常见错误：
# - "Host not found: pve-01" -> hosts.yml 中 parent 引用不存在
# - "Port 8081 already in use" -> 端口被占用，修改 server.port
# - "Failed to load hosts.yml" -> YAML 格式错误，检查缩进
```

### 4.3 SSH 连接失败（压测/诊断时）

```bash
# 测试从 vm-fedora-dev 到各节点的 SSH
ssh -i ~/.ssh/id_rsa test@10.0.0.103 "echo ok"
ssh -i ~/.ssh/id_rsa loadgen@10.0.0.104 "echo ok"

# 如果失败：
# 1. 确保 SSH key 已分发到各节点
# 2. 确保 ~/.ssh/id_rsa 权限为 600
# 3. 确保目标节点的 ~/.ssh/authorized_keys 包含公钥
```

---

## 五、生产环境建议

### 5.1 使用 systemd 管理 MCP Server

创建 `/etc/systemd/system/devops-mcp.service`：

```ini
[Unit]
Description=DevOps Dashboard MCP Server
After=network.target

[Service]
Type=simple
User=dev
WorkingDirectory=/home/dev/devops-dashboard
ExecStart=/usr/bin/java \
  -Dspring.profiles.active=mcp \
  -Dserver.port=8081 \
  -jar /home/dev/devops-dashboard/target/devops-dashboard-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

启用：

```bash
sudo systemctl enable devops-mcp
sudo systemctl start devops-mcp
sudo systemctl status devops-mcp
```

### 5.2 使用 Nginx 反向代理（可选）

如果你希望统一入口，用 Nginx 代理 MCP SSE：

```nginx
server {
    listen 80;
    server_name devops.local;

    location /mcp/sse {
        proxy_pass http://10.0.0.102:8081/mcp/sse;
        proxy_http_version 1.1;
        proxy_set_header Connection '';
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 86400s;
    }
}
```

Trae 中配置改为：
```json
{
  "mcpServers": {
    "devops-dashboard": {
      "type": "sse",
      "url": "http://devops.local/mcp/sse"
    }
  }
}
```

---

## 六、快速参考卡

### Trae MCP 配置（SSE 远程）

```json
{
  "mcpServers": {
    "devops-dashboard": {
      "type": "sse",
      "url": "http://10.0.0.102:8081/mcp/sse"
    }
  }
}
```

### 启动命令

```bash
java -Dspring.profiles.active=mcp -Dserver.port=8081 -jar devops-dashboard.jar
```

### 停止命令

```bash
kill -15 $(cat mcp-server.pid)
```

### 验证命令

```bash
curl -N http://10.0.0.102:8081/mcp/sse
```

### 防火墙命令

```bash
sudo firewall-cmd --add-port=8081/tcp --permanent && sudo firewall-cmd --reload
```