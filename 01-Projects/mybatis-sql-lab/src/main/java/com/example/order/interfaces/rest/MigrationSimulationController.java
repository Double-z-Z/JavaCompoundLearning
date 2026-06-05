package com.example.order.interfaces.rest;

import com.example.order.domain.order.*;
import com.example.order.migration.MigrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 单库 → 分库分表 全流程迁移演练.
 *
 * 仅在迁移期间存在，迁移完成后删除此类.
 *
 * 流程:
 *   POST /seed          → 往旧库灌测试数据
 *   POST /start         → 开启全量搬迁 (SYNCING)
 *   POST /double-write  → 追平后双写 (DOUBLE_WRITE)
 *   POST /advance?pct=N → 灰度推进
 *   POST /complete      → 完成
 *   POST /rollback      → 回退
 *   GET  /status        → 状态
 *   GET  /verify        → 校验新旧库行数
 */
@RestController
@RequestMapping("/api/v2/migration-sim")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "migration.active", havingValue = "true")
public class MigrationSimulationController {
    private static final Logger log = LoggerFactory.getLogger(MigrationSimulationController.class);

    private final MigrationService migration;
    private final OrderRepository orderRepo;
    private final JdbcTemplate oldJdbc;

    public MigrationSimulationController(
            MigrationService migration,
            @Qualifier("sharding") OrderRepository orderRepo,
            @Qualifier("singleDbDataSource") DataSource oldDs) {
        this.migration = migration;
        this.orderRepo = orderRepo;
        this.oldJdbc = new JdbcTemplate(oldDs);
    }

    /** 往旧库灌测试数据 (写旧库, 经路由在 IDLE 阶段走 oldDs) */
    @PostMapping("/seed")
    public Map<String, Object> seed(@RequestParam(defaultValue = "200") int count) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            Order order = Order.create(null, (long) (i % 50),
                    "LEGACY-" + System.currentTimeMillis() + "-" + i,
                    BigDecimal.valueOf(50 + (i % 200)),
                    List.of("历史商品" + (i % 5)));
            orderRepo.save(order);
        }
        return Map.of("phase", "DATA_SEEDED", "seeded", count,
                "oldDbRows", countOldDb(),
                "elapsedMs", System.currentTimeMillis() - start);
    }

    /** 开启全量同步 */
    @PostMapping("/start")
    public Map<String, Object> start() {
        return Map.of("result", migration.start(),
                "oldDbRows", countOldDb());
    }

    /** 进入双写 */
    @PostMapping("/double-write")
    public Map<String, Object> doubleWrite() {
        return Map.of("result", migration.startDoubleWrite(),
                "migratedRecords", migration.state.migratedRecords.get(),
                "snapshotCaughtUp", migration.state.snapshotCaughtUp.get());
    }

    /** 灰度推进 */
    @PostMapping("/advance")
    public Map<String, Object> advance(@RequestParam int percent) {
        return Map.of("result", migration.advance(percent),
                "advancePercent", migration.state.advancePercent.get());
    }

    /** 完成 */
    @PostMapping("/complete")
    public Map<String, Object> complete() {
        return Map.of("result", migration.advance(100),
                "oldDbRows", countOldDb(),
                "shardRows", orderRepo.countAll());
    }

    /** 回退 */
    @PostMapping("/rollback")
    public Map<String, Object> rollback(@RequestParam int shard) {
        return Map.of("result", migration.rollback(shard));
    }

    /** 暂停 */
    @PostMapping("/pause")
    public Map<String, Object> pause() {
        return Map.of("result", migration.pause());
    }

    /** 迁移状态 */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
            "phase", migration.state.phase.get().name(),
            "advancePercent", migration.state.advancePercent.get(),
            "migratedRecords", migration.state.migratedRecords.get(),
            "snapshotMaxId", migration.state.snapshotMaxId.get(),
            "cursorId", migration.state.cursorId.get(),
            "snapshotCaughtUp", migration.state.snapshotCaughtUp.get(),
            "doubleWrite", migration.isDoubleWrite(),
            "oldDbRows", countOldDb(),
            "shardRows", orderRepo.countAll(),
            "startTime", migration.state.startTime.toString()
        );
    }

    /** 校验新旧库数据行数 */
    @GetMapping("/verify")
    public Map<String, Object> verify() {
        long oldCount = countOldDb();
        long newCount = orderRepo.countAll();
        long drift = Math.abs(oldCount - newCount);
        return Map.of(
            "oldDbRows", oldCount,
            "shardRows", newCount,
            "consistency", drift == 0 ? "CONSISTENT" : "DRIFT",
            "drift", drift
        );
    }

    private long countOldDb() {
        Long c = oldJdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
        return c != null ? c : 0;
    }
}
