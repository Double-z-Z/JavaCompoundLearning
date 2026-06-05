-- 分库分表建表脚本（在 ds0 上执行，通过主从复制同步到 ds1）
-- t_user: 广播表，每个分片全量复制
-- orders_0, orders_1: 分片订单表（逻辑表名 orders）
-- order_items_0, order_items_1: 分片订单明细表（绑定表，逻辑表名 order_items）

USE order_db;

-- 广播表：每个分片存完整副本
CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT PRIMARY KEY,
    username    VARCHAR(50) NOT NULL,
    email       VARCHAR(100),
    phone       VARCHAR(20),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 分片订单表 _0
CREATE TABLE IF NOT EXISTS orders_0 (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    order_no    VARCHAR(32) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB;

-- 分片订单表 _1
CREATE TABLE IF NOT EXISTS orders_1 (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    order_no    VARCHAR(32) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB;

-- 分片订单明细 _0
CREATE TABLE IF NOT EXISTS order_items_0 (
    id          BIGINT PRIMARY KEY,
    order_id    BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    product_name VARCHAR(100),
    quantity    INT NOT NULL DEFAULT 1,
    unit_price  DECIMAL(10,2) NOT NULL,
    INDEX idx_order_id (order_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB;

-- 分片订单明细 _1
CREATE TABLE IF NOT EXISTS order_items_1 (
    id          BIGINT PRIMARY KEY,
    order_id    BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    product_name VARCHAR(100),
    quantity    INT NOT NULL DEFAULT 1,
    unit_price  DECIMAL(10,2) NOT NULL,
    INDEX idx_order_id (order_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB;

-- 广播表测试数据
INSERT INTO t_user (id, username, email, phone) VALUES
    (1, '张三', 'zhangsan@example.com', '13800000001'),
    (2, '李四', 'lisi@example.com', '13800000002'),
    (3, '王五', 'wangwu@example.com', '13800000003')
ON DUPLICATE KEY UPDATE username=VALUES(username);
