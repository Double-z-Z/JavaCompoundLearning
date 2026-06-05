# Migration Fault Injection Simulation — 规范文档

> 本文档描述模拟单元的固定流程：架构设计、迁移状态机、API 交互、故障注入方法。
> 每次运行的动态结果见 `migration-fault-results.md`。

---

## 1. 架构概览

### 1.1 系统拓扑

```
┌─────────────────────────────────────────────────────────┐
│  Spring Boot App (port 8089)                            │
│                                                         │
│  OrderController  FeatureDemoController                 │
│  MigrationSimulationController  MigrationController     │
│  MetricsController  HealthController                    │
│                                                         │
│  MigrationRoutingDataSource (@Primary DataSource)       │
│    ├─ oldDs → singleDbDataSource (HikariCP → 3307)     │
│    └─ newDs → ShardingSphere (ds0:3307 + ds1:3308)     │
│                                                         │
│  MigrationService (state machine + @Scheduled jobs)     │
│  MigrationWriteAspect (AOP double-write)                │
└─────────────────────────────────────────────────────────┘

┌──────────────────┐    ┌──────────────────┐
│ mysql-master     │    │ mysql-slave      │
│ (ds0, port 3307) │◄───│ (ds1, port 3308) │
│                  │ GTID│                  │
│ orders (old)     │    │ orders_0, orders_1│
│ orders_0, orders_1│   │ order_items_0,1 │
│ order_items_0,1  │    │ t_user           │
│ t_user           │    └──────────────────┘
└──────────────────┘
```

### 1.2 迁移状态机

```
IDLE ──start──→ SYNCING ──snapshotCaughtUp──→ DOUBLE_WRITE
                    │                              │
                    │ batch error (retry)          ├─ advance(10%)──→ ADVANCING(10%)
                    ▼                              │       │
              (cursor 不推进)                      │       ├─ advance(30%)
                                                   │       │       │
                                                   │       │   [故障注入: docker stop slave]
                                                   │       │       │
                                                   │       │   rollback(shard=1)
                                                   │       │       │ advancePercent -= 10
                                                   │       │       │
                                                   │       │   [恢复: docker start slave]
                                                   │       │       │
                                                   │       ├─ advance(50%)
                                                   │       ├─ advance(100%) → COMPLETE
                                                   │
                                                   └─ double-write: AOP 双写
```

### 1.3 分片规则

| 维度 | 规则 | 说明 |
|------|------|------|
| 数据库 | `user_id % 2` | ds0 (偶数), ds1 (奇数) |
| 表 | `(user_id % 4).intdiv(2)` | orders_0 / orders_1 |
| 绑定表 | orders ↔ order_items | 同分片键，不跨片 JOIN |
| 广播表 | t_user | 每分片全量复制 |
| 主键 | SNOWFLAKE | ShardingSphere 内置 |

---

## 2. API 交互文档

### 2.1 迁移模拟 API (`/api/v2/migration-sim`)

| 方法 | 路径 | 参数 | 作用 |
|------|------|------|------|
| POST | `/seed` | `count` (default 200) | 向旧库灌测试数据 |
| POST | `/start` | — | IDLE → SYNCING, 拍摄快照 |
| POST | `/double-write` | — | SYNCING → DOUBLE_WRITE |
| POST | `/advance` | `percent` (int) | 设置灰度百分比 |
| POST | `/complete` | — | advance(100) 快捷方式 |
| POST | `/rollback` | `shard` (int) | 回退指定分片 |
| POST | `/pause` | — | 暂停 |
| GET | `/status` | — | 完整迁移状态 |
| GET | `/verify` | — | 新旧库行数对比 |

### 2.2 监控 API (`/api/v2/metrics`)

| 方法 | 路径 | 返回 |
|------|------|------|
| GET | `/shards` | 拓扑 + 行数 + HikariCP 连接池 |
| GET | `/migration` | 阶段 + 进度 + 延迟 + 补偿状态 |

### 2.3 业务 API (`/api/v2/orders`)

| 方法 | 路径 | 参数 | 用途 |
|------|------|------|------|
| POST | `/orders` | JSON body | 创建订单（故障窗口测试写入） |
| GET | `/orders` | `userId` | 按 userId 查询（故障窗口测试读取） |

---

## 3. 模拟流程

### 3.1 标准流程 (14 阶段)

| # | 阶段 | 操作 | API/命令 |
|---|------|------|----------|
| 1 | 环境检查 | 验证 Docker/App/复制/迁移开关 | `docker ps`, `/actuator/health`, `/metrics/migration` |
| 2 | 播种 | 向旧库灌 N 条订单 | `POST /migration-sim/seed?count=N` |
| 3 | 启动迁移 | IDLE → SYNCING | `POST /migration-sim/start` |
| 4 | 等待同步 | 轮询至 snapshotCaughtUp=true | `GET /metrics/migration` |
| 5 | 开启双写 | SYNCING → DOUBLE_WRITE | `POST /migration-sim/double-write` |
| 6 | 灰度 10% | advancePercent=10 | `POST /migration-sim/advance?percent=10` |
| 7 | 灰度 30% | advancePercent=30 | `POST /migration-sim/advance?percent=30` |
| **8** | **故障注入** | **停 ds1 容器** | **`docker stop sharding-slave`** |
| 9 | 故障读取 | 验证 ds1 路由失败, ds0 行为 | `GET /orders?userId=1`, `GET /orders?userId=2` |
| 10 | 回退 | 回退分片 1 | `POST /migration-sim/rollback?shard=1` |
| 11 | 恢复 | 启动 ds1 + 等复制追上 | `docker start sharding-slave` |
| 12 | 灰度 50% | 恢复后继续推进 | `POST /migration-sim/advance?percent=50` |
| 13 | 灰度 100% | 完成迁移 | `POST /migration-sim/advance?percent=100` |
| 14 | 校验 + 最终快照 | 一致性检查 | `GET /migration-sim/verify`, `/metrics/*` |

### 3.2 故障注入方法

```bash
# 注入: 停止 ds1 (模拟物理机故障)
docker stop sharding-slave

# 影响:
#   - ShardingSphere 到 ds1 (port 3308) 的所有连接断开
#   - userId % 2 == 1 → 路由到 ds1 → 读/写失败
#   - userId % 2 == 0 → 路由到 ds0 (master) → 可能也受 ShardingSphere 全局影响
#   - 旧单库 (singleDbDataSource, 独立连接池) → 不受影响

# 恢复: 重启 ds1
docker start sharding-slave
# 等待 MySQL 就绪 + GTID 复制追上
```

### 3.3 指标采集点

每个阶段结束后自动采集 4 个 JSON 快照到 `metrics-<timestamp>/` 目录:
- `shards-<label>-<time>.json` — 分片拓扑 + 连接池
- `migration-<label>-<time>.json` — 迁移状态
- `status-<label>-<time>.json` — 详细状态
- `verify-<label>-<time>.json` — 一致性校验

---

## 4. 解决方案清单

模拟中暴露的问题及其修复:

| # | 问题 | 方案 | 状态 |
|---|------|------|------|
| P0 | 双写故障时数据不一致 | 补偿表 + 异步重试 | ✅ 已实现 |
| P1 | rolledBackShards 不参与路由 | 路由层检测分片回退标记 | ✅ 已实现 |
| P2 | 迁移状态重启丢失 | migration_state 表持久化 | ✅ 已实现 |
| P3 | 故障时无读降级 | 5 秒探活缓存 fallback | ✅ 已实现 |
| P4 | 无分片健康检查 | ShardingHealthIndicator | ✅ 已实现 |

### 4.1 补偿机制 (P0)

```
MigrationWriteAspect.doubleWrite()
  → newShardSql.insert(order) 失败
    → recordCompensation(order, exception)
      → INSERT INTO migration_compensation (order_data, target_shard, cm_status='PENDING')
        → compensateBatch() 每 5 秒重试 PENDING 任务 (最多 3 次)
          → 成功: cm_status='COMPLETED'
          → 超限: cm_status='FAILED', error_msg 记录异常
```

### 4.2 分片级回退路由 (P1)

```java
// MigrationRoutingDataSource.determineCurrentLookupKey()
if (userId != null && migration.readFromNewShard(userId)) {
    int targetShard = (int) (userId % 2);
    if (migration.state.rolledBackShards.get(targetShard) != null) {
        return "oldDs";  // 分片已回退 → 读旧库
    }
    return "newDs";
}
```

### 4.3 状态持久化 (P2)

- 表: `migration_state` (单行, id=1)
- 写入: 每个状态变更末尾 `persist()` → `INSERT ... ON DUPLICATE KEY UPDATE`
- 恢复: `@PostConstruct recover()` → `SELECT * FROM migration_state WHERE id=1`

### 4.4 读降级 (P3)

- `MigrationRoutingDataSource.determineTargetDataSource()` 覆盖
- 5 秒间隔探活: `newDs.getConnection().isValid(1)`
- 不可用时直接返回 `oldDs`

### 4.5 健康检查 (P4)

- `ShardingHealthIndicator` → `/actuator/health` 暴露
- 通过 ShardingSphere 执行 `SELECT 1` 验证全分片可达

---

## 5. 运行模拟

```bash
# 前提: Docker 容器运行 + App 启动 (migration.active=true)

# 完整运行
bash simulations/migration-fault-sim.sh

# CI 模式 (无暂停)
bash simulations/migration-fault-sim.sh --no-pause

# 自定义参数
bash simulations/migration-fault-sim.sh --seed-count 500 --pause 1

# 仅清理
bash simulations/migration-fault-sim.sh --cleanup
```

指标快照输出: `simulations/metrics-<timestamp>/` (每次运行 44 个 JSON 文件)

---

## 6. 关键文件

| 文件 | 说明 |
|------|------|
| `migration/MigrationService.java` | 状态机 + 后台批次 + 补偿 |
| `migration/MigrationState.java` | 原子状态变量 |
| `migration/datasource/MigrationRoutingDataSource.java` | 读路由 + 降级 |
| `migration/interceptor/MigrationWriteAspect.java` | AOP 双写 + 补偿记录 |
| `infrastructure/config/ShardingHealthIndicator.java` | 分片健康检查 |
| `interfaces/rest/MetricsController.java` | 监控端点 |
| `interfaces/rest/MigrationSimulationController.java` | 迁移模拟 API |
| `simulations/migration-fault-sim.sh` | 模拟驱动脚本 |
