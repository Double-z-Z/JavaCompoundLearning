package com.example.order.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.shardingsphere.driver.api.yaml.YamlShardingSphereDataSourceFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.SQLException;

/**
 * 数据源配置 — 只定义 DataSource Bean, 不定义 SqlSessionFactory.
 *
 * Bean:
 *   shardingDataSource   — ShardingSphere (ds0:3307 + ds1:3308)
 *   singleDbDataSource   — HikariCP 直连旧单库 (3307)
 *
 * SqlSessionFactory 由 MigrationDataSourceConfig 统一管理.
 * 迁移完成后设置 migration.active=false, MigrationDataSourceConfig 停用,
 * Spring Boot 自动使用 shardingDataSource(@Primary) 创建默认 SqlSessionFactory.
 */
@Configuration
public class ShardingDataSourceConfig {

    // ── 分片数据源 ──

    @Bean
    @ConditionalOnProperty(name = "migration.active", havingValue = "false", matchIfMissing = true)
    @Primary
    public DataSource shardingDataSource() throws IOException, SQLException {
        return loadSharding();
    }

    @Bean(name = "shardingDataSource")
    @ConditionalOnProperty(name = "migration.active", havingValue = "true")
    public DataSource shardingDataSourceForMigration() throws IOException, SQLException {
        return loadSharding();
    }

    // ── 旧单库数据源 ──

    @Bean
    @ConditionalOnProperty(name = "migration.active", havingValue = "true")
    public DataSource singleDbDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:mysql://localhost:3307/order_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        ds.setUsername("root");
        ds.setPassword("root123");
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(5);
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setPoolName("SingleDbPool");
        return ds;
    }

    private DataSource loadSharding() throws IOException, SQLException {
        return YamlShardingSphereDataSourceFactory.createDataSource(
                new ClassPathResource("sharding-config.yml").getInputStream().readAllBytes());
    }
}
