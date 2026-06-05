package com.example.order.migration;

import com.example.order.infrastructure.config.ShardSessionFactory;
import com.example.order.infrastructure.persistence.sharding.ShardOrder;
import com.example.order.infrastructure.persistence.sharding.ShardOrderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 迁移状态机 — 单库 → 分库分表.
 *
 * 快照游标模式:
 *   1. start(): SELECT MAX(id) → snapshotMaxId（快照）
 *   2. migrateBatch(): 逐批搬 id <= snapshotMaxId 的行
 *   3. cursorId >= snapshotMaxId → snapshotCaughtUp = true → 可进入双写
 *   4. 双写期间的新写入 (id > snapshotMaxId) 由 AOP 双写 + 补偿表覆盖
 *
 * 流程: IDLE → SYNCING → DOUBLE_WRITE → ADVANCING → COMPLETE
 */
@Service
@EnableScheduling
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "migration.active", havingValue = "true")
public class MigrationService {
    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);
    private static final int BATCH_SIZE = 20;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public final MigrationState state = new MigrationState();
    private volatile boolean doubleWriteEnabled = false;

    // 供 MyBatis Interceptor 静态访问（避免 Spring Bean 依赖循环）
    private static volatile MigrationService INSTANCE;
    public static boolean isDoubleWriteActive() {
        return INSTANCE != null && INSTANCE.doubleWriteEnabled;
    }

    private final JdbcTemplate oldJdbc;             // 读旧库 + 持久化
    private final ShardSessionFactory shardSession;  // 写分片

    public MigrationService(
            @Qualifier("singleDbDataSource") DataSource oldDs,
            @Qualifier("shardingDataSource") DataSource shardingDs) {
        this.oldJdbc = new JdbcTemplate(oldDs);
        this.shardSession = new ShardSessionFactory(shardingDs);
    }

    @PostConstruct
    void registerInstance() {
        INSTANCE = this;
        recover();
    }

    // ==================== 启动恢复 ====================

    private void recover() {
        try {
            var row = oldJdbc.queryForMap("SELECT * FROM migration_state WHERE id=1");
            if (row == null || row.get("phase") == null) return;

            String phaseStr = (String) row.get("phase");
            if ("IDLE".equals(phaseStr)) return; // nothing to recover

            state.phase.set(MigrationPhase.valueOf(phaseStr));
            state.advancePercent.set(getInt(row, "advance_percent"));
            state.migratedRecords.set(getLong(row, "migrated_records"));
            state.snapshotMaxId.set(getLong(row, "snapshot_max_id"));
            state.cursorId.set(getLong(row, "cursor_id"));
            state.snapshotCaughtUp.set(getBool(row, "snapshot_caught_up"));
            doubleWriteEnabled = getBool(row, "double_write_enabled");

            // 恢复 rolledBackShards
            String rbs = (String) row.get("rolled_back_shards");
            if (rbs != null && !rbs.isEmpty() && !"{}".equals(rbs)) {
                // 格式: "1:true,2:true"
                for (String part : rbs.replaceAll("[{}]", "").split(",")) {
                    if (part.isEmpty()) continue;
                    String[] kv = part.split(":");
                    if (kv.length == 2) state.rolledBackShards.put(Integer.parseInt(kv[0]), true);
                }
            }

            log.info("Migration state RECOVERED: phase={}, advancePercent={}, cursor={}/{}",
                    phaseStr, state.advancePercent.get(), state.cursorId.get(), state.snapshotMaxId.get());
        } catch (Exception e) {
            log.info("No prior migration state to recover (table may not exist yet)");
        }
    }

    // ==================== 命令 ====================

    /** 拍摄快照 + 启动全量搬迁 */
    public synchronized String start() {
        if (state.isActive()) return "Already in progress: " + state.phase.get();
        state.phase.set(MigrationPhase.SYNCING);
        state.migratedRecords.set(0);
        state.advancePercent.set(0);
        state.snapshotCaughtUp.set(false);
        state.rolledBackShards.clear();

        Long maxId = oldJdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM orders", Long.class);
        state.snapshotMaxId.set(maxId);
        state.cursorId.set(0);
        log.info("Migration STARTED. snapshotMaxId={}", maxId);
        persist();
        return state.statusJson();
    }

    /** 快照追平 → 进入双写 */
    public synchronized String startDoubleWrite() {
        if (state.phase.get() != MigrationPhase.SYNCING)
            return "Must be SYNCING, current: " + state.phase.get();
        if (!state.snapshotCaughtUp.get())
            return "Snapshot not caught up yet. cursor=" + state.cursorId.get()
                + ", snapshotMax=" + state.snapshotMaxId.get();
        state.phase.set(MigrationPhase.DOUBLE_WRITE);
        doubleWriteEnabled = true;
        log.info("-> DOUBLE_WRITE. snapshotCaughtUp=true, new writes covered by double-write.");
        persist();
        return state.statusJson();
    }

    public synchronized String advance(int targetPercent) {
        var p = state.phase.get();
        if (p != MigrationPhase.DOUBLE_WRITE && p != MigrationPhase.ADVANCING)
            return "Must be DOUBLE_WRITE or ADVANCING, current: " + p;
        if (targetPercent <= state.advancePercent.get())
            return "Already at " + state.advancePercent.get() + "%";
        if (targetPercent > 100) targetPercent = 100;
        state.phase.set(MigrationPhase.ADVANCING);
        state.advancePercent.set(targetPercent);
        if (targetPercent >= 100) {
            state.phase.set(MigrationPhase.COMPLETE);
            doubleWriteEnabled = false;
            log.info("Migration COMPLETE");
        }
        persist();
        return state.statusJson();
    }

    public String pause() { return "Paused at " + state.advancePercent.get() + "%"; }

    public synchronized String rollback(int shard) {
        if (!state.isActive() && state.phase.get() != MigrationPhase.COMPLETE) return "Nothing to rollback";
        state.phase.set(MigrationPhase.ROLLING_BACK);
        state.rolledBackShards.put(shard, true);
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        state.phase.set(MigrationPhase.ADVANCING);
        int reduced = Math.max(0, state.advancePercent.get() - 10);
        state.advancePercent.set(reduced);
        persist();
        return state.statusJson();
    }

    public String resumeShard(int shard) {
        state.rolledBackShards.remove(shard);
        persist();
        return state.statusJson();
    }

    public String status() { return state.statusJson(); }

    public boolean readFromNewShard(long userId) {
        if (state.phase.get() == MigrationPhase.COMPLETE) return true;
        if (!state.isActive()) return false;
        return (userId % 100) < state.advancePercent.get();
    }

    public boolean isDoubleWrite() { return doubleWriteEnabled; }

    // ==================== 后台搬迁 ====================

    @Scheduled(fixedDelay = 1000)
    void migrateBatch() {
        if (state.phase.get() != MigrationPhase.SYNCING) return;
        try {
            long cursor = state.cursorId.get();
            long maxId = state.snapshotMaxId.get();

            // 从旧库读
            List<Map<String, Object>> rows = oldJdbc.queryForList(
                "SELECT * FROM orders WHERE id > ? AND id <= ? ORDER BY id LIMIT ?",
                cursor, maxId, BATCH_SIZE);
            if (rows.isEmpty()) {
                state.snapshotCaughtUp.set(true);
                persist();
                return;
            }

            for (Map<String, Object> row : rows) {
                long id = ((Number) row.get("id")).longValue();
                saveToShard(row);
                state.cursorId.set(id);
                state.migratedRecords.incrementAndGet();
            }
            persist();
            log.debug("Batch {} rows, cursor={}/{}, snapshotCaughtUp={}",
                    rows.size(), state.cursorId.get(), maxId, state.snapshotCaughtUp.get());
        } catch (Exception e) {
            log.error("Migration batch failed", e);
        }
    }

    // ==================== 补偿重试 ====================

    @Scheduled(fixedDelay = 5000)
    void compensateBatch() {
        try {
            List<Map<String, Object>> tasks = oldJdbc.queryForList(
                "SELECT id, order_data FROM migration_compensation WHERE cm_status='PENDING' AND retry_count < max_retries ORDER BY id LIMIT 20");
            if (tasks.isEmpty()) return;

            int recovered = 0;
            for (Map<String, Object> task : tasks) {
                Long taskId = ((Number) task.get("id")).longValue();
                String json = (String) task.get("order_data");
                try {
                    var node = MAPPER.readTree(json);
                    Map<String, Object> row = Map.of(
                        "id", node.get("orderId").asLong(),
                        "user_id", node.get("userId").asLong(),
                        "order_no", node.get("orderNo").asText(),
                        "total_amount", new java.math.BigDecimal(node.get("totalAmount").asText()),
                        "status", node.get("status").asText(),
                        "created_at", node.has("createdAt") ? node.get("createdAt").asText() : LocalDateTime.now().toString()
                    );
                    saveToShard(row);
                    oldJdbc.update("UPDATE migration_compensation SET cm_status='COMPLETED' WHERE id=?", taskId);
                    recovered++;
                } catch (Exception e) {
                    oldJdbc.update("UPDATE migration_compensation SET retry_count=retry_count+1, error_msg=? WHERE id=?",
                            e.getMessage(), taskId);
                    log.warn("Compensation retry failed for task {}: {}", taskId, e.getMessage());
                }
            }
            if (recovered > 0) log.info("Compensated {} orders", recovered);
        } catch (Exception e) {
            log.debug("Compensation check skipped: {}", e.getMessage());
        }
    }

    // ==================== 持久化 ====================

    private void persist() {
        try {
            oldJdbc.update(
                "INSERT INTO migration_state (id, phase, advance_percent, migrated_records, " +
                "snapshot_max_id, cursor_id, snapshot_caught_up, double_write_enabled, " +
                "rolled_back_shards, start_time) VALUES (1,?,?,?,?,?,?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE phase=VALUES(phase), advance_percent=VALUES(advance_percent), " +
                "migrated_records=VALUES(migrated_records), snapshot_max_id=VALUES(snapshot_max_id), " +
                "cursor_id=VALUES(cursor_id), snapshot_caught_up=VALUES(snapshot_caught_up), " +
                "double_write_enabled=VALUES(double_write_enabled), rolled_back_shards=VALUES(rolled_back_shards)",
                state.phase.get().name(), state.advancePercent.get(), state.migratedRecords.get(),
                state.snapshotMaxId.get(), state.cursorId.get(), state.snapshotCaughtUp.get(),
                doubleWriteEnabled, state.rolledBackShards.toString(), state.startTime);
        } catch (Exception e) {
            log.warn("Failed to persist migration state: {}", e.getMessage());
        }
    }

    // ==================== helpers ====================

    private void saveToShard(Map<String, Object> row) {
        try (SqlSession session = shardSession.openSession(true)) {
            ShardOrderMapper mapper = session.getMapper(ShardOrderMapper.class);
            ShardOrder so = new ShardOrder();
            so.setId(((Number) row.get("id")).longValue());
            so.setUserId(((Number) row.get("user_id")).longValue());
            so.setOrderNo((String) row.get("order_no"));
            so.setTotalAmount(new java.math.BigDecimal(row.get("total_amount").toString()));
            so.setStatus((String) row.get("status"));
            Object ts = row.get("created_at");
            if (ts instanceof java.sql.Timestamp t) so.setCreatedAt(t.toLocalDateTime());
            else if (ts instanceof LocalDateTime ldt) so.setCreatedAt(ldt);
            else so.setCreatedAt(LocalDateTime.now());
            mapper.insert(so);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 幂等跳过
        }
    }

    private int getInt(java.util.Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(v));
    }

    private long getLong(java.util.Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(v));
    }

    private boolean getBool(java.util.Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v instanceof Number n) return n.intValue() == 1;
        return Boolean.parseBoolean(String.valueOf(v));
    }
}
