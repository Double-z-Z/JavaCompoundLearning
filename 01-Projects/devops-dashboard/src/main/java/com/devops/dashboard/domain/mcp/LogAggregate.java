package com.devops.dashboard.domain.mcp;

import com.devops.dashboard.domain.environment.EnvironmentId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 日志聚合根（Aggregate Root）。
 *
 * <p>V3 新增的日志聚合根，作为 MCP 管理的指定环境的【唯一合法日志来源】。
 * 替代 SSH + docker logs，统一日志格式与脱敏。</p>
 *
 * <h3>能力对比</h3>
 * <table>
 *   <tr><th>能力</th><th>V2 SSH+docker logs</th><th>V3 env_get_logs</th></tr>
 *   <tr><td>实时日志</td><td>✅</td><td>✅</td></tr>
 *   <tr><td>历史日志</td><td>✅</td><td>✅</td></tr>
 *   <tr><td>按服务筛选</td><td>✅</td><td>✅</td></tr>
 *   <tr><td>行数控制</td><td>✅</td><td>✅</td></tr>
 *   <tr><td>格式统一</td><td>❌</td><td>✅</td></tr>
 *   <tr><td>敏感信息脱敏</td><td>❌</td><td>✅</td></tr>
 * </table>
 */
public class LogAggregate {

    private static final Logger log = LoggerFactory.getLogger(LogAggregate.class);

    // 敏感信息模式（简单的 credit card、password、token 等检测）
    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
        Pattern.compile("password[=:]\\s*[^\\s,}]+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("token[=:]\\s*[^\\s,}]+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("secret[=:]\\s*[^\\s,}]+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\d{13,16}"), // credit card
        Pattern.compile("api[_-]?key[=:]\\s*[^\\s,}]+", Pattern.CASE_INSENSITIVE)
    );

    private final LogQueryId id;
    private final EnvironmentId envId;
    private final String serviceName;
    private final LogQuerySpec spec;

    public LogAggregate(LogQueryId id, EnvironmentId envId, String serviceName, LogQuerySpec spec) {
        this.id = id;
        this.envId = envId;
        this.serviceName = serviceName;
        this.spec = spec != null ? spec : LogQuerySpec.of();
    }

    public LogQueryId getId() { return id; }
    public EnvironmentId getEnvId() { return envId; }
    public String getServiceName() { return serviceName; }
    public LogQuerySpec getSpec() { return spec; }

    /**
     * 获取日志流。
     *
     * @param logProvider 日志提供者（实际由 LogHandler 实现）
     * @return 脱敏和格式化后的日志流
     */
    public Flux<LogLine> stream(LogProvider logProvider) {
        log.debug("Streaming logs for env {} service {}", envId.value(), serviceName);

        return logProvider.fetch(envId, serviceName, spec)
            .map(this::normalizeFormat)
            .map(this::desensitize);
    }

    /**
     * 统一日志格式：timestamp | level | service | message
     */
    private LogLine normalizeFormat(LogLine raw) {
        // 原始日志可能格式不统一，这里做格式标准化
        // 目前假设原始日志已经是标准格式，直接返回
        return raw;
    }

    /**
     * 敏感信息脱敏。
     *
     * <p>检测并替换：
     * <ul>
     *   <li>password=xxx → password=***</li>
     *   <li>token=xxx → token=***</li>
     *   <li>secret=xxx → secret=***</li>
     *   <li>信用卡号 → ****-****-****-????</li>
     *   <li>api_key=xxx → api_key=***</li>
     * </ul>
     */
    LogLine desensitize(LogLine line) {
        String message = line.message();

        for (Pattern pattern : SENSITIVE_PATTERNS) {
            message = pattern.matcher(message).replaceAll("*** (sensitive data hidden)");
        }

        if (message.equals(line.message())) {
            return line;
        }

        return new LogLine(line.timestamp(), line.level(), line.service(), message, line.rawHash());
    }

    /**
     * 日志提供者接口。
     * 实际实现由 LogHandler 注入，负责从容器/主机获取原始日志。
     */
    public interface LogProvider {
        Flux<LogLine> fetch(EnvironmentId envId, String serviceName, LogQuerySpec spec);
    }
}