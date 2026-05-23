package com.devops.dashboard.mcp.handler;

import com.devops.dashboard.mcp.error.McpError;
import com.devops.dashboard.mcp.error.McpExceptionTranslator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import reactor.core.publisher.Mono;

/**
 * MCP Handler 抽象基类：封装异步处理模板与统一响应序列化。
 *
 * <h3>异步处理模板（Template Method 模式）</h3>
 * <p>子类通过 {@link #handleAsync(Mono)} 将领域服务调用包装为标准化的
 * {@code Mono&lt;String&gt;} 流，自动完成以下步骤：</p>
 * <ol>
 *   <li>执行业务逻辑（{@code serviceCall}）</li>
 *   <li>成功时：JSON 序列化结果（pretty-print 格式）</li>
 *   <li>异常时：经 {@link McpExceptionTranslator} 翻译后返回错误 JSON</li>
 * </ol>
 *
 * <h3>统一响应格式</h3>
 * <pre>
 * 成功: { ... 业务数据 JSON ... }
 * 失败: { "code": "HOST_NOT_FOUND", "message": "...", "httpStatus": 404, "userHint": "..." }
 * </pre>
 *
 * <h3>子类扩展点</h3>
 * <ul>
 *   <li>注入所需的 {@code XxxService} 依赖，在公开方法中调用 {@link #handleAsync(Mono)}</li>
 *   <li>可覆写 {@link #toJson(Object)} / {@link #toErrorJson(McpError)} 自定义序列化策略</li>
 *   <li><strong>不应</strong>直接操作 {@link McpExceptionTranslator} 或 {@link ObjectMapper}</li>
 * </ul>
 *
 * @see McpExceptionTranslator 异常翻译策略
 * @see McpError 标准化错误格式
 */
public abstract class McpHandler {

    protected final McpExceptionTranslator errorTranslator;
    protected final ObjectMapper objectMapper;

    /**
     * 构造基类，注入异常翻译器。
     *
     * @param errorTranslator MCP 异常翻译器实例，用于将 Java 异常转换为标准化错误
     */
    protected McpHandler(McpExceptionTranslator errorTranslator) {
        this.errorTranslator = errorTranslator;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 异步处理模板：执行服务调用并统一处理成功/失败路径。
     *
     * <p>错误恢复按优先级匹配：{@code SharedException} → {@code IllegalArgumentException}
     * → 通用 {@code Exception}，确保不会出现未处理的异常导致 Mono 终止。</p>
     *
     * @param <T>        业务返回值类型
     * @param serviceCall 包装了业务逻辑的 {@link Mono}，通常来自 Service 层
     * @return 包含 JSON 字符串的 {@link Mono}，成功为业务数据 JSON，失败为错误 JSON
     */
    protected <T> Mono<String> handleAsync(Mono<T> serviceCall) {
        return serviceCall
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .map(this::toJson)
                .onErrorResume(com.devops.dashboard.domain.exception.shared.SharedException.class, ex ->
                        Mono.just(toErrorJson(errorTranslator.translate(ex)))
                )
                .onErrorResume(IllegalArgumentException.class, ex ->
                        Mono.just(toErrorJson(errorTranslator.translate(ex)))
                )
                .onErrorResume(Exception.class, ex ->
                        Mono.just(toErrorJson(errorTranslator.translate(ex)))
                );
    }

    /**
     * 将业务对象序列化为 pretty-print JSON 字符串。
     *
     * <p>序列化失败时返回兜底错误 JSON {@code {"error": "Serialization failed"}}，
     * 保证方法永不抛异常。</p>
     *
     * @param data 待序列化的业务对象
     * @return JSON 字符串（pretty-print 格式），或兜底错误 JSON
     */
    protected String toJson(Object data) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"Serialization failed\"}";
        }
    }

    /**
     * 将 {@link McpError} 序列化为 JSON 字符串。
     *
     * <p>作为 {@link #toJson(Object)} 的错误路径版本，序列化失败时降级为
     * 手动拼接的简化 JSON，至少保留 code 和 message 两个字段。</p>
     *
     * @param error 已翻译好的 MCP 错误记录
     * @return 错误 JSON 字符串，或降级后的简化 JSON
     */
    protected String toErrorJson(McpError error) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(error);
        } catch (JsonProcessingException e) {
            return "{\"code\": \"" + error.code() + "\", \"message\": \"" + error.message() + "\"}";
        }
    }
}
