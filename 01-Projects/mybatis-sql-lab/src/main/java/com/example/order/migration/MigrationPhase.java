package com.example.order.migration;

public enum MigrationPhase {
    IDLE,            // 未开始
    SYNCING,         // 增量同步追赶中
    DOUBLE_WRITE,    // 双写（新旧同时写）
    ADVANCING,       // 灰度推进中（部分用户切读新分片）
    COMPLETE,        // 迁移完成（全部走新分片）
    ROLLING_BACK     // 回退中
}
