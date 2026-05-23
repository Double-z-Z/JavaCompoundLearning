package com.devops.dashboard.mcp.dto.request;

import lombok.Builder;
import lombok.Getter;

/**
 * 压测请求 DTO（MCP Tool: {@code test_load}）。
 *
 * @see com.devops.dashboard.mcp.handler.TestingHandler#testLoad(LoadTestRequest)
 */
@Getter
@Builder
public class LoadTestRequest {

    /** 目标 URL（必填） */
    private final String targetUrl;

    /** 压测机 ID（必填，需在 hosts.yml 中注册且具备 LOADGEN 角色） */
    private final String loadgenHostId;

    /** 压测工具：wrk/hey/ab，默认 wrk */
    private final String tool;

    /** 并发连接数，默认 10 */
    private final Integer connections;

    /** 持续时间秒数，默认 30 */
    private final Integer durationSeconds;

    /** 线程数（仅 wrk），默认 2 */
    private final Integer threads;

    /** HTTP 方法：GET/POST，默认 GET */
    private final String method;
}
