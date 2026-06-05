# Migration Fault Injection — 运行记录

> 每次模拟的运行结果、数据分析、发现的问题和处理方案。
> 固定流程文档见 `migration-fault-spec.md`。

---

## 运行 #1 — 2026-06-04 12:18

### 运行环境

| 项目 | 值 |
|------|-----|
| 时间 | 2026-06-04 12:18:40 ~ 12:21:55 |
| 种子数据 | 200 条 (userId 0-49 均匀分布) |
| MySQL master | sharding-master (3307), healthy |
| MySQL slave | sharding-slave (3308), IO=Yes, SQL=No (初始) |
| 应用版本 | P0-P4 全部已实现 |
| 指标快照 | `metrics-20260604-121840/` (44 个 JSON) |

### 阶段流转

```
Seed (200 → oldDb=502) → SYNCING (快照 maxId=501)
  → ⏱ 120s 超时等待 snapshotCaughtUp (migratedRecords=0, 无进展)
  → DOUBLE_WRITE (被拒: "snapshot not caught up")
  → ADVANCE 10% (被拒: 仍在 SYNCING)
  → ADVANCE 30% (被拒: 仍在 SYNCING)
  → ⚡ FAULT: docker stop sharding-slave
  → 故障读测试 (userId=1 ✅500, userId=2 ❌500)
  → ROLLBACK (phase→ADVANCING, advancePercent→0)
  → RECOVER: docker start + 复制 SQL=No (超时)
  → ADVANCE 50% (成功, phase→ADVANCING)
  → ADVANCE 100% (成功, phase→COMPLETE)
  → VERIFY: drift=198 (oldDb=502, shard=304)
```

### 指标矩阵

| 指标 | Seed | SYNCING | Fault | Rollback | Recover | ADV 50% | COMPLETE |
|------|------|---------|-------|----------|---------|---------|----------|
| phase | IDLE | SYNCING | SYNCING | ADVANCING | ADVANCING | ADVANCING | COMPLETE |
| advancePercent | 0 | 0 | 0 | 0 | 0 | 50 | 100 |
| migratedRecords | 0 | **0** | **0** | 0 | 0 | 0 | 0 |
| snapshotCaughtUp | false | false | false | false | false | false | false |
| snapshotLag | 0 | 501 | 501 | 501 | 501 | 501 | 501 |
| oldDbRows | 502 | 502 | 502 | 502 | 502 | 502 | 502 |
| shardRows | 304 | 304 | 304 | 304 | 304 | 304 | 304 |
| consistency | N/A | DRIFT | DRIFT | DRIFT | DRIFT | DRIFT | DRIFT(198) |

### 故障窗口行为

| 测试 | userId | 目标分片 | 预期 | 实际 | 诊断 |
|------|--------|---------|------|------|------|
| 读 | 1 | ds1 (slave) | 500 | **500** | ✅ ShardingSphere 无法连接 ds1 |
| 读 | 2 | ds0 (master) | 200 | **500** | ❌ ShardingSphere 全分片失败 |
| 写 | 1 | ds1 | 部分失败 | **500** | AOP 双写失败, 补偿表有记录? |

### 发现的问题

#### 问题 1: migratedRecords=0 — migrateBatch() 主键冲突

**现象**: 迁移进入 SYNCING 后, 120 秒内 migratedRecords 始终为 0。

**根因**:
```
migrateBatch() 从旧库读 id=1..20 的订单
  → saveToShard() → mapper.insert(so)
    → INSERT INTO orders_X VALUES (1, ...)
      → Duplicate entry '1' for key 'PRIMARY'
        → DuplicateKeyException → catch(Exception) 记日志
          → cursor 不推进 → 下次 @Scheduled 重试 → 又失败 → 死循环
```

分片表 (`orders_0/1`) 中已有 id=1,2,3 的初始数据 (来自 `01-init.sql`), 与旧库主键冲突。

**处理**: 修改 `saveToShard()` 捕获 `DuplicateKeyException` 并跳过 (幂等迁移)。见下个版本验证。

#### 问题 2: userId=2 读在故障窗口也返回 500

**现象**: ds1 宕机时, userId=2 (路由到 ds0, master 健康) 也返回 500。

**根因**: ShardingSphere-JDBC 在获取连接时, 可能因任一 shard 不可用导致全局阻塞或快速失败。具体行为取决于 `maxConnectionsSizePerQuery` 等连接池配置。

**影响**: P3 读降级未生效 (降级在 DataSource 层, 但 ShardingSphere 的异常在更上层抛出)。

**待处理**: 需在 Controller/Service 层增加全局异常处理, 或调整 ShardingSphere 连接超时。

#### 问题 3: 从库复制 SQL 线程未启动

**现象**: 恢复后 `Slave_SQL_Running=No` 持续 60 秒超时。

**根因**: slave 重启后 GTID 复制 IO 线程自动连接, 但 SQL 线程可能需要手动 `START SLAVE SQL_THREAD` 或因 relay log 不连续而停止。

**处理**: 脚本中增加 `START SLAVE` 命令确保两个线程都启动。

#### 问题 4: 一致性漂移 drift=198

**现象**: 旧库 502 行, 分片 304 行。

**分析**:
- 分片初始有 301+3=304 行 (来自之前的种子数据 + init.sql)
- 旧库有 301+200+1=502 行 (之前种子 + 本次种子 + 故障窗口的 1 次写入)
- migrateBatch 完全未工作 → 新种子数据全在旧库
- 故障窗口写入 (如果成功到达旧库) 也未双写

**处理**: 修复 migrateBatch 幂等后应自动同步。恢复后的一致性依赖 compensateBatch() 补充故障窗口遗漏。

---

## 修复记录

### 修复 1: migrateBatch 幂等 (2026-06-04)

**文件**: `MigrationService.java`

```java
// saveToShard() 修改前:
mapper.insert(so);

// 修改后:
try {
    mapper.insert(so);
} catch (DuplicateKeyException e) {
    return; // 幂等跳过
}
```

同样对 order_items 的 insert 增加幂等保护。

**预期效果**: 下次模拟 migratedRecords 正常推进, snapshotCaughtUp 可达。

### 修复 2: 模拟初始清表 (2026-06-04)

**文件**: `migration-fault-sim.sh`

新增 `phase_reset_tables()`:
```sql
TRUNCATE TABLE orders_0; TRUNCATE TABLE orders_1;
TRUNCATE TABLE order_items_0; TRUNCATE TABLE order_items_1;
TRUNCATE TABLE orders; TRUNCATE TABLE migration_compensation;
UPDATE migration_state SET phase='IDLE', ...;
```

运行方式: `--reset-only` 仅清表, `--cleanup` 清表+恢复容器。

**预期效果**: 每次模拟从干净状态开始, 消除累积数据干扰。

### 修复 3: 从库 SQL 线程自动恢复 (2026-06-04)

**文件**: `migration-fault-sim.sh`

恢复阶段增加 `docker exec sharding-slave mysql ... -e "START SLAVE;"` 确保 IO 和 SQL 线程都启动。

---

## 下次模拟计划

1. 验证 migrateBatch 幂等修复
2. 验证 snapshotCaughtUp → DOUBLE_WRITE 正常流转
3. 验证补偿表在故障窗口的写入和恢复
4. 调查 userId=2 读降级在 ShardingSphere 层的可行性
5. 修复从库复制自动恢复
