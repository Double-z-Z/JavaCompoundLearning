package com.devops.dashboard.domain.loadgen;

import java.time.Duration;
import java.util.List;

/**
 * 健康检查规格说明值对象。
 *
 * <p>定义对目标服务进行健康检查的参数，包括目标 URL、超时时间、期望状态码等。
 * 用于 {@code test_health_check} MCP Tool 的输入。</p>
 *
 * @see com.devops.dashboard.application.loadgen.LoadgenService#executeHealthCheck(HealthCheckSpec)
 */
public class HealthCheckSpec {

    private final String targetUrl;

    private final Duration timeout;

    private final int expectedStatusCode;

    private final int retryCount;

    private HealthCheckSpec(Builder builder) {
        this.targetUrl = builder.targetUrl;
        this.timeout = builder.timeout != null ? builder.timeout : Duration.ofSeconds(10);
        this.expectedStatusCode = builder.expectedStatusCode > 0 ? builder.expectedStatusCode : 200;
        this.retryCount = Math.max(0, builder.retryCount);
    }

    public String getTargetUrl() { return targetUrl; }
    public Duration getTimeout() { return timeout; }
    public int getExpectedStatusCode() { return expectedStatusCode; }
    public int getRetryCount() { return retryCount; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String targetUrl;
        private Duration timeout = Duration.ofSeconds(10);
        private int expectedStatusCode = 200;
        private int retryCount = 3;

        public Builder targetUrl(String url) { this.targetUrl = url; return this; }
        public Builder timeout(Duration d) { this.timeout = d; return this; }
        public Builder expectedStatusCode(int code) { this.expectedStatusCode = code; return this; }
        public Builder retryCount(int n) { this.retryCount = n; return this; }

        public HealthCheckSpec build() {
            if (targetUrl == null || targetUrl.isBlank()) {
                throw new IllegalArgumentException("targetUrl is required");
            }
            return new HealthCheckSpec(this);
        }
    }
}
