package com.example.counter.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 热点库存本地缓存实现
 * 使用 Caffeine 作为本地 LRU 缓存，减少 Redis 请求
 */
@Service
public class HotStockCacheServiceImpl implements HotStockCacheService {

    private static final Logger log = LoggerFactory.getLogger(HotStockCacheServiceImpl.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<Long> decrementScript;

    // 本地缓存：热点商品在这里被缓存
    private final Cache<String, String> localCache;

    public HotStockCacheServiceImpl(
            ReactiveStringRedisTemplate redisTemplate,
            @Qualifier("decrementScript") RedisScript<Long> decrementScript) {
        this.redisTemplate = redisTemplate;
        this.decrementScript = decrementScript;

        // 配置 Caffeine：最大1000条，过期时间5分钟
        this.localCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    @Override
    public Mono<Void> initWithCache(String sku, long quantity) {
        String key = "stock:" + sku;
        // 同时写 Redis 和本地缓存
        return redisTemplate.opsForValue()
                .set(key, String.valueOf(quantity))
                .doOnNext(v -> {
                    localCache.put(key, String.valueOf(quantity));
                    log.info("Initialized {} with cache: {}", sku, quantity);
                })
                .then();
    }

    @Override
    public Mono<String> getStockWithCache(String sku) {
        String key = "stock:" + sku;

        // 先查本地缓存
        String cached = localCache.getIfPresent(key);
        if (cached != null) {
            log.debug("Cache hit for {}", sku);
            return Mono.just(cached);
        }

        // 缓存未命中，查 Redis
        log.debug("Cache miss for {}", sku);
        return redisTemplate.opsForValue()
                .get(key)
                .switchIfEmpty(Mono.just("0"))
                .doOnNext(stock -> {
                    // 写入本地缓存
                    localCache.put(key, stock);
                });
    }

    @Override
    public Mono<String> decrementWithCache(String sku, long quantity) {
        String key = "stock:" + sku;

        // 直接在 Redis 执行 Lua 扣减（保持原子性）
        return redisTemplate.execute(
                        decrementScript,
                        List.of(key),
                        List.of(String.valueOf(quantity))
                ).next()
                .map(result -> {
                    long remaining = result.longValue();
                    // 扣减成功后失效本地缓存（确保下次查到最新值）
                    if (remaining != -1) {
                        localCache.invalidate(key);
                        log.debug("Decremented {} to {}, invalidated cache", sku, remaining);
                    }
                    return String.valueOf(remaining);
                })
                .onErrorReturn("-1")
                .defaultIfEmpty("-1");
    }

    @Override
    public void invalidate(String sku) {
        String key = "stock:" + sku;
        localCache.invalidate(key);
        log.debug("Invalidated cache for {}", sku);
    }

    @Override
    public void invalidateAll(Iterable<String> skus) {
        skus.forEach(sku -> invalidate(sku));
    }
}