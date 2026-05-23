package com.devops.dashboard.domain.mcp;

import java.time.Instant;

/**
 * 日志行（Value Object）。
 *
 * @param timestamp 时间戳
 * @param level     日志级别
 * @param service   服务名
 * @param message   脱敏后的消息
 * @param rawHash   原始日志的哈希（用于审计追溯）
 */
public record LogLine(
    Instant timestamp,
    LogLevel level,
    String service,
    String message,
    String rawHash
) {
    /**
     * 创建日志行（不计算哈希）。
     */
    public static LogLine of(Instant timestamp, LogLevel level, String service, String message) {
        return new LogLine(timestamp, level, service, message, null);
    }
}