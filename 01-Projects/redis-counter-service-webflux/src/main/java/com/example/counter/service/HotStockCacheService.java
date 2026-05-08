package com.example.counter.service;

import reactor.core.publisher.Mono;

/**
 * 热点库存本地缓存服务
 * 使用 Caffeine 实现本地 LRU 缓存，吸收热点请求
 */
public interface HotStockCacheService {

    /**
     * 初始化热点库存（同时写Redis和本地缓存）
     * @param sku SKU标识
     * @param quantity 初始数量
     * @return 完成信号
     */
    Mono<Void> initWithCache(String sku, long quantity);

    /**
     * 带本地缓存的库存查询
     * @param sku SKU标识
     * @return 当前库存（先查本地缓存，未命中查Redis）
     */
    Mono<String> getStockWithCache(String sku);

    /**
     * 带本地缓存的库存扣减
     * @param sku SKU标识
     * @param quantity 扣减数量
     * @return 扣减后的剩余库存，-1表示库存不足
     */
    Mono<String> decrementWithCache(String sku, long quantity);

    /**
     * 失效本地缓存（库存更新后调用）
     * @param sku SKU标识
     */
    void invalidate(String sku);

    /**
     * 批量失效
     * @param skus SKU列表
     */
    void invalidateAll(Iterable<String> skus);
}