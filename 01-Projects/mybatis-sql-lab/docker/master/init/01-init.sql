-- 初始化脚本：Master 启动时执行
-- 创建复制用户
CREATE USER IF NOT EXISTS 'repl'@'%' IDENTIFIED BY 'repl123';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'repl'@'%';
FLUSH PRIVILEGES;

-- 建表（与 V1__init.sql 保持一致，调整为 MySQL 语法）
USE order_db;

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50) NOT NULL,
    email       VARCHAR(100),
    phone       VARCHAR(20),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version     INT DEFAULT 0,
    tenant_id   BIGINT DEFAULT 1,
    deleted     INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS products (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    price       DECIMAL(10,2) NOT NULL,
    stock       INT NOT NULL DEFAULT 0,
    category    VARCHAR(50)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS orders (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    order_no    VARCHAR(32) NOT NULL UNIQUE,
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_items (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT NOT NULL,
    user_id     BIGINT DEFAULT 0,
    product_id  BIGINT NULL DEFAULT NULL,
    product_name VARCHAR(100),
    quantity    INT NOT NULL DEFAULT 1,
    unit_price  DECIMAL(10,2) NOT NULL,
    INDEX idx_order_id (order_id)
) ENGINE=InnoDB;

-- 存储过程
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS sp_update_order_status(IN orderId BIGINT, IN newStatus VARCHAR(20))
BEGIN
    UPDATE orders SET status = newStatus WHERE id = orderId;
END //
DELIMITER ;

-- 测试数据
INSERT INTO users (username, email, phone) VALUES
    ('张三', 'zhangsan@example.com', '13800000001'),
    ('李四', 'lisi@example.com', '13800000002'),
    ('王五', 'wangwu@example.com', '13800000003');

INSERT INTO products (name, price, stock, category) VALUES
    ('机械键盘', 399.00, 100, '电子产品'),
    ('显示器', 2499.00, 50, '电子产品'),
    ('Java核心技术', 99.00, 200, '图书'),
    ('人体工学椅', 1899.00, 30, '办公家具'),
    ('无线鼠标', 149.00, 150, '电子产品');

INSERT INTO orders (user_id, order_no, total_amount, status) VALUES
    (1, 'ORD20260530001', 2898.00, 'PAID'),
    (1, 'ORD20260530002', 149.00, 'PENDING'),
    (2, 'ORD20260530003', 99.00, 'PAID');

INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES
    (1, 2, 1, 2499.00),
    (1, 1, 1, 399.00),
    (2, 5, 1, 149.00),
    (3, 3, 1, 99.00);
