# DevOps Dashboard API - 完整使用指南

> **版本**: v1.0 (Phase 1)
> **最后更新**: 2026-05-18
> **基础URL**: http://localhost:8080
> **Swagger UI**: http://localhost:8080/swagger-ui.html

---

## 📖 目录

1. [快速开始](#快速开始)
2. [核心概念](#核心概念)
3. [API 详解](#api-详解)
4. [完整示例](#完整示例)
5. [最佳实践](#最佳实践)
6. [错误处理](#错误处理)

---

## 快速开始

### 最小化请求（30秒上手）

只需提供**名称+类型**，其他字段都有智能默认值：

```bash
curl -X POST http://localhost:8080/api/v1/environments \
  -H "Content-Type: application/json" \
  -d '{
    "name": "dev-nacos",
    "type": "DEV"
  }'
```

**响应**：
```json
{
  "id": "env-a1b2c3d4",
  "name": "dev-nacos",
  "type": "DEV",
  "status": "CREATING",
  "createdAt": "2026-05-18T23:05:00",
  "resourceQuota": {
    "cpuRequest": "500m",
    "cpuLimit": "2000m",
    "memoryRequest": "512Mi",
    "memoryLimit": "2Gi"
  },
  "lifecyclePolicy": {
    "autoDestroy": false,
    "maxLifetime": "24h",
    "idleTimeout": "2h",
    "destroyOnFailure": true
  },
  "serviceCount": 0,
  "accessEndpoints": {}
}
```

> ✅ **系统自动填充了默认值！**

---

## 核心概念

### 1️⃣ 环境（Environment）

环境是**一组相关服务的集合**，代表一个完整的运行时上下文。

**类比理解**：
- 就像 IDE 的"运行配置"：一个环境 = 一组服务 + 资源限制 + 生命周期策略
- 例如："dev-nacos" 环境可能包含 Nacos + MySQL + Redis 这3个服务

**为什么需要环境？**
| 问题 | 解决方案 |
|------|---------|
| 服务冲突 | 不同项目用不同端口/实例 |
| 资源隔离 | 防止开发环境占用生产资源 |
| 一键清理 | 销毁环境 = 清理所有关联资源 |
| 可复现性 | 固定配置保证环境一致性 |

### 2️⃣ 环境类型（EnvironmentType）

| 类型 | 枚举值 | 适用场景 | 默认资源配额 | 推荐生命周期 |
|------|--------|---------|-------------|-------------|
| 开发环境 | `DEV` | 日常开发调试 | CPU: 500m~2000m, 内存: 512Mi~2Gi | 24小时 |
| 测试环境 | `TEST` | 功能测试/QA验证 | CPU: 1000m~4000m, 内存: 2Gi~8Gi | 永久 |
| 预发布 | `STAGING` | 上线前最终验证 | CPU: 1500m~6000m, 内存: 4Gi~12Gi | 永久 |
| 生产环境 | `PROD` | 正式对外服务 | CPU: 2000m~8000m, 内存: 4Gi~16Gi | 永久 |
| 实验环境 | `EXPERIMENT` | Spike 技术验证 | CPU: 1000m~4000m, 内存: 2Gi~8Gi | **2小时** |

**选择指南**：
- 个人学习 → 用 `DEV`
- 团队测试 → 用 `TEST`
- 验证新技术 → 用 `EXPERIMENT`（会自动销毁）
- ⚠️ 不要用 `PROD`，除非你真的要部署到生产！

### 3️⃣ 环境状态机（EnvironmentStatus）

```
                    创建成功
    CREATING ──────────────→ RUNNING
       │                      │
       │ 失败                 │ 停止
       ↓                      ↓
     FAILED                STOPPED
       │                      │
       │                      │ 销毁
       └──────────→ DESTROYED ←┘
                   （终态）
```

| 状态 | 含义 | 允许的操作 |
|------|------|-----------|
| `CREATING` | 正在分配资源、启动容器 | → RUNNING / FAILED |
| `RUNNING` | 运行中，可正常访问 | → STOPPED / DESTROYED / FAILED |
| `STOPPED` | 已停止（资源保留） | → RUNNING / DESTROYED |
| `DESTROYED` | 已销毁（数据保留） | ❌ 终态，不可操作 |
| `FAILED` | 创建/运行失败 | → DESTROYED |

### 4️⃣ 资源配额（ResourceQuota）

控制环境可以使用的 **CPU 和内存上限**。

**字段详解**：

| 字段 | 类型 | 说明 | 示例值 |
|------|------|------|--------|
| `cpuRequest` | String | **最低**保证的 CPU（调度器优先满足） | `"500m"` = 0.5核 |
| `cpuLimit` | String | **最高**允许的 CPU（突发时可超发） | `"2000m"` = 2核 |
| `memoryRequest` | String | **最低**保证的内存 | `"512Mi"` = 512MB |
| `memoryLimit` | String | **最高**允许的内存（超出会被 OOM Kill） | `"2Gi"` = 2GB |

**单位速查表**：

| 单位 | 含义 | 换算 |
|------|------|------|
| `m` | millicores (毫核) | 1000m = 1核 = 1000m |
| `Mi` | Mebibyte (兆) | 1024Mi = 1Gi ≈ 1GB |
| `Gi` | Gibibyte (吉) | 1Gi = 1024Mi |

**推荐配置模板**：

```json
// 轻量级（个人开发）
{
  "cpuRequest": "250m",
  "cpuLimit": "1000m",
  "memoryRequest": "256Mi",
  "memoryLimit": "1Gi"
}

// 标准级（团队协作）
{
  "cpuRequest": "500m",
  "cpuLimit": "2000m",
  "memoryRequest": "512Mi",
  "memoryLimit": "2Gi"
}

// 重载级（压测/大数据）
{
  "cpuRequest": "2000m",
  "cpuLimit": "8000m",
  "memoryRequest": "4Gi",
  "memoryLimit": "16Gi"
}
```

> 💡 **提示**: Request 应该 ≤ Limit，否则 Kubernetes/Docker 可能拒绝调度。

### 5️⃣ 生命周期策略（LifecyclePolicy）

控制环境的**自动管理行为**。

**字段详解**：

| 字段 | 类型 | 说明 | 推荐值 |
|------|------|------|--------|
| `autoDestroy` | boolean | 到期后是否自动销毁？ | 实验=`true`, 其他=`false` |
| `maxLifetime` | string | 最大存活时间（之后触发警告或销毁） | `"24h"`, `"2h"`, `"7d"` |
| `idleTimeout` | string | 无操作多长时间算"空闲"（触发警告） | `"2h"`, `"30m"` |
| `destroyOnFailure` | boolean | 创建失败时是否自动清理残留资源？ | 推荐 `true` |

**时间格式**：
- 支持: `30m`(30分钟), `2h`(2小时), `24h`(1天), `7d`(7天)
- 不支持: 复杂表达式如 "1d12h"

**场景化配置**：

#### 场景 A: 日常开发环境
```json
{
  "autoDestroy": false,
  "maxLifetime": "24h",
  "idleTimeout": "2h",
  "destroyOnFailure": true
}
```
**效果**: 
- 不会自动删除（防止误删）
- 超过24小时提醒你考虑是否还需要
- 2小时没用就提醒（避免浪费资源）
- 创建失败自动清理（不留垃圾）

#### 场景 B: Spike 实验环境（推荐）
```json
{
  "autoDestroy": true,
  "maxLifetime": "2h",
  "idleTimeout": "30m",
  "destroyOnFailure": true
}
```
**效果**:
- ⚡ **2小时后自动销毁**（防止实验环境变僵尸）
- 30分钟没操作就警告
- 失败立即清理

#### 场景 C: 生产环境
```json
{
  "autoDestroy": false,
  "maxLifetime": null,      // 或超长时间如 "365d"
  "idleTimeout": null,        // 不设空闲超时
  "destroyOnFailure": false   // 失败需要人工介入排查
}
```
**效果**:
- 绝不自动销毁（安全第一）
- 需要人工确认才能删除

### 6️⃣ 目标节点（TargetNodes）

指定环境应该部署在哪些机器上。

**当前版本限制**：
- ✅ 可以记录节点信息到数据库
- ❌ 不会实际调度（Phase 2 实现 DockerComposeProvider 后生效）
- 🔮 预留接口，为未来多机部署做准备

**什么时候填？**
- 单机开发 → **不填**（留空数组即可）
- 多机集群 → 填写目标机器信息
- 特殊硬件需求 → 指定有 GPU/SSD 的节点

---

## API 详解

### POST /api/v1/environments - 创建环境

**用途**: 从零开始创建一个新的运行环境。

**何时调用**:
- 开始新项目开发前
- 需要 isolated 测试环境时
- 进行 Spike 实验验证技术假设时

**请求体 Schema**:

```json
{
  // === 必填字段 ===
  
  "name": "string",           // [必填] 环境名，建议格式：<类型>-<用途>
  "type": "DEV",              // [必填] 环境类型枚举
  
  // === 可选字段（不填则用默认值）===
  
  "resourceQuota": { ... },    // 资源配额（见上文说明）
  "lifecyclePolicy": { ... },  // 生命周期策略（见上文说明）
  "targetNodes": [ ... ]      // 目标节点列表（见上文说明）
}
```

**响应 Schema**:

```json
{
  "id": "env-a1b2c3d4",        // 系统生成的唯一ID（后续操作要用）
  "name": "dev-nacos",          // 你提供的名字
  "type": "DEV",               // 环境类型
  "status": "CREATING",         // 当前状态（刚创建都是 CREATING）
  "createdAt": "2026-...",      // 创建时间戳
  "resourceQuota": { ... },     // 实际生效的资源配额（可能是默认值）
  "lifecyclePolicy": { ... },   // 实际生效的生命周期策略
  "accessEndpoints": {},        // 访问地址（Phase 2 填充，如 Nacos 的 8848 端口）
  "serviceCount": 0             // 当前包含的服务数量（刚创建为0）
}
```

**HTTP 状态码**:

| 状态码 | 含义 | 触发条件 |
|--------|------|---------|
| `201 Created` | 成功创建 | 返回新环境对象 |
| `400 Bad Request` | 参数错误 | name 为空、type 不合法等 |
| `500 Internal Error` | 服务端异常 | 数据库连接失败等 |

---

### GET /api/v1/environments - 查询环境列表

**用途**: 获取所有环境的信息概览。

**查询参数**:

| 参数 | 必填 | 说明 | 示例 |
|------|------|------|------|
| `status` | 否 | 按状态筛选 | `?status=RUNNING` |

**响应**: 环境对象数组（每个对象结构与 POST 响应相同）

**使用场景**:
- 查看当前有哪些环境在运行
- 找出某个状态的坏境（如所有 FAILED 的）
- 统计资源占用情况

---

### GET /api/v1/environments/{id} - 获取环境详情

**用途**: 获取单个环境的完整信息。

**路径参数**:

| 参数 | 说明 | 示例 |
|------|------|------|
| `id` | 环境ID（从创建响应获取） | `env-a1b2c3d4` |

**响应**: 单个环境对象（包含所有字段）

**使用场景**:
- 查看某环境的具体资源配置
- 获取访问端点地址（Phase 2 后有用）
- 检查环境当前状态

---

### DELETE /api/v1/environments/{id} - 销毁环境

**用途**: 彻底销毁一个环境及其所有关联资源。

**⚠️ 危险操作警告**:
- 此操作**不可逆**
- 会停止并删除所有关联的服务实例
- 数据库记录标记为 DESTROYED（但记录保留用于审计）

**路径参数**:

| 参数 | 说明 |
|------|------|
| `id` | 要销毁的环境ID |

**HTTP 状态码**:

| 状态码 | 含义 |
|--------|------|
| `204 No Content` | 销毁成功（无返回内容） |
| `404 Not Found` | 环境不存在 |
| `409 Conflict` | 环境状态不允许销毁（如已经是 DESTROYED） |

**前置条件检查**:
- ✅ 环境必须存在
- ✅ 环境不能已是 DESTROYED 状态
- ⚠️ 如果环境是 RUNNING 状态，会先尝试 STOP 再 DESTROY

---

## 完整示例

### 示例 1: 创建最小化开发环境

**请求**:
```bash
POST /api/v1/environments
Content-Type: application/json

{
  "name": "dev-myproject",
  "type": "DEV"
}
```

**响应 (201)**:
```json
{
  "id": "env-x7y9z0w1",
  "name": "dev-myproject",
  "type": "DEV",
  "status": "CREATING",
  "createdAt": "2026-05-19T10:30:00",
  "resourceQuota": {
    "cpuRequest": "500m",
    "cpuLimit": "2000m",
    "memoryRequest": "512Mi",
    "memoryLimit": "2Gi"
  },
  "lifecyclePolicy": {
    "autoDestroy": false,
    "maxLifetime": "24h",
    "idleTimeout": "2h",
    "destroyOnFailure": true
  },
  "accessEndpoints": {},
  "serviceCount": 0
}
```

**解析**:
- 只提供了 name 和 type
- 系统自动填充了 DEV 类型的默认配额
- status 是 CREATING（等待 Phase 2 的 Provider 实际启动容器）

---

### 示例 2: 创建带自定义配置的实验环境

**请求**:
```bash
POST /api/v1/environments
Content-Type: application/json

{
  "name": "exp-kafka-vs-rabbitmq",
  "type": "EXPERIMENT",
  "resourceQuota": {
    "cpuRequest": "1000m",
    "cpuLimit": "4000m",
    "memoryRequest": "2Gi",
    "memoryLimit": "8Gi"
  },
  "lifecyclePolicy": {
    "autoDestroy": true,
    "maxLifetime": "2h",
    "idleTimeout": "30m",
    "destroyOnFailure": true
  }
}
```

**设计意图**:
- 名称体现实验目的：对比 Kafka vs RabbitMQ
- 类型是 EXPERIMENT → 自动销毁机制
- 资源给得比较大方（消息队列测试需要内存）
- 2小时后自动清理（防止忘记删）

---

### 示例 3: 查询并销毁环境

**Step 1: 查询列表**
```bash
GET /api/v1/environments?status=RUNNING
```

**响应**:
```json
[
  {
    "id": "env-old-env",
    "name": "test-abandoned",
    "status": "RUNNING",
    ...
  }
]
```

**Step 2: 销毁它**
```bash
DELETE /api/v1/environments/env-old-env
```

**响应**: `204 No Content` (无内容)

**验证**:
```bash
GET /api/v1/environments/env-old-env
# → 404 Not Found (已销毁)
```

---

## 最佳实践

### ✅ 推荐做法

#### 1. 命名规范

| 场景 | 命名格式 | 示例 |
|------|---------|------|
| 开发环境 | `dev-<项目名>` | `dev-nacos`, `dev-spring-cloud` |
| 测试环境 | `test-<模块名>` | `test-user-service`, `test-payment` |
| 实验环境 | `exp-<研究主题>` | `exp-grpc-vs-rest`, `exp-reactive` |
| 生产环境 | `prod-<服务名>` | `prod-api-gateway` |

**好处**:
- 一眼看出环境和用途
- 方便搜索过滤
- 避免命名冲突

#### 2. 资源配额原则

**开发环境**（够用就行）:
```json
{
  "cpuRequest": "250m",
  "cpuLimit": "1000m",
  "memoryRequest": "256Mi",
  "memoryLimit": "1Gi"
}
```

**实验环境**（按需分配）:
- I/O 密集型（数据库测试）→ 多给内存少给CPU
- CPU 密集型（编译/计算）→ 多给CPU
- 网络密集型（消息队列）→ 均衡配置

#### 3. 生命周期管理

**黄金法则**:
- 开发环境：**不自动销毁**（你可能离开一会儿再回来）
- 实验环境：**必须自动销毁**（防止僵尸环境占资源）
- 生产环境：**永不自动销毁**（安全第一）

### ❌ 常见错误

#### 错误 1: type 字段用了错误的枚举值

❌ **错误**:
```json
{ "type": "DEVELOPMENT" }  // 不存在这个值！
```

✅ **正确**:
```json
{ "type": "DEV" }  // 正确的枚举值
```

**可用值**: `DEV`, `TEST`, `STAGING`, `PROD`, `EXPERIMENT`

---

#### 错误 2: 资源配额 Request > Limit

❌ **危险**:
```json
{
  "cpuRequest": "2000m",  // 要求 2 核
  "cpuLimit": "1000m"     // 但只允许 1 核？
}
```

✅ **正确**:
```json
{
  "cpuRequest": "500m",   // 至少 0.5 核
  "cpuLimit": "2000m"    // 最多 2 核（突发可用）
}
```

**后果**: Kubernetes/Docker 可能直接拒绝调度该容器。

---

#### 错误 3: 忘记设置实验环境的 autoDestroy

❌ **风险**:
```json
{
  "type": "EXPERIMENT",
  "lifecyclePolicy": {
    "autoDestroy": false,  // ← 忘记开了！
    "maxLifetime": "2h"
  }
}
```

**后果**: 2小时后只会收到警告，但环境不会自动删除。日积月累产生大量僵尸环境。

✅ **修正**:
```json
{
  "type": "EXPERIMENT",
  "lifecyclePolicy": {
    "autoDestroy": true,   // ← 实验环境必须开！
    "maxLifetime": "2h"
  }
}
```

---

## 错误处理

### HTTP 状态码速查

| 状态码 | 含义 | 用户操作 |
|--------|------|---------|
| `200 OK` | 请求成功 | 读取返回数据 |
| `201 Created` | 创建成功 | 从 Location header 或 body 获取新资源 ID |
| `204 No Content` | 操作成功（无返回） | 如 DELETE 操作 |
| `400 Bad Request` | 参数校验失败 | 检查请求体格式和必填字段 |
| `404 Not Found` | 资源不存在 | 检查 ID 是否正确 |
| `409 Conflict` | 状态冲突 | 如试图销毁已销毁的环境 |
| `500 Internal Error` | 服务端异常 | 查看日志或联系管理员 |

### 错误响应格式

所有错误都遵循统一格式：

```json
{
  "timestamp": "2026-05-19T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "具体错误原因（人类可读）",
  "path": "/api/v1/environments",
  "traceId": "abc-123-def"  // 用于日志追踪（如果有）
}
```

**常见 message 及解决方案**:

| 错误消息 | 原因 | 解决方法 |
|---------|------|---------|
| `环境名称不能为空` | name 字段缺失或空字符串 | 添加 name 字段 |
| `环境类型不能为空` | type 字段缺失 | 添加 type 字段（必须是合法枚举值） |
| `Environment not found: xxx` | ID 不存在 | 先用 GET 列表查询正确的 ID |
| `Cannot transition from X to Y` | 状态转换非法 | 检查当前状态，参考状态机图 |

---

## 下一步功能预告（Phase 2）

当前版本（v1.0）实现了**环境 CRUD 的数据层**，后续将添加：

| 功能 | 版本 | 说明 |
|------|------|------|
| 🐳 Docker Compose 集成 | v1.1 | 创建环境时实际启动容器 |
| 📦 服务部署 | v1.2 | 向环境添加 Nacos/MySQL等服务 |
| 📊 实时监控 | v1.3 | 查看 CPU/内存使用率 |
| 📝 日志流 | v1.4 | 实时查看容器日志 |
| 🧪 实验管理 | v2.0 | 完整的 Spike 实验生命周期 |

---

## 附录：命令行快捷方式

保存为 shell 脚本方便复用：

```bash
#!/bin/bash
# env-api.sh - DevOps Dashboard API 快捷脚本

BASE_URL="http://localhost:8080/api/v1"

# 创建环境
create_env() {
  curl -X POST "$BASE_URL/environments" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$1\",\"type\":\"$2\"}"
}

# 查看所有环境
list_envs() {
  curl -s "$BASE_URL/environments" | jq '.'
}

# 查看单个环境
get_env() {
  curl -s "$BASE_URL/environments/$1" | jq '.'
}

# 销毁环境
destroy_env() {
  curl -X DELETE "$BASE_URL/environments/$1" -w "\nHTTP Status: %{http_code}\n"
}

# 使用示例
# ./env-api.sh create dev-nacos DEV
# ./env-api.sh list
# ./env-api.sh get env-a1b2c3d4
# ./env-api.sh destroy env-a1b2c3d4

case "$1" in
  create) create_env $2 $3 ;;
  list) list_envs ;;
  get) get_env $2 ;;
  destroy) destroy_env $2 ;;
  *) echo "用法: $0 {create\|list\|get\|destroy} [args...]" ;;
esac
```

---

**文档结束**。如有疑问，请查看 Swagger UI 的交互式文档或查阅代码注释。
