package com.devops.dashboard.mcp.dto.request;

import lombok.Builder;
import lombok.Getter;

/**
 * 健康检查请求 DTO（MCP Tool: {@code test_health_check}）。
 */
@Getter
@Builder
public class HealthCheckRequest {

    /** 目标 URL（必填） */
    private final String targetUrl;

    /** 超时秒数，默认 10 */
    private final Integer timeoutSeconds;

    /** 期望状态码，默认 200 */
    private final Integer expectedStatusCode;
}
