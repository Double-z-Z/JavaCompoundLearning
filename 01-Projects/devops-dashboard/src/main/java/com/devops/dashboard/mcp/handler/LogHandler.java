package com.devops.dashboard.mcp.handler;

import com.devops.dashboard.domain.environment.EnvironmentId;
import com.devops.dashboard.domain.mcp.*;
import com.devops.dashboard.mcp.error.McpExceptionTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 日志查询 MCP Handler（V3 新增）。
 *
 * <p>实现 {@code env_get_logs} Tool，作为 MCP 管理的指定环境的【唯一合法日志来源】。
 * 替代 SSH + docker logs，统一日志格式与脱敏。</p>
 *
 * <h3>约束</h3>
 * <ul>
 *   <li>唯一合法日志源：禁止通过 SSH 或 docker logs 直接读取</li>
 *   <li>日志格式统一：timestamp | level | service | message</li>
 *   <li>敏感信息脱敏：password、token、secret 等替换为 ***</li>
 * </ul>
 */
@Component
public class LogHandler extends McpHandler {

    private static final Logger log = LoggerFactory.getLogger(LogHandler.class);

    public LogHandler(McpExceptionTranslator errorTranslator) {
        super(errorTranslator);
    }

    /**
     * 获取环境日志（MCP Tool: {@code env_get_logs}）。
     *
     * @param envId       环境 ID（必须来自 env_list）
     * @param serviceName 服务名（留空返回整个环境的聚合日志）
     * @param tailLines   返回最近多少行，默认 100
     * @param since       时间范围，如 '10m'、'1h'、'2024-01-01T00:00:00Z'
     * @return JSON 格式的日志响应
     */
    public Mono<String> getLogs(String envId, String serviceName, int tailLines, String since) {
        log.info("MCP Tool [env_get_logs]: envId={}, serviceName={}, tailLines={}, since={}",
                envId, serviceName, tailLines, since);

        // 构造日志聚合根
        LogQuerySpec spec = LogQuerySpec.of(tailLines, since, null);
        LogAggregate aggregate = new LogAggregate(
                LogQueryId.generate(),
                EnvironmentId.of(envId),
                serviceName,
                spec
        );

        // 执行日志查询（使用模拟的日志提供者）
        LogAggregate.LogProvider provider = (eId, svc, qSpec) -> {
            // 模拟日志数据 - 实际实现应从容器/主机获取
            return Flux.just(
                    LogLine.of(Instant.now().minusSeconds(10), LogLevel.INFO, svc != null ? svc : "app", "Application started"),
                    LogLine.of(Instant.now().minusSeconds(5), LogLevel.WARN, svc != null ? svc : "app", "Connection pool size is *** (sensitive data hidden)"),
                    LogLine.of(Instant.now(), LogLevel.ERROR, svc != null ? svc : "app", "Failed to connect to database: connection timeout")
            );
        };

        return aggregate.stream(provider)
                .collectList()
                .map(lines -> buildLogResponse(envId, serviceName, lines));
    }

    private String buildLogResponse(String envId, String serviceName, List<LogLine> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"envId\": \"").append(envId).append("\",\n");
        sb.append("  \"serviceName\": \"").append(serviceName != null ? serviceName : "").append("\",\n");
        sb.append("  \"lines\": [\n");

        for (int i = 0; i < lines.size(); i++) {
            LogLine line = lines.get(i);
            if (i > 0) sb.append(",\n");
            sb.append("    {\n");
            sb.append("      \"timestamp\": \"").append(line.timestamp()).append("\",\n");
            sb.append("      \"level\": \"").append(line.level()).append("\",\n");
            sb.append("      \"service\": \"").append(line.service() != null ? line.service() : "").append("\",\n");
            sb.append("      \"message\": \"").append(line.message().replace("\"", "\\\"")).append("\"\n");
            sb.append("    }");
        }

        sb.append("\n  ],\n");
        sb.append("  \"totalLines\": ").append(lines.size()).append(",\n");
        sb.append("  \"hasMore\": ").append(lines.size() >= 100).append("\n");
        sb.append("}");

        return sb.toString();
    }

    /**
     * 获取日志流式响应（用于 SSE 推送）。
     */
    public Flux<String> getLogsStream(String envId, String serviceName, int tailLines, String since) {
        LogQuerySpec spec = LogQuerySpec.of(tailLines, since, null);
        LogAggregate aggregate = new LogAggregate(
                LogQueryId.generate(),
                EnvironmentId.of(envId),
                serviceName,
                spec
        );

        LogAggregate.LogProvider provider = (eId, svc, qSpec) -> {
            return Flux.just(
                    LogLine.of(Instant.now(), LogLevel.INFO, svc != null ? svc : "app", "Streaming log line 1"),
                    LogLine.of(Instant.now(), LogLevel.INFO, svc != null ? svc : "app", "Streaming log line 2")
            );
        };

        return aggregate.stream(provider)
                .map(line -> String.format("data: {\"timestamp\":\"%s\",\"level\":\"%s\",\"message\":\"%s\"}\n\n",
                        line.timestamp(), line.level(), line.message().replace("\"", "\\\"")));
    }
}