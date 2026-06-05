package com.example.order.migration.datasource;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.example.order.infrastructure.config.DoubleWriteInterceptor;
import com.example.order.infrastructure.config.ShardSessionFactory;
import com.example.order.migration.MigrationService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * 迁移期间的主 DataSource + SqlSessionFactory.
 *
 * 职责 1: 请求路由 — MigrationRoutingDataSource 根据 phase+userId 选 DataSource.
 * 职责 2: 双写 — DoubleWriteInterceptor (MyBatis Plugin) 在 Executor 层拦截,
 *         同一 SQL 通过 ShardSessionFactory 在分片数据源上再执行一次.
 *
 * 注意:
 * - ShardSessionFactory 和 DoubleWriteInterceptor 不是 Spring Bean, 不参与依赖图.
 * - defaultSqlSessionFactory 是唯一的 SqlSessionFactory Bean (@Primary).
 */
@Configuration
@ConditionalOnProperty(name = "migration.active", havingValue = "true")
public class MigrationDataSourceConfig {

    @Bean
    @Primary
    public DataSource migrationRoutingDataSource(
            @Qualifier("singleDbDataSource") DataSource oldDs,
            @Qualifier("shardingDataSource") DataSource newDs,
            MigrationService migration) {
        return new MigrationRoutingDataSource(oldDs, newDs, migration);
    }

    @Bean
    @Primary
    public SqlSessionFactory defaultSqlSessionFactory(
            @Qualifier("migrationRoutingDataSource") DataSource routingDs,
            @Qualifier("shardingDataSource") DataSource shardingDs,
            @Qualifier("singleDbDataSource") DataSource oldDs) throws Exception {

        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(routingDs);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:com/example/order/infrastructure/persistence/sharding/*.xml"));
        var config = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);

        // 注册 MyBatis 双写插件 (非 Spring Bean)
        ShardSessionFactory shardSession = new ShardSessionFactory(shardingDs);
        DoubleWriteInterceptor dwInterceptor = new DoubleWriteInterceptor(shardSession, oldDs);
        config.addInterceptor(dwInterceptor);

        factory.setConfiguration(config);
        return factory.getObject();
    }

    @Bean
    @Primary
    public SqlSessionTemplate defaultSqlSessionTemplate(
            @Qualifier("defaultSqlSessionFactory") SqlSessionFactory sf) {
        return new SqlSessionTemplate(sf);
    }
}
