package com.devops.dashboard.domain.mcp;

/**
 * 日志查询规格（Value Object）。
 *
 * @param tailLines 返回最近多少行，默认 100
 * @param since     时间范围，如 '10m'、'1h'、'2024-01-01T00:00:00Z'
 * @param minLevel  最低日志级别过滤
 */
public record LogQuerySpec(
    int tailLines,
    String since,
    LogLevel minLevel
) {
    public static LogQuerySpec of() {
        return new LogQuerySpec(100, null, null);
    }

    public static LogQuerySpec of(int tailLines) {
        return new LogQuerySpec(tailLines, null, null);
    }

    public static LogQuerySpec of(int tailLines, String since, LogLevel minLevel) {
        return new LogQuerySpec(tailLines, since, minLevel);
    }
}