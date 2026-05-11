package com.example.counter.config;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.example.counter.dto.OrderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Sentinel 限流 Block Handler
 * 当请求被限流时，返回友好的错误信息
 */
@Component
public class SentinelBlockHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SentinelBlockHandler.class);

    private static final String SPIKE_RESOURCE = "spike";
    private static final byte[] DEFAULT_BLOCK_MSG = "系统繁忙，请稍后重试".getBytes(StandardCharsets.UTF_8);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (ex instanceof BlockException) {
            BlockException blockEx = (BlockException) ex;
            log.warn("请求被限流, 资源: {}, 类型: {}", blockEx.getRuleLimitApp(), blockEx.getClass().getSimpleName());

            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
            response.getHeaders().add("X-Spike-Limit", "triggered");

            String blockMsg = String.format(
                    "{\"code\":429,\"message\":\"系统繁忙，请稍后重试\",\"data\":null,\"requestId\":\"%s\"}",
                    exchange.getRequest().getId()
            );
            return response.writeWith(
                    Mono.just(response.bufferFactory().wrap(blockMsg.getBytes(StandardCharsets.UTF_8)))
            );
        }
        return Mono.error(ex);
    }

    /**
     * 限流时的返回结果（供 Controller 调用）
     */
    public static OrderResult getBlockedResult(String requestId) {
        java.util.Map<String, Long> meta = new java.util.HashMap<>();
        meta.put("code", 429L);
        return OrderResult.failure("系统繁忙，请稍后重试", meta);
    }
}