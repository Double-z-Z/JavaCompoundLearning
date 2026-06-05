package com.example.order.interfaces.rest;

import com.example.order.domain.order.OrderRepository;
import com.example.order.domain.order.OrderStatus;
import com.example.order.migration.MigrationService;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 监控端点 — 分片指标 + 迁移指标.
 *
 * /api/v2/metrics/shards    — 分片拓扑、数据量、连接池状态
 * /api/v2/metrics/migration — 迁移状态、延迟、进度
 */
@RestController
@RequestMapping("/api/v2/metrics")
public class MetricsController {

    private final OrderRepository orderRepo;
    private final MigrationService migrationService;
    private final DataSource shardingDataSource;
    private final DataSource singleDbDataSource;

    public MetricsController(
            @Qualifier("sharding") OrderRepository orderRepo,
            @Autowired(required = false) MigrationService migrationService,
            @Autowired(required = false) @Qualifier("shardingDataSource") DataSource shardingDataSource,
            @Autowired(required = false) @Qualifier("singleDbDataSource") DataSource singleDbDataSource) {
        this.orderRepo = orderRepo;
        this.migrationService = migrationService;
        this.shardingDataSource = shardingDataSource;
        this.singleDbDataSource = singleDbDataSource;
    }

    // ==================== 分片指标 ====================

    @GetMapping("/shards")
    public Map<String, Object> shardMetrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", LocalDateTime.now().toString());

        // 拓扑
        Map<String, Object> topology = new LinkedHashMap<>();
        topology.put("dataSources", new String[]{"ds0 (localhost:3307)", "ds1 (localhost:3308)"});
        topology.put("shardCount", 4);
        topology.put("dbShardAlgorithm", "user_id % 2");
        topology.put("tableShardAlgorithm", "(user_id % 4).intdiv(2)");
        topology.put("logicalTables", new String[]{"orders (orders_0, orders_1)", "order_items (order_items_0, order_items_1)"});
        topology.put("broadcastTables", new String[]{"t_user"});
        topology.put("bindingTables", new String[]{"orders <-> order_items"});
        topology.put("keyGenerator", "SNOWFLAKE");
        m.put("topology", topology);

        // 数据量
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("orders", orderRepo.countAll());
        counts.put("orders_PENDING", orderRepo.countByStatus(OrderStatus.PENDING));
        counts.put("orders_PAID", orderRepo.countByStatus(OrderStatus.PAID));
        counts.put("orders_SHIPPED", orderRepo.countByStatus(OrderStatus.SHIPPED));
        counts.put("orders_COMPLETED", orderRepo.countByStatus(OrderStatus.COMPLETED));
        counts.put("orders_CANCELLED", orderRepo.countByStatus(OrderStatus.CANCELLED));
        m.put("rowCounts", counts);

        // 连接池
        Map<String, Object> pools = new LinkedHashMap<>();
        if (shardingDataSource != null) {
            pools.put("sharding", describePool("shardingDataSource", shardingDataSource));
        }
        if (singleDbDataSource != null) {
            pools.put("singleDb", describePool("singleDbDataSource", singleDbDataSource));
        }
        m.put("connectionPools", pools);

        return m;
    }

    private Map<String, Object> describePool(String name, DataSource ds) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("class", ds.getClass().getSimpleName());
        if (ds instanceof HikariDataSource hikari) {
            info.put("active", hikari.getHikariPoolMXBean().getActiveConnections());
            info.put("idle", hikari.getHikariPoolMXBean().getIdleConnections());
            info.put("total", hikari.getHikariPoolMXBean().getTotalConnections());
            info.put("pending", hikari.getHikariPoolMXBean().getThreadsAwaitingConnection());
            info.put("maxPoolSize", hikari.getMaximumPoolSize());
        }
        return info;
    }

    // ==================== 迁移指标 ====================

    @GetMapping("/migration")
    public Map<String, Object> migrationMetrics() {
        if (migrationService == null) {
            return Map.of(
                "enabled", false,
                "hint", "Set migration.active=true to enable migration metrics"
            );
        }

        var s = migrationService.state;
        long lag = Math.max(0, s.snapshotMaxId.get() - s.cursorId.get());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", true);
        m.put("timestamp", LocalDateTime.now().toString());
        m.put("phase", s.phase.get().name());
        m.put("startTime", s.startTime.toString());
        m.put("advancePercent", s.advancePercent.get());
        m.put("migratedRecords", s.migratedRecords.get());
        m.put("snapshotMaxId", s.snapshotMaxId.get());
        m.put("cursorId", s.cursorId.get());
        m.put("snapshotCaughtUp", s.snapshotCaughtUp.get());
        m.put("snapshotLag", lag);
        m.put("doubleWriteEnabled", migrationService.isDoubleWrite());
        m.put("rolledBackShards", s.rolledBackShards.keySet());
        m.put("readDecision", s.isActive()
                ? "userId %% 100 < " + s.advancePercent.get() + "%%"
                : s.phase.get().name());

        return m;
    }
}
