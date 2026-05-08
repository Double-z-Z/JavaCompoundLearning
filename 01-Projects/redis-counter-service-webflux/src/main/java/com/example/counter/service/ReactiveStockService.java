package com.example.counter.service;

import reactor.core.publisher.Mono;

/**
 * 库存服务接口（响应式版本）
 */
public interface ReactiveStockService {

    /**
     * 初始化库存
     * @param sku SKU标识
     * @param quantity 初始数量
     * @return 完成信号
     */
    Mono<Void> initStock(String sku, long quantity);

    /**
     * 扣减库存
     * @param sku SKU标识
     * @param quantity 扣减数量
     * @return 扣减后的剩余库存（字符串），-1表示库存不足
     */
    Mono<String> decrementStock(String sku, long quantity);

    /**
     * 查询库存
     * @param sku SKU标识
     * @return 当前库存（字符串），不存在返回0
     */
    Mono<String> getStock(String sku);
}