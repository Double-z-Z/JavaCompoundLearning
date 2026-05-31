-- V2: 乐观锁 + 多租户
-- docker exec -i mybatis-pg psql -U order_user -d order_db < src/main/resources/db/migration/V2__optlock_tenant.sql

ALTER TABLE users       ADD COLUMN IF NOT EXISTS version   INTEGER DEFAULT 0;
ALTER TABLE users       ADD COLUMN IF NOT EXISTS tenant_id BIGINT  DEFAULT 1;
ALTER TABLE orders      ADD COLUMN IF NOT EXISTS tenant_id BIGINT  DEFAULT 1;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS tenant_id BIGINT  DEFAULT 1;

UPDATE users       SET version   = 0 WHERE version   IS NULL;
UPDATE users       SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE orders      SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE order_items SET tenant_id = 1 WHERE tenant_id IS NULL;
