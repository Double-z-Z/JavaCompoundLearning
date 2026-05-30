-- 建表脚本
-- docker exec -i mybatis-pg psql -U order_user -d order_db < src/main/resources/db/migration/V1__init.sql

CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50) NOT NULL,
    email       VARCHAR(100),
    phone       VARCHAR(20),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS products (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    price       DECIMAL(10,2) NOT NULL,
    stock       INT NOT NULL DEFAULT 0,
    category    VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS orders (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    order_no    VARCHAR(32) NOT NULL UNIQUE,
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS order_items (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT NOT NULL REFERENCES orders(id),
    product_id  BIGINT NOT NULL REFERENCES products(id),
    quantity    INT NOT NULL DEFAULT 1,
    unit_price  DECIMAL(10,2) NOT NULL
);

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
