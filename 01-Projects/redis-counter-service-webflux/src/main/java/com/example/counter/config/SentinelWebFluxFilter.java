package com.example.counter.config;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Sentinel WebFlux Filter
 * 对 /spike/** 路径的请求进行限流保护
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SentinelWebFluxFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(SentinelWebFluxFilter.class);

    private static final String SPIKE_PATH_PATTERN = "/spike";

    @Value("${sentinel.enabled:true}")
    private boolean sentinelEnabled;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (!path.startsWith(SPIKE_PATH_PATTERN) || !sentinelEnabled) {
            return chain.filter(exchange);
        }

        return doSentinelFilter(exchange, chain);
    }

    private Mono<Void> doSentinelFilter(ServerWebExchange exchange, WebFilterChain chain) {
        Entry entry = null;
        try {
            entry = SphU.entry(SentinelConfig.SPIKE_RESOURCE, EntryType.IN);
        } catch (BlockException e) {
            log.debug("请求被限流: resource={}", SentinelConfig.SPIKE_RESOURCE);
            return handleBlocked(exchange);
        } catch (Exception e) {
            log.debug("Sentinel 限流检查异常: resource={}", SentinelConfig.SPIKE_RESOURCE, e);
            return chain.filter(exchange);
        }

        if (entry != null) {
            entry.exit();
        }
        return chain.filter(exchange);
    }

    private Mono<Void> handleBlocked(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        response.getHeaders().add("X-Spike-Limit", "triggered");

        String requestId = exchange.getRequest().getId();
        String blockMsg = String.format(
                "{\"code\":429,\"success\":false,\"message\":\"系统繁忙，请稍后重试\",\"data\":null,\"requestId\":\"%s\"}",
                requestId != null ? requestId : "unknown"
        );

        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(blockMsg.getBytes(StandardCharsets.UTF_8)))
        );
    }
}