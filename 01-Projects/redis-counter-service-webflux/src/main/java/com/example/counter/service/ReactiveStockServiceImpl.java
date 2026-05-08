package com.example.counter.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 库存服务实现（响应式版本）
 * 使用 ReactiveStringRedisTemplate 实现非阻塞 I/O
 */
@Service
public class ReactiveStockServiceImpl implements ReactiveStockService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<Long> decrementScript;

    public ReactiveStockServiceImpl(
            ReactiveStringRedisTemplate redisTemplate,
            @Qualifier("decrementScript") RedisScript<Long> decrementScript) {
        this.redisTemplate = redisTemplate;
        this.decrementScript = decrementScript;
    }

    @Override
    public Mono<Void> initStock(String sku, long quantity) {
        return redisTemplate.opsForValue()
                .set("stock:" + sku, String.valueOf(quantity))
                .then();
    }

    @Override
    public Mono<String> decrementStock(String sku, long quantity) {
        return redisTemplate.execute(
                decrementScript,
                List.of("stock:" + sku),
                List.of(String.valueOf(quantity))
        ).next()  // Flux -> Mono
        .map(Object::toString)
        .onErrorReturn("-1")
        .defaultIfEmpty("-1");
    }

    @Override
    public Mono<String> getStock(String sku) {
        return redisTemplate.opsForValue()
                .get("stock:" + sku)
                .switchIfEmpty(Mono.just("0"));
    }
}