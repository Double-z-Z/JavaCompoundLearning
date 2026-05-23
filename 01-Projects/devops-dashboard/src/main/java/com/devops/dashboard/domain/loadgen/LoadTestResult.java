package com.devops.dashboard.domain.loadgen;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 压测结果值对象（Value Object）。
 *
 * <p>封装压测工具执行后的完整输出数据，包括吞吐量、延迟分布、错误率等核心指标。
 * 数据来源为 wrk/hey/ab 的原始输出解析。</p>
 *
 * <h3>核心指标</h3>
 * <table border="1">
 *   <tr><th>指标</th><th>说明</th><th>单位</th></tr>
 *   <tr><td>{@code requestsPerSecond}</td><td>每秒请求数（QPS/TPS）</td><td>req/s</td></tr>
 *   <tr><td>{@code latencyAvg}</td><td>平均延迟</td><td>ms</td></tr>
 *   <tr><td>{@code latencyP50/P90/P99}</td><td>分位延迟</td><td>ms</td></tr>
 *   <tr><td>{@code errorRate}</td><td>错误率</td><td>%</td></tr>
 *   <tr><td>{@code totalRequests}</td><td>总请求成功数</td><td>count</td></tr>
 * </table>
 *
 * @see LoadTestSpec 压测输入规格
 * @see LoadTestStatus 任务状态
 */
public class LoadTestResult {

    private final String id;

    private final LoadTestStatus status;

    private final LocalDateTime startTime;

    private final LocalDateTime endTime;

    private final Duration actualDuration;

    private final LoadgenTool tool;

    private final String rawOutput;

    private final BigDecimal requestsPerSecond;

    private final double latencyAvg;

    private final double latencyP50;

    private final double latencyP90;

    private final double latencyP99;

    private final double errorRate;

    private final long totalRequests;

    private final long totalErrors;

    private final long transferredBytes;

    private final Map<String, String> perStatusCodeCount;

    private LoadTestResult(Builder builder) {
        this.id = builder.id != null ? builder.id : "lt-" + System.currentTimeMillis();
        this.status = builder.status != null ? builder.status : LoadTestStatus.PENDING;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.actualDuration = builder.actualDuration;
        this.tool = builder.tool;
        this.rawOutput = builder.rawOutput;
        this.requestsPerSecond = builder.requestsPerSecond;
        this.latencyAvg = builder.latencyAvg;
        this.latencyP50 = builder.latencyP50;
        this.latencyP90 = builder.latencyP90;
        this.latencyP99 = builder.latencyP99;
        this.errorRate = builder.errorRate;
        this.totalRequests = builder.totalRequests;
        this.totalErrors = builder.totalErrors;
        this.transferredBytes = builder.transferredBytes;
        this.perStatusCodeCount = builder.perStatusCodeCount != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(builder.perStatusCodeCount))
                : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static LoadTestResult failed(String reason, LoadgenTool tool) {
        return builder()
                .status(LoadTestStatus.FAILED)
                .tool(tool)
                .rawOutput(reason)
                .build();
    }

    public String getId() { return id; }
    public LoadTestStatus getStatus() { return status; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public Duration getActualDuration() { return actualDuration; }
    public LoadgenTool getTool() { return tool; }
    public String getRawOutput() { return rawOutput; }
    public BigDecimal getRequestsPerSecond() { return requestsPerSecond; }
    public double getLatencyAvg() { return latencyAvg; }
    public double getLatencyP50() { return latencyP50; }
    public double getLatencyP90() { return latencyP90; }
    public double getLatencyP99() { return latencyP99; }
    public double getErrorRate() { return errorRate; }
    public long getTotalRequests() { return totalRequests; }
    public long getTotalErrors() { return totalErrors; }
    public long getTransferredBytes() { return transferredBytes; }
    public Map<String, String> getPerStatusCodeCount() { return perStatusCodeCount; }

    public boolean isSuccess() {
        return status == LoadTestStatus.COMPLETED && errorRate < 5.0;
    }

    public static class Builder {
        private String id;
        private LoadTestStatus status;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Duration actualDuration;
        private LoadgenTool tool;
        private String rawOutput;
        private BigDecimal requestsPerSecond;
        private double latencyAvg;
        private double latencyP50;
        private double latencyP90;
        private double latencyP99;
        private double errorRate;
        private long totalRequests;
        private long totalErrors;
        private long transferredBytes;
        private Map<String, String> perStatusCodeCount;

        public Builder id(String id) { this.id = id; return this; }
        public Builder status(LoadTestStatus status) { this.status = status; return this; }
        public Builder startTime(LocalDateTime time) { this.startTime = time; return this; }
        public Builder endTime(LocalDateTime time) { this.endTime = time; return this; }
        public Builder actualDuration(Duration d) { this.actualDuration = d; return this; }
        public Builder tool(LoadgenTool tool) { this.tool = tool; return this; }
        public Builder rawOutput(String output) { this.rawOutput = output; return this; }
        public Builder requestsPerSecond(BigDecimal rps) { this.requestsPerSecond = rps; return this; }
        public Builder latencyAvg(double avg) { this.latencyAvg = avg; return this; }
        public Builder latencyP50(double p50) { this.latencyP50 = p50; return this; }
        public Builder latencyP90(double p90) { this.latencyP90 = p90; return this; }
        public Builder latencyP99(double p99) { this.latencyP99 = p99; return this; }
        public Builder errorRate(double rate) { this.errorRate = rate; return this; }
        public Builder totalRequests(long n) { this.totalRequests = n; return this; }
        public Builder totalErrors(long n) { this.totalErrors = n; return this; }
        public Builder transferredBytes(long bytes) { this.transferredBytes = bytes; return this; }
        public Builder perStatusCodeCount(Map<String, String> map) { this.perStatusCodeCount = map; return this; }

        public LoadTestResult build() {
            return new LoadTestResult(this);
        }
    }
}
