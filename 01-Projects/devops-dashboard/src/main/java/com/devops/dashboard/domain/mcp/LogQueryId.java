package com.devops.dashboard.domain.mcp;

import java.util.UUID;

/**
 * 日志查询聚合根标识（Value Object）。
 */
public record LogQueryId(String value) {

    public static LogQueryId generate() {
        return new LogQueryId("log-" + UUID.randomUUID().toString().substring(0, 8));
    }

    public static LogQueryId of(String value) {
        return new LogQueryId(value);
    }
}