# Trae 配置与运维指南

> 文档编号: OPS-MCP-001
> 日期: 2026-05-22

本文档指导如何在 Trae IDE 中配置、启动、停止和运维 DevOps Dashboard MCP Server。

---

## 一、Trae 中配置 MCP 的两种方式

DevOps MCP Server 是 **Spring Boot + WebFlux** 应用，运行在 `vm-fedora-dev` (10.0.0.102) 上，暴露 **SSE** 端点。Trae 支持 SSE 远程连接，因此不需要在本地（Windows）安装任何 Java 环境。

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
   在项目根目录创建：

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

### 两种方式对比

| 维度 | 全局配置 | 项目级配置 |
|------|---------|-----------|
| 作用域 | 所有 Trae 项目 | 仅当前项目 |
| 版本控制 | ❌ 不纳入 Git | ✅ 可提交到仓库 |
| 团队共享 | 需手动同步 | 自动共享 |
| 适用场景 | 个人长期使用 | 团队协作项目 |

---

## 二、MCP Server 配置与启停

### 2.1 配置文件

MCP Server 与主应用共用 Spring Boot 环境，通过 `application-mcp.yml` 控制。

**文件位置**: `src/main/resources/application-mcp.yml`

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

    # 可选：认证（生产环境建议启用）
    # auth:
    #   type: bearer
    #   token: your-secret-token

# DevOps 基础设施拓扑配置
devops:
  hosts:
    config-path: classpath:hosts.yml
    refresh-interval: 60s   # 动态刷新拓扑

  # 可选：限制 MCP 可执行的危险命令
  security:
    exec-command:
      allowed-patterns:
        - "^curl\\s+-s\\s+http://.*"     # 允许 curl 诊断
        - "^netstat\\s+-tlnp"             # 允许 netstat
        - "^ps\\s+aux"                    # 允许 ps
      blocked-patterns:
        - "rm\\s+-rf"                     # 禁止删除
        - "mkfs"                           # 禁止格式化
        - ">\\s*/etc/passwd"                # 禁止写系统文件

# 日志
logging:
  level:
    devops.dashboard.mcp: DEBUG
    io.modelcontextprotocol: INFO
```

### 2.2 主机拓扑配置

**文件位置**: `src/main/resources/hosts.yml`

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

MCP Server 作为独立 Spring Boot 应用运行，与主应用分离。REST API 和 MCP 各自监听不同端口，互不干扰。

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

# 记录 PID（便于后续停止）
echo $! > mcp-server.pid
```

**端口分配**:

| 服务 | 端口 | 协议 |
|------|------|------|
| REST API（主应用） | 8080 | HTTP |
| MCP SSE（独立进程） | 8081 | HTTP SSE |

#### 方式 B：同进程模式（简化部署）

MCP Server 与主应用共用一个 JVM，通过 `/mcp/sse` 路径暴露。适合开发调试或资源受限环境。

```bash
# 修改主应用的 application.yml，添加：
# spring.profiles.include: mcp

# 启动主应用（自动包含 MCP）
java -jar target/devops-dashboard-0.0.1-SNAPSHOT.jar

# 此时：
# REST API: http://10.0.0.102:8080/api/v1/*
# MCP SSE:  http://10.0.0.102:8080/mcp/sse
```

**修改主应用 `application.yml`**：

```yaml
spring:
  profiles:
    include: mcp    # 追加 mcp profile
```

**同进程模式端口分配**:

| 服务 | 端口 | 路径 |
|------|------|------|
| REST API | 8080 | `/api/v1/*` |
| MCP SSE | 8080 | `/mcp/sse` |

### 2.4 停止 MCP Server

```bash
# 方法一：使用 PID 文件（推荐，如果启动时记录了 PID）
kill -15 $(cat mcp-server.pid)

# 方法二：按进程名查找
ps aux | grep devops-dashboard

# 优雅停止（发送 SIGTERM，等待正在处理的请求完成）
kill -15 <PID>

# 强制停止（如果优雅停止超过 10 秒无响应）
kill -9 <PID>

# 方法三：如果是 systemd 管理（生产环境）
sudo systemctl stop devops-mcp
```

### 2.5 验证运行状态

```bash
# 在 vm-fedora-dev 本地测试（应返回 SSE 流式响应，持续连接不断开）
curl -N http://localhost:8081/mcp/sse

# 从远程开发机测试（确保网络可达）
curl -N http://10.0.0.102:8081/mcp/sse

# 预期输出示例：
# data: {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}

# 如果无法访问，检查防火墙：
sudo firewall-cmd --add-port=8081/tcp --permanent
sudo firewall-cmd --reload

# 验证端口监听
ss -tlnp | grep 8081
# 应输出: LISTEN  0  128  *:8081  *:*  users:(("java",pid=12345,...))
```

---

## 三、完整启动流程（从 0 到可用，8 步）

**场景**: 首次在 `vm-fedora-dev` 上部署 MCP Server 并连接 Trae

```bash
# ========== 步骤 1: SSH 登录到 MCP Host ==========
ssh dev@10.0.0.102

# ========== 步骤 2: 环境准备 ==========
# 确保 JDK 17+ 已安装
java -version
# openjdk version "17.x.x" ...

# 确保 Maven 已安装
./mvnw -v

# 创建日志目录
mkdir -p logs

# ========== 步骤 3: 确保 loadgen 工具就位 ==========
# 压测工具需要在 loadgen host (vm-loadgen-01) 上可用
ssh loadgen@10.0.0.104 "which wrk && which hey && which ab"
# 如果未安装：
# sudo dnf install wrk  (Fedora/RHEL)
# sudo apt install apache2-utils hey  (Ubuntu)

# ========== 步骤 4: 配置 hosts.yml ==========
# 编辑 src/main/resources/hosts.yml
# 确认以下信息与你的实际环境一致：
#   - IP 地址（各节点的 access.ssh）
#   - SSH key 路径（access.key_path）
#   - 用户名（access.user）
#   - 父子关系（parent 字段引用的 ID 存在）

# ========== 步骤 5: 编译打包 ==========
cd /path/to/devops-dashboard
./mvnw clean package -DskipTests

# ========== 步骤 6: 启动 MCP Server ==========
nohup java -jar \
  -Dspring.profiles.active=mcp \
  -Dserver.port=8081 \
  target/devops-dashboard-0.0.1-SNAPSHOT.jar \
  > logs/mcp-server.log 2>&1 &

echo $! > mcp-server.pid

# ========== 步骤 7: 验证启动成功 ==========
tail -f logs/mcp-server.log
# 应看到以下关键日志：
#   "MCP Server started on port 8081"
#   "Registered N tools, M resources, P prompts"
#   "Loaded K hosts from hosts.yml"

# 开放防火墙端口
sudo firewall-cmd --add-port=8081/tcp --permanent
sudo firewall-cmd --reload

# 从本地验证 SSE 端点
curl -N http://localhost:8081/mcp/sse | head -5

# ========== 步骤 8: 在 Trae 中配置并测试 ==========
# 回到 Windows，打开 Trae IDE
# 设置 → MCP → 手动添加 → 粘贴 SSE 配置（见上文「方式一」）
#
# 测试连接：在 Trae AI 对话框中输入：
#   "我现在有哪些可用的主机？"
# AI 应调用 hosts://topology 并返回你的 PVE/VM 列表
```

---

## 四、常见问题排查

### 4.1 Trae 显示红色 ❌（连接失败）

| 现象 | 可能原因 | 解决方法 |
|------|---------|---------|
| `Connection refused` | MCP Server 未启动或端口错误 | `ssh dev@10.0.0.102 'ss -tlnp \| grep 8081'` 确认进程存在 |
| `Connection timed out` | 防火墙拦截或网络不通 | `sudo firewall-cmd --add-port=8081/tcp`；确认 IP 正确 |
| `404 Not Found` | SSE 路径错误 | 检查 `application-mcp.yml` 中 `mcp.server.path` 是否为 `/mcp/sse` |
| `SSL/TLS error` | HTTPS 配置问题 | 如果用了 Nginx 反代，检查证书是否有效 |
| `Invalid JSON in response` | 应用启动异常 | 查看 MCP Server 日志 `tail -100 logs/mcp-server.log` |

**排查流程图**：

```
Trae 显示 ❌
  │
  ├─ ping 10.0.0.102 通吗？
  │   └─ 不通 → 检查网络/VPN
  │
  ├─ curl http://10.0.0.102:8081/mcp/sse 有响应吗？
  │   └─ Connection refused → MCP Server 未启动（见步骤 6）
  │   └─ Timeout → 防火墙问题（见步骤 7）
  │   └─ 404 → path 配置错误（检查 application-mcp.yml）
  │
  └─ 有响应但 Trae 仍红 → JSON 格式错误或 headers 问题
      → 检查 Trae 配置中的 URL 是否多/少字符
```

### 4.2 MCP Server 启动日志排查

```bash
# 实时查看日志
tail -f logs/mcp-server.log | grep -E "ERROR|WARN|MCP|Started"

# 常见启动错误及解决：

# 错误 1: "Host not found: pve-01"
# 原因: hosts.yml 中某 VM 的 parent 引用了一个不存在的 ID
# 解决: 检查所有 parent 字段值是否与某个 host 的 id 匹配

# 错误 2: "Port 8081 already in use"
# 原因: 端口被占用
# 解决: ss -tlnp | grep 8081 找到占用进程，kill 掉或换端口

# 错误 3: "Failed to load hosts.yml"
# 原因: YAML 格式错误（缩进、冒号后空格等）
# 解决: 使用在线 YAML 校验工具检查格式

# 错误 4: "No active profile set for mcp"
# 原因: 未正确传递 spring.profiles.active
# 解决: 确认启动命令包含 -Dspring.profiles.active=mcp

# 错误 5: "SSH key permission denied"
# 原因: SSH key 文件权限过于开放
# 解决: chmod 600 ~/.ssh/id_rsa

# 错误 6: "Registered 0 tools"
# 原因: MCP Handler 注册失败，通常是依赖注入问题
# 解决: 检查 @Component 注解是否遗漏，Spring Bean 扫描包路径
```

### 4.3 SSH 连接失败（压测/远程命令执行时）

当 MCP Tool 调用需要 SSH 到其他节点执行命令时（如 `test_load`、`test_exec_command`），可能出现 SSH 问题。

```bash
# 从 vm-fedora-dev 测试到各节点的 SSH 连通性

# 测试到 Ubuntu 测试机
ssh -i ~/.ssh/id_rsa test@10.0.0.103 "echo ok"

# 测试到压测机
ssh -i ~/.ssh/id_rsa loadgen@10.0.0.104 "echo ok"

# 如果失败，逐项检查：

# 1. SSH key 是否已分发到目标节点
#    在目标节点上检查 ~/.ssh/authorized_keys 是否包含公钥

# 2. SSH key 文件权限是否正确
ls -la ~/.ssh/id_rsa
# 应为 -rw------- (600)

# 3. 目标节点 sshd 是否运行
ssh test@10.0.0.103 "systemctl status sshd"

# 4. 目标节点的 ~/.ssh 目录权限
#    应为 drwx------ (700)
#    authorized_keys 应为 -rw------- (600)

# 5. 如果使用密码认证（不推荐但可用于调试）
#    确保 hosts.yml 中 password 字段正确
```

### 4.4 MCP Tools 调用失败

| Tool | 常见错误 | 原因 | 解决 |
|------|---------|------|------|
| `env_create` | `HostNotFoundException` | target_host_id 不存在 | 先调 `hosts://topology` 查看可用 ID |
| `env_create` | `InvalidHostRoleException` | 指定的 Host 不是 TARGET 角色 | 选择 roles 包含 target 的主机 |
| `env_deploy_service` | `PortConflictException` | 端口已被占用 | 销毁占用环境或指定新端口 |
| `test_load` | `LoadgenToolNotAvailableException` | 压测机上未安装该工具 | 在 loadgen host 上安装 wrk/hey |
| `test_exec_command` | `CommandExecutionException` | 命令执行超时或非零退出 | 检查命令语法，查看 raw_output 详情 |
| `session_conclude` | `NoEvidenceException` | 会话无证据无法结论 | 先调用 `session_record_evidence` |

---

## 五、生产环境建议

### 5.1 使用 systemd 管理 MCP Server

创建 `/etc/systemd/system/devops-mcp.service`：

```ini
[Unit]
Description=DevOps Dashboard MCP Server
After=network.target docker.service
Wants=docker.service

[Service]
Type=simple
User=dev
Group=dev
WorkingDirectory=/home/dev/devops-dashboard
ExecStart=/usr/bin/java \
  -Dspring.profiles.active=mcp \
  -Dserver.port=8081 \
  -Xms512m \
  -Xmx1024m \
  -jar /home/dev/devops-dashboard/target/devops-dashboard-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=devops-mcp

# 安全加固
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/home/dev/devops-dashboard/logs

# 资源限制
LimitNOFILE=65536
MemoryMax=2G

[Install]
WantedBy=multi-user.target
```

启用并启动：

```bash
sudo systemctl daemon-reload
sudo systemctl enable devops-mcp
sudo systemctl start devops-mcp
sudo systemctl status devops-mcp

# 查看日志
journalctl -u devops-mcp -f
```

### 5.2 使用 Nginx 反向代理（可选）

统一入口、域名访问、HTTPS 终结：

```nginx
server {
    listen 80;
    server_name devops.local;

    location /mcp/sse {
        proxy_pass http://127.0.0.1:8081/mcp/sse;
        proxy_http_version 1.1;
        proxy_set_header Connection '';
        proxy_set_header X-Accel-Buffering no;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 86400s;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /actuator/health {
        proxy_pass http://127.0.0.1:8081/actuator/health;
    }

    location / {
        return 404;
    }

    access_log /var/log/nginx/devops-mcp.access.log;
    error_log /var/log/nginx/devops-mcp.error.log;
}
```

使用 Nginx 后，AI 客户端 URL 改为 `http://devops.local/mcp/sse`。

### 5.3 生产环境安全 Checklist

| 项目 | 建议 | 说明 |
|------|------|------|
| Bearer Token 认证 | ✅ 启用 | 在 `application-mcp.yml` 中配置 `mcp.server.auth` |
| HTTPS | ✅ 启用 | 通过 Nginx + Let's Encrypt 终结 TLS |
| 网络隔离 | ✅ 限制来源 | 防火墙仅开放 8081 给可信网段 |
| 命令白名单 | ✅ 配置 | 使用 `devops.security.exec-command.allowed-patterns` |
| 非 root 用户 | ✅ 必须使用 | systemd Service 中 User=dev |
| 日志审计 | ✅ 开启 | journalctl 持久化 + 定期归档 |
| 资源限制 | ✅ 配置 | MemoryMax / LimitNOFILE 防止 OOM |
| 自动重启 | ✅ 启用 | Restart=always + RestartSec=10 |

---

## 六、快速参考卡

### Trae MCP 配置（SSE 远程连接）

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

### 启动命令（独立进程模式）

```bash
nohup java -Dspring.profiles.active=mcp -Dserver.port=8081 \
  -jar devops-dashboard-0.0.1-SNAPSHOT.jar > logs/mcp-server.log 2>&1 &
echo $! > mcp-server.pid
```

### 停止命令

```bash
kill -15 $(cat mcp-server.pid)
# 或强制: kill -9 $(cat mcp-server.pid)
```

### 验证命令

```bash
# 本地验证
curl -N http://localhost:8081/mcp/sse

# 远程验证
curl -N http://10.0.0.102:8081/mcp/sse

# 端口监听
ss -tlnp | grep 8081
```

### 防火墙命令

```bash
sudo firewall-cmd --add-port=8081/tcp --permanent
sudo firewall-cmd --reload
```

### 日志查看

```bash
# 实时日志
tail -f logs/mcp-server.log | grep -E "ERROR|WARN|MCP|Started"

# systemd 日志（生产环境）
journalctl -u devops-mcp -f
```

### 关键端口速查

| 端口 | 服务 | 用途 |
|------|------|------|
| 8080 | REST API | 前端/外部 HTTP 接口 |
| 8081 | MCP SSE | AI Client (Trae/Claude) 连接 |
| 22 | SSH | 所有节点间通信基础 |
