package com.devops.dashboard.application.loadgen;

/**
 * 远程命令执行结果 DTO。
 *
 * <p>封装 SSH 远程命令执行的返回信息，用于 MCP Tool 响应。</p>
 */
public class CommandExecutionResult {

    private final int exitCode;

    private final String stdout;

    private final String stderr;

    private final long durationMs;

    private final boolean success;

    public CommandExecutionResult(int exitCode, String stdout, String stderr, long durationMs) {
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
        this.durationMs = durationMs;
        this.success = exitCode == 0;
    }

    public int getExitCode() { return exitCode; }
    public String getStdout() { return stdout; }
    public String getStderr() { return stderr; }
    public long getDurationMs() { return durationMs; }
    public boolean isSuccess() { return success; }
}
