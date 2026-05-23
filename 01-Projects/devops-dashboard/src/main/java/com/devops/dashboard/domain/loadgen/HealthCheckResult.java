package com.devops.dashboard.domain.loadgen;

import java.time.Duration;

/**
 * 健康检查结果值对象。
 *
 * <p>封装健康检查的执行结果，包括 HTTP 状态码、响应时间、是否通过等。</p>
 */
public class HealthCheckResult {

    private final boolean healthy;

    private final String targetUrl;

    private final int statusCode;

    private final Duration responseTime;

    private final String errorMessage;

    private HealthCheckResult(Builder builder) {
        this.healthy = builder.healthy;
        this.targetUrl = builder.targetUrl;
        this.statusCode = builder.statusCode;
        this.responseTime = builder.responseTime;
        this.errorMessage = builder.errorMessage;
    }

    public boolean isHealthy() { return healthy; }
    public String getTargetUrl() { return targetUrl; }
    public int getStatusCode() { return statusCode; }
    public Duration getResponseTime() { return responseTime; }
    public String getErrorMessage() { return errorMessage; }

    public static Builder builder() { return new Builder(); }

    public static HealthCheckResult healthy(String url, int code, Duration rt) {
        return builder().healthy(true).targetUrl(url).statusCode(code).responseTime(rt).build();
    }

    public static HealthCheckResult unhealthy(String url, String error) {
        return builder().healthy(false).targetUrl(url).errorMessage(error).build();
    }

    public static class Builder {
        private boolean healthy;
        private String targetUrl;
        private int statusCode;
        private Duration responseTime;
        private String errorMessage;

        public Builder healthy(boolean v) { this.healthy = v; return this; }
        public Builder targetUrl(String u) { this.targetUrl = u; return this; }
        public Builder statusCode(int c) { this.statusCode = c; return this; }
        public Builder responseTime(Duration d) { this.responseTime = d; return this; }
        public Builder errorMessage(String e) { this.errorMessage = e; return this; }

        public HealthCheckResult build() { return new HealthCheckResult(this); }
    }
}
