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
 * 配置自定义限流响应处理器
 */
@Configuration
public class SentinelAdapterConfig {

    private static final Logger log = LoggerFactory.getLogger(SentinelAdapterConfig.class);

    @PostConstruct
    public void init() {
        WebFluxCallbackManager.setBlockHandler(new SpikeBlockRequestHandler());
        log.info("Sentinel WebFlux 限流响应处理器已配置");
    }

    /**
     * 自定义限流响应处理器
     * 适配器统一使用此处理器返回 429 响应
     */
    private static class SpikeBlockRequestHandler implements BlockRequestHandler {

        @Override
        public Mono<org.springframework.web.reactive.function.server.ServerResponse> handleRequest(
                ServerWebExchange exchange, Throwable ex) {

            String requestId = exchange.getRequest().getId();
            log.warn("请求被限流: requestId={}", requestId);

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
}