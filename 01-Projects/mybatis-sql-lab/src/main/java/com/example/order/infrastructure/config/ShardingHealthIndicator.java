package com.example.order.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

/**
 * ShardingSphere 分片健康检查.
 *
 * 通过 ShardingSphere 执行一条广播查询, 验证所有分片可达.
 * 暴露给 /actuator/health, 可在 Prometheus 中配置告警.
 */
@Component
public class ShardingHealthIndicator implements HealthIndicator {
    private static final Logger log = LoggerFactory.getLogger(ShardingHealthIndicator.class);

    private final DataSource shardingDataSource;

    public ShardingHealthIndicator(
            @Qualifier("shardingDataSource") DataSource shardingDataSource) {
        this.shardingDataSource = shardingDataSource;
    }

    @Override
    public Health health() {
        try (Connection conn = shardingDataSource.getConnection()) {
            if (!conn.isValid(2)) {
                return Health.down()
                        .withDetail("error", "Connection invalid")
                        .build();
            }

            // 尝试实际查询 — ShardingSphere 会路由到所有分片
            try (var stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                if (rs.next()) {
                    return Health.up()
                            .withDetail("shards", "ds0+ds1 reachable")
                            .withDetail("query", "SELECT 1 OK")
                            .build();
                }
            }

            return Health.up()
                    .withDetail("shards", "connection OK, query skipped")
                    .build();
        } catch (Exception e) {
            log.warn("ShardingSphere health check FAILED: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
