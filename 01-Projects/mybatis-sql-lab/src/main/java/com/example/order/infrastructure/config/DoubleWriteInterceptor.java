package com.example.order.infrastructure.config;

import com.example.order.migration.MigrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * MyBatis 插件 — Executor 层拦截, 实现双写.
 *
 * 与 AOP 方案的区别:
 * - AOP 在方法层: 一次方法调用只能路由到一个 DataSource, 无法原生双写
 * - 本插件在 SQL 层: 每个 Executor.update() 独立拦截, 可在同一拦截点内执行两次
 *
 * 双写流程:
 *   1. invocation.proceed() → 正常执行 (经路由 DataSource, 由 phase 决定走 oldDs/newDs)
 *   2. 若 isDoubleWrite(): 通过 ShardSessionFactory 再执行一次 → 直连 ShardingSphere
 *   3. 第 2 步失败 → 写入补偿表 (migration_compensation)
 *
 * 注册方式: 在 defaultSqlSessionFactory 构建时通过 config.addInterceptor() 注入.
 * 非 Spring Bean, 不参与 Spring 依赖图.
 */
@Intercepts({
    @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class DoubleWriteInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(DoubleWriteInterceptor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private final ShardSessionFactory shardSession;
    private final JdbcTemplate compensationJdbc;

    public DoubleWriteInterceptor(ShardSessionFactory shardSession, DataSource oldDbDataSource) {
        this.shardSession = shardSession;
        this.compensationJdbc = new JdbcTemplate(oldDbDataSource);
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 1. 正常执行 (经路由 DataSource)
        Object result = invocation.proceed();

        // 2. 双写: 同一 MappedStatement, 在分片 DataSource 上再执行一次
        if (MigrationService.isDoubleWriteActive()) {
            MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
            Object parameter = invocation.getArgs()[1];
            String stmtId = ms.getId();

            // 只对 ShardOrderMapper/ShardOrderItemMapper 的写操作做双写
            if (isTargetWrite(stmtId)) {
                try (SqlSession session = shardSession.openSession(true)) {
                    session.update(stmtId, parameter);
                    log.debug("[DoubleWrite] {} → shard OK", stmtId);
                } catch (Exception e) {
                    log.error("[DoubleWrite] {} FAILED — writing to compensation", stmtId, e);
                    recordCompensation(stmtId, parameter, e);
                }
            }
        }

        return result;
    }

    /** 只对分片 Mapper 的 INSERT 做双写 (UPDATE/DELETE 迁移场景不需要) */
    private boolean isTargetWrite(String stmtId) {
        return stmtId != null && (
            stmtId.contains("ShardOrderMapper.insert") ||
            stmtId.contains("ShardOrderItemMapper.insert"));
    }

    private void recordCompensation(String stmtId, Object parameter, Exception cause) {
        try {
            String json = MAPPER.writeValueAsString(parameter);
            compensationJdbc.update(
                "INSERT INTO migration_compensation (order_data, cm_status) VALUES (?,?)",
                json, "PENDING");
        } catch (Exception ex) {
            log.error("[Compensation] Failed to record for {}: {}", stmtId, ex.getMessage());
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
