package com.example.order.infrastructure.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.example.order.infrastructure.persistence.sharding.ShardOrderItemMapper;
import com.example.order.infrastructure.persistence.sharding.ShardOrderMapper;
import com.example.order.infrastructure.persistence.sharding.ShardUserMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * 程序化 SqlSessionFactory — 直连 shardingDataSource, 不经过路由.
 *
 * 非 Spring Bean. 由 DoubleWriteInterceptor 和 MigrationService 内部使用.
 * 与 defaultSqlSessionFactory 共享相同的 Mapper 注册, 确保 MappedStatement ID 一致.
 */
public class ShardSessionFactory {
    private static final Logger log = LoggerFactory.getLogger(ShardSessionFactory.class);

    private final SqlSessionFactory factory;

    public ShardSessionFactory(DataSource shardingDataSource) {
        try {
            MybatisSqlSessionFactoryBean fb = new MybatisSqlSessionFactoryBean();
            fb.setDataSource(shardingDataSource);
            var config = new MybatisConfiguration();
            config.setMapUnderscoreToCamelCase(true);
            // 注册纯注解 Mapper（无 XML 的 BaseMapper）
            config.addMapper(ShardOrderMapper.class);
            config.addMapper(ShardOrderItemMapper.class);
            config.addMapper(ShardUserMapper.class);
            fb.setConfiguration(config);
            this.factory = fb.getObject();
            log.info("ShardSessionFactory created (programmatic, non-bean)");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create ShardSessionFactory", e);
        }
    }

    public SqlSession openSession(boolean autoCommit) {
        return factory.openSession(autoCommit);
    }

    public SqlSession openSession() {
        return factory.openSession();
    }

    SqlSessionFactory getFactory() { return factory; }
}
