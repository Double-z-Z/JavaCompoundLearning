package com.devops.dashboard.mcp.handler;

import com.devops.dashboard.application.loadgen.LoadgenService;
import com.devops.dashboard.domain.loadgen.*;
import com.devops.dashboard.mcp.dto.request.ExecCommandRequest;
import com.devops.dashboard.mcp.dto.request.HealthCheckRequest;
import com.devops.dashboard.mcp.dto.request.LoadTestRequest;
import com.devops.dashboard.mcp.error.McpExceptionTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试工具 MCP Handler（Phase 3）。
 *
 * <p>提供压测执行、健康检查、远程命令等测试相关 Tool 集合。</p>
 *
 * <h3>Tool 清单</h3>
 * <table border="1">
 *   <tr><th>Tool</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>{@code test_load}</td><td>{@link #testLoad}</td><td>负载测试</td></tr>
 *   <tr><td>{@code test_health_check}</td><td>{@link #testHealthCheck}</td><td>健康检查</td></tr>
 *   <tr><td>{@code test_exec_command}</td><td>{@link #testExecCommand}</td><td>远程命令</td></tr>
 * </table>
 */
@Component
public class TestingHandler extends McpHandler {

    private static final Logger log = LoggerFactory.getLogger(TestingHandler.class);

    private final LoadgenService loadgenService;

    public TestingHandler(McpExceptionTranslator errorTranslator, LoadgenService loadgenService) {
        super(errorTranslator);
        this.loadgenService = loadgenService;
    }

    public reactor.core.publisher.Mono<String> testLoad(LoadTestRequest request) {
        log.info("MCP Tool [test_load]: target={}, hostId={}, tool={}",
                request.getTargetUrl(), request.getLoadgenHostId(), request.getTool());

        return handleAsync(
                loadgenService.executeLoadTest(buildSpec(request))
                        .map(this::toResultJson)
        );
    }

    public reactor.core.publisher.Mono<String> testHealthCheck(HealthCheckRequest request) {
        log.info("MCP Tool [test_health_check]: url={}", request.getTargetUrl());

        return handleAsync(
                loadgenService.executeHealthCheck(buildHealthCheckSpec(request))
                        .map(this::toHealthResultJson)
        );
    }

    public reactor.core.publisher.Mono<String> testExecCommand(ExecCommandRequest request) {
        log.info("MCP Tool [test_exec_command]: hostId={}, command={}",
                request.getHostId(), request.getCommand());

        int timeout = request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : 30;

        return handleAsync(
                loadgenService.executeCommand(request.getHostId(), request.getCommand(), timeout)
                        .map(this::toCommandResultJson)
        );
    }

    private String toResultJson(LoadTestResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", result.getStatus() == LoadTestStatus.COMPLETED);
        data.put("message", result.isSuccess() ? "Load test completed successfully" : "Load test failed");
        data.put("id", result.getId());
        data.put("status", result.getStatus().name());
        putIfNotNull(data, "tool", result.getTool() != null ? result.getTool().getCommand() : null);
        putIfNotNull(data, "requestsPerSecond", safeBigDecimal(result.getRequestsPerSecond()));
        putIfNotNull(data, "latencyAvgMs", positiveOrNull(result.getLatencyAvg()));
        putIfNotNull(data, "latencyP50Ms", positiveOrNull(result.getLatencyP50()));
        putIfNotNull(data, "latencyP90Ms", positiveOrNull(result.getLatencyP90()));
        putIfNotNull(data, "latencyP99Ms", positiveOrNull(result.getLatencyP99()));
        data.put("errorRatePercent", round(result.getErrorRate()));
        data.put("totalRequests", result.getTotalRequests());
        data.put("totalErrors", result.getTotalErrors());
        data.put("rawOutput", result.getRawOutput());
        return toJson(data);
    }

    private String toHealthResultJson(com.devops.dashboard.domain.loadgen.HealthCheckResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", result.isHealthy());
        data.put("healthy", result.isHealthy());
        data.put("targetUrl", result.getTargetUrl());
        data.put("statusCode", result.getStatusCode());
        putIfNotNull(data, "responseTimeMs",
                result.getResponseTime() != null ? result.getResponseTime().toMillis() : null);
        putIfNotNull(data, "errorMessage", result.getErrorMessage());
        return toJson(data);
    }

    private String toCommandResultJson(com.devops.dashboard.application.loadgen.CommandExecutionResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", result.isSuccess());
        data.put("exitCode", result.getExitCode());
        data.put("stdout", truncate(result.getStdout(), 2048));
        data.put("stderr", truncate(result.getStderr(), 1024));
        data.put("durationMs", result.getDurationMs());
        return toJson(data);
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }

    private Object safeBigDecimal(java.math.BigDecimal bd) {
        return bd != null ? bd.toString() : null;
    }

    private Double positiveOrNull(double val) {
        return val > 0 ? val : null;
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "... [truncated]" : s;
    }

    private LoadTestSpec buildSpec(LoadTestRequest req) {
        LoadgenTool tool = req.getTool() != null
                ? LoadgenTool.fromCommand(req.getTool()) : LoadgenTool.WRK;

        return LoadTestSpec.builder()
                .targetUrl(req.getTargetUrl())
                .loadgenHostId(req.getLoadgenHostId())
                .tool(tool)
                .connections(req.getConnections() != null ? req.getConnections() : 10)
                .duration(java.time.Duration.ofSeconds(
                        req.getDurationSeconds() != null ? req.getDurationSeconds() : 30))
                .threads(req.getThreads() != null ? req.getThreads() : 2)
                .method(HttpMethod.fromString(req.getMethod()))
                .build();
    }

    private HealthCheckSpec buildHealthCheckSpec(HealthCheckRequest req) {
        return HealthCheckSpec.builder()
                .targetUrl(req.getTargetUrl())
                .timeout(java.time.Duration.ofSeconds(
                        req.getTimeoutSeconds() != null ? req.getTimeoutSeconds() : 10))
                .expectedStatusCode(req.getExpectedStatusCode() != null
                        ? req.getExpectedStatusCode() : 200)
                .build();
    }
}
