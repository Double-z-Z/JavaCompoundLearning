package com.devops.dashboard.mcp.dto.request;

import lombok.Builder;
import lombok.Getter;

/**
 * 远程命令执行请求 DTO（MCP Tool: {@code test_exec_command}）。
 *
 * <p><strong>保守式交互</strong>：仅用于只读诊断命令，
 * AI 应在执行前向用户展示命令内容并请求确认。</p>
 */
@Getter
@Builder
public class ExecCommandRequest {

    /** 目标主机 ID（必填） */
    private final String hostId;

    /** 要执行的命令（必填） */
    private final String command;

    /** 超时秒数，默认 30 */
    private final Integer timeoutSeconds;
}
