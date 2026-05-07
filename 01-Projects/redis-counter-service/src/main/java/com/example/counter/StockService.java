package com.example.counter;

import com.example.counter.dto.DecrementResult;

import java.util.List;

/**
 * 库存服务接口
 * 提供库存初始化、扣减、查询操作
 */
public interface StockService {

    /**
     * 初始化库存
     * @param sku SKU标识
     * @param quantity 初始数量
     */
    void initStock(String sku, long quantity);

    /**
     * 扣减库存
     * @param sku SKU标识
     * @param quantity 扣减数量
     * @return 扣减后的剩余库存，-1表示库存不足
     */
    Long decrementStock(String sku, long quantity);

    /**
     * 批量扣减库存
     * @param sku SKU标识
     * @param quantities 扣减数量列表
     * @return 每笔扣减的结果
     */
    List<DecrementResult> batchDecrementStock(String sku, List<Long> quantities);

    /**
     * 查询库存
     * @param sku SKU标识
     * @return 当前库存，不存在返回0
     */
    Long getStock(String sku);
}