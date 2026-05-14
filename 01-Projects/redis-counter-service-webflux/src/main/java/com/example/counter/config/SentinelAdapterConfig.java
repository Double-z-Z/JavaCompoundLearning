package com.example.counter.config;

import com.alibaba.csp.sentinel.adapter.spring.webflux.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.spring.webflux.callback.WebFluxCallbackManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;

/**
 * Sentinel WebFlux 适配器配置
 * 配置限流/熔断响应处理器 + URL 资源名归一化
 */
@Configuration
public class SentinelAdapterConfig {

    private static final Logger log = LoggerFactory.getLogger(SentinelAdapterConfig.class);

    @PostConstruct
    public void init() {
        // 配置 BlockHandler：限流/熔断触发时的响应
        WebFluxCallbackManager.setBlockHandler(new SpikeBlockRequestHandler());
        // 配置 UrlCleaner：统一资源名（将 /spike/* -> spike）
        WebFluxCallbackManager.setUrlCleaner(this::cleanUrl);
        log.info("Sentinel WebFlux 适配器配置完成");
    }

    /**
     * 自定义限流/熔断响应处理器
     */
    private static class SpikeBlockRequestHandler implements BlockRequestHandler {

        @Override
        public Mono<org.springframework.web.reactive.function.server.ServerResponse> handleRequest(
                ServerWebExchange exchange, Throwable ex) {

            String requestId = exchange.getRequest().getId();
            log.warn("请求被限流/熔断: requestId={}, path={}", requestId, exchange.getRequest().getPath());

            String blockMsg = String.format(
                    "{\"code\":429,\"success\":false,\"message\":\"系统繁忙，请稍后重试\",\"data\":null,\"requestId\":\"%s\"}",
                    requestId != null ? requestId : "unknown"
            );

            return org.springframework.web.reactive.function.server.ServerResponse
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Spike-Limit", "triggered")
                    .bodyValue(blockMsg);
        }
    }

    /**
     * URL 归一化：BiFunction<ServerWebExchange, originalUrl, normalizedUrl>
     * 将 /spike/* 统一映射为 spike，便于统一配置限流/熔断规则
     */
    private String cleanUrl(ServerWebExchange exchange, String originUrl) {
        if (originUrl == null || originUrl.isEmpty()) {
            return originUrl;
        }
        // 秒杀接口统一映射到 spike
        if (originUrl.startsWith("/spike")) {
            return "spike";
        }
        return originUrl;
    }
}