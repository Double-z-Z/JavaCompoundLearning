-- 迁移支撑表（在旧库 order_db 上执行）
-- migration_state: 迁移状态持久化（单行）
-- migration_compensation: 双写补偿队列

USE order_db;

-- 迁移状态持久化表
CREATE TABLE IF NOT EXISTS migration_state (
    id                  INT PRIMARY KEY DEFAULT 1,
    phase               VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    advance_percent     INT NOT NULL DEFAULT 0,
    migrated_records    BIGINT NOT NULL DEFAULT 0,
    snapshot_max_id     BIGINT NOT NULL DEFAULT 0,
    cursor_id           BIGINT NOT NULL DEFAULT 0,
    snapshot_caught_up  BOOLEAN NOT NULL DEFAULT FALSE,
    double_write_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    rolled_back_shards  VARCHAR(500) DEFAULT '{}',
    start_time          TIMESTAMP NULL,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

INSERT INTO migration_state (id) VALUES (1) ON DUPLICATE KEY UPDATE id=id;

-- 双写补偿表
CREATE TABLE IF NOT EXISTS migration_compensation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_data      JSON NOT NULL,
    target_shard    INT DEFAULT 0,
    cm_status       VARCHAR(10) DEFAULT 'PENDING',
    retry_count     INT DEFAULT 0,
    max_retries     INT DEFAULT 3,
    error_msg       TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cm_status (cm_status)
) ENGINE=InnoDB;
