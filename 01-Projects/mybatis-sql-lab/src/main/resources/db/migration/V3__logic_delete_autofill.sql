-- V3: 逻辑删除 + 自动填充
-- docker exec -i mybatis-pg psql -U order_user -d order_db < src/main/resources/db/migration/V3__logic_delete_autofill.sql

ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted     INTEGER   DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS create_time TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS update_time TIMESTAMP;

UPDATE users SET deleted = 0 WHERE deleted IS NULL;
