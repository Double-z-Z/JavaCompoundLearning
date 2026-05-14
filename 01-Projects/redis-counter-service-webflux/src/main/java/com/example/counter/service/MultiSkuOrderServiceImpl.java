package com.example.counter.service;

import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.adapter.reactor.SentinelReactorTransformer;
import com.example.counter.config.SentinelConfig;
import com.example.counter.dto.MultiSkuOrderRequest;
import com.example.counter.dto.OrderItem;
import com.example.counter.dto.OrderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 多SKU下单服务实现
 * Saga补偿模式：每个SKU的检查+扣减在Lua脚本原子完成，跨SKU失败时补偿
 * L2 熔断保护：Redis 不可用时拒绝下单（写操作不支持降级）
 */
@Service
public class MultiSkuOrderServiceImpl implements MultiSkuOrderService {

    private static final Logger log = LoggerFactory.getLogger(MultiSkuOrderServiceImpl.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<Long> decrementScript;

    public MultiSkuOrderServiceImpl(
            ReactiveStringRedisTemplate redisTemplate,
            @Qualifier("decrementScript") RedisScript<Long> decrementScript) {
        this.redisTemplate = redisTemplate;
        this.decrementScript = decrementScript;
    }

    @Override
    public Mono<OrderResult> placeOrder(MultiSkuOrderRequest request) {
        List<OrderItem> items = request.getItems();
        if (items == null || items.isEmpty()) {
            return Mono.just(OrderResult.failure("Empty order", Map.of()));
        }

        // 使用 Sentinel Reactor Transformer 埋点 L2 资源
        return doPlaceOrder(request)
                .transform(new SentinelReactorTransformer<>(SentinelConfig.REDIS_DECREMENT_RESOURCE))
                .onErrorResume(Exception.class, e -> {
                    // 熔断/异常触发降级
                    Tracer.trace(e);
                    log.warn("Redis 熔断降级，拒绝下单（写操作不支持本地缓存降级）");
                    return Mono.just(OrderResult.failure("Service degraded, please retry later", Map.of(), Map.of()));
                });
    }

    /**
     * 实际下单逻辑（Lua 原子扣减）
     */
    private Mono<OrderResult> doPlaceOrder(MultiSkuOrderRequest request) {
        AtomicReference<Map<String, Long>> successMap = new AtomicReference<>(new HashMap<>());
        AtomicReference<Map<String, Integer>> failedMap = new AtomicReference<>(new HashMap<>());
        AtomicReference<Map<String, Long>> compensateMap = new AtomicReference<>(new HashMap<>());
        AtomicBoolean hasFailure = new AtomicBoolean(false);

        return Flux.fromIterable(request.getItems())
                .flatMap(item -> {
                    String sku = item.getSku();
                    int qty = item.getQty();

                    if (qty <= 0) {
                        hasFailure.set(true);
                        failedMap.get().put(sku, qty);
                        log.warn("SKU {} invalid quantity: {}", sku, qty);
                        return Mono.empty();
                    }

                    String key = "stock:" + sku;

                    return redisTemplate.execute(
                            decrementScript,
                            List.of(key),
                            List.of(String.valueOf(qty))
                    ).next()
                    .map(result -> {
                        long remaining = result.longValue();
                        if (remaining == -1) {
                            hasFailure.set(true);
                            failedMap.get().put(sku, qty);
                            log.debug("SKU {} decrement failed: insufficient stock, requested {}", sku, qty);
                        } else {
                            successMap.get().put(sku, remaining);
                            compensateMap.get().put(sku, (long) qty);
                            log.debug("SKU {} decremented to {}", sku, remaining);
                        }
                        return result;
                    });
                })
                .collectList()
                .flatMap(results -> {
                    if (hasFailure.get()) {
                        return compensate(compensateMap.get())
                                .thenReturn(OrderResult.failure("Partial failure, compensated", successMap.get(), failedMap.get()));
                    }
                    return Mono.just(OrderResult.success(successMap.get()));
                });
    }

    /**
     * 补偿回滚：把已扣减的库存加回去
     */
    private Mono<Void> compensate(Map<String, Long> decremented) {
        if (decremented.isEmpty()) {
            return Mono.empty();
        }

        return Flux.fromIterable(decremented.entrySet())
                .flatMap(entry -> {
                    String sku = entry.getKey();
                    Long decrementedQty = entry.getValue();
                    String key = "stock:" + sku;

                    return redisTemplate.opsForValue()
                            .increment(key, decrementedQty)
                            .doOnNext(newVal -> log.debug("Compensated: {} +{} = {}", sku, decrementedQty, newVal));
                })
                .then();
    }
}