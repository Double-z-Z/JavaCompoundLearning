package com.devops.dashboard.domain.loadgen;

import java.time.Duration;
import java.util.Objects;

/**
 * 压测规格说明值对象（Value Object）。
 *
 * <p>封装执行一次压测所需的全部参数，作为 {@code LoadgenService.executeLoadTest()}
 * 的输入。采用 Builder 模式支持灵活组合，所有参数均提供合理默认值。</p>
 *
 * <h3>核心参数</h3>
 * <table border="1">
 *   <tr><th>字段</th><th>必填</th><th>说明</th><th>默认值</th></tr>
 *   <tr><td>{@code targetUrl}</td><td>✅ 必填</td><td>目标 URL</td><td>-</td></tr>
 *   <tr><td>{@code tool}</td><td>推荐</td><td>压测工具类型</td><td>WRK</td></tr>
 *   <tr><td>{@code connections}</td><td>可选</td><td>并发连接数</td><td>10</td></tr>
 *   <tr><td>{@code duration}</td><td>可选</td><td>持续时间</td><td>30s</td></tr>
 *   <tr><td>{@code threads}</td><td>可选</td><td>线程数（仅 wrk）</td><td>2</td></tr>
 *   <tr><td>{@code loadgenHostId}</td><td>推荐</td><td>压测机 ID</td><td>null（本地）</td></tr>
 * </table>
 *
 * @see LoadgenTool 支持的压测工具
 * @see LoadTestResult 压测结果
 */
public class LoadTestSpec {

    private final String targetUrl;

    private final HttpMethod method;

    private final LoadgenTool tool;

    private final int connections;

    private final Duration duration;

    private final int threads;

    private final String loadgenHostId;

    private final String body;

    private final String headers;

    private LoadTestSpec(Builder builder) {
        this.targetUrl = Objects.requireNonNull(builder.targetUrl, "targetUrl is required");
        this.method = builder.method != null ? builder.method : HttpMethod.GET;
        this.tool = builder.tool != null ? builder.tool : LoadgenTool.WRK;
        this.connections = Math.max(1, builder.connections > 0 ? builder.connections : 10);
        this.duration = builder.duration != null && !builder.duration.isZero()
                ? builder.duration : Duration.ofSeconds(30);
        this.threads = Math.max(1, builder.threads > 0 ? builder.threads : 2);
        this.loadgenHostId = builder.loadgenHostId;
        this.body = builder.body;
        this.headers = builder.headers;
    }

    public String getTargetUrl() { return targetUrl; }
    public HttpMethod getMethod() { return method; }
    public LoadgenTool getTool() { return tool; }
    public int getConnections() { return connections; }
    public Duration getDuration() { return duration; }
    public int getThreads() { return threads; }
    public String getLoadgenHostId() { return loadgenHostId; }
    public String getBody() { return body; }
    public String getHeaders() { return headers; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String targetUrl;
        private HttpMethod method;
        private LoadgenTool tool;
        private int connections = 10;
        private Duration duration = Duration.ofSeconds(30);
        private int threads = 2;
        private String loadgenHostId;
        private String body;
        private String headers;

        public Builder targetUrl(String targetUrl) { this.targetUrl = targetUrl; return this; }
        public Builder method(HttpMethod method) { this.method = method; return this; }
        public Builder tool(LoadgenTool tool) { this.tool = tool; return this; }
        public Builder connections(int connections) { this.connections = connections; return this; }
        public Builder duration(Duration duration) { this.duration = duration; return this; }
        public Builder threads(int threads) { this.threads = threads; return this; }
        public Builder loadgenHostId(String hostId) { this.loadgenHostId = hostId; return this; }
        public Builder body(String body) { this.body = body; return this; }
        public Builder headers(String headers) { this.headers = headers; return this; }

        public LoadTestSpec build() {
            return new LoadTestSpec(this);
        }
    }
}
