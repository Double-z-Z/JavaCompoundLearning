package com.example.order.migration;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 迁移状态.
 *
 * 全量搬迁用快照游标模式:
 * - snapshotMaxId: 迁移启动时旧库最大 id（此时刻的快照上限）
 * - cursorId: 当前搬迁到的游标位置
 * - snapshotCaughtUp: cursorId >= snapshotMaxId，快照已追平
 *
 * 快照期间新写入的数据 (id > snapshotMaxId) 在双写阶段由 save() 的双写覆盖.
 */
public class MigrationState {
    public final AtomicReference<MigrationPhase> phase = new AtomicReference<>(MigrationPhase.IDLE);
    public final AtomicInteger advancePercent = new AtomicInteger(0);
    public final AtomicLong migratedRecords = new AtomicLong(0);
    public final AtomicLong snapshotMaxId = new AtomicLong(0);  // 启动快照
    public final AtomicLong cursorId = new AtomicLong(0);        // 搬迁游标
    public final AtomicBoolean snapshotCaughtUp = new AtomicBoolean(false);
    public final LocalDateTime startTime = LocalDateTime.now();
    public final ConcurrentHashMap<Integer, Boolean> rolledBackShards = new ConcurrentHashMap<>();

    public boolean isActive() {
        var p = phase.get();
        return p == MigrationPhase.SYNCING || p == MigrationPhase.DOUBLE_WRITE || p == MigrationPhase.ADVANCING;
    }

    public String statusJson() {
        return String.format(
            "{\"phase\":\"%s\",\"advancePercent\":%d,\"migratedRecords\":%d," +
            "\"snapshotMaxId\":%d,\"cursorId\":%d,\"snapshotCaughtUp\":%s,\"startTime\":\"%s\"}",
            phase.get(), advancePercent.get(), migratedRecords.get(),
            snapshotMaxId.get(), cursorId.get(), snapshotCaughtUp.get(), startTime.toString());
    }
}
