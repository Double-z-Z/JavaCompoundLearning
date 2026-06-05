package com.example.order.migration.datasource;

import com.example.order.migration.MigrationPhase;
import com.example.order.migration.MigrationService;
import com.example.order.migration.MigrationContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 迁移路由数据源 — AbstractRoutingDataSource + 熔断降级.
 *
 * 路由决策:
 * - IDLE / SYNCING / DOUBLE_WRITE → oldDs
 * - ADVANCING → userId%100 < advancePercent → newDs（含分片级回退过滤）
 * - COMPLETE → newDs
 *
 * 熔断降级:
 * - 当 newDs 上的真实查询失败时, 自动熔断 (circuit OPEN)
 * - 熔断期间所有请求直接走 oldDs, 不再尝试 newDs
 * - 冷却期过后, 下一次请求尝试 newDs (HALF_OPEN)
 * - 成功则关闭熔断 (CLOSED), 失败则重新计时冷却
 *
 * 降级前提: 双写期间 old single DB 持有全量数据, 可以作为 fallback.
 */
public class MigrationRoutingDataSource extends AbstractRoutingDataSource {
    private static final Logger log = LoggerFactory.getLogger(MigrationRoutingDataSource.class);

    static final long COOLDOWN_MS = 5000; // 熔断冷却 5 秒后重试

    private final MigrationService migration;
    private final DataSource oldDs;
    private final DataSource newDs;

    private final Object circuitLock = new Object();
    private volatile boolean circuitOpen = false;
    private volatile long circuitOpenedAt = 0;
    private volatile int consecutiveFailures = 0;

    public MigrationRoutingDataSource(DataSource oldDs, DataSource newDs,
                                       MigrationService migration) {
        this.migration = migration;
        this.oldDs = oldDs;
        this.newDs = newDs;
        Map<Object, Object> dsMap = new HashMap<>();
        dsMap.put("oldDs", oldDs);
        dsMap.put("newDs", newDs);
        setTargetDataSources(dsMap);
        setDefaultTargetDataSource(oldDs);
        afterPropertiesSet();
    }

    // ── 路由决策 ──

    @Override
    protected Object determineCurrentLookupKey() {
        MigrationPhase phase = migration.state.phase.get();

        if (phase == MigrationPhase.COMPLETE) return "newDs";

        if (phase == MigrationPhase.ADVANCING) {
            Long userId = MigrationContext.getUserId();
            if (userId != null && migration.readFromNewShard(userId)) {
                int targetShard = (int) (userId % 2);
                if (migration.state.rolledBackShards.get(targetShard) != null) {
                    return "oldDs";
                }
                return "newDs";
            }
        }

        return "oldDs";
    }

    // ── 熔断降级 ──

    @Override
    public Connection getConnection() throws SQLException {
        Object lookupKey = determineCurrentLookupKey();

        if (!"newDs".equals(lookupKey)) {
            return super.getConnection(); // oldDs — 直连 HikariCP, 不经过 ShardingSphere
        }

        // 目标 newDs — 检查熔断状态
        if (circuitOpen) {
            if (cooldownExpired()) {
                // HALF_OPEN: 冷却期过, 放一个请求试探 newDs
                log.info("Circuit HALF_OPEN — probing newDs");
                return tryNewDs();
            }
            // OPEN: 还在冷却, 直接走 oldDs
            return oldDs.getConnection();
        }

        // CLOSED: 正常走 newDs
        return tryNewDs();
    }

    private Connection tryNewDs() throws SQLException {
        try {
            Connection conn = super.getConnection(); // → newDs.getConnection() → ShardingSphere
            closeCircuit();
            return conn;
        } catch (Exception e) {
            // ShardingSphere 异常可能是 RuntimeException 子类, 不只是 SQLException
            openCircuit();
            log.warn("newDs query FAILED ({}), circuit OPEN (cooldown {}ms). Falling back to oldDs.",
                    e.getClass().getSimpleName(), COOLDOWN_MS);
            return oldDs.getConnection();
        }
    }

    // ── 熔断状态管理 ──

    private boolean cooldownExpired() {
        return System.currentTimeMillis() - circuitOpenedAt > COOLDOWN_MS;
    }

    private void openCircuit() {
        synchronized (circuitLock) {
            circuitOpen = true;
            circuitOpenedAt = System.currentTimeMillis();
            consecutiveFailures++;
            log.warn("Circuit OPENED (failures: {})", consecutiveFailures);
        }
    }

    private void closeCircuit() {
        synchronized (circuitLock) {
            if (circuitOpen && consecutiveFailures > 0) {
                log.info("Circuit CLOSED after {} failures", consecutiveFailures);
            }
            circuitOpen = false;
            consecutiveFailures = 0;
        }
    }

    // ── 可观测性 ──

    boolean isCircuitOpen() { return circuitOpen; }
    int consecutiveFailures() { return consecutiveFailures; }
}
