package com.example.counter.strategy;

import java.util.List;

/**
 * 库存扣减策略接口
 * 支持三种模式：原子策略、Pipeine原子策略、原始Pipeline策略
 */
public interface DecrementStrategy {

    /**
     * 获取策略名称
     */
    String getName();

    /**
     * 单次扣减
     * @param sku 商品SKU
     * @param quantity 扣减数量
     * @return 剩余库存，-1表示库存不足
     */
    Long decrement(String sku, long quantity);

    /**
     * 批量扣减
     * @param sku 商品SKU
     * @param quantities 扣减数量列表
     * @return 每笔扣减后的剩余库存列表（按顺序）
     */
    List<Long> batchDecrement(String sku, List<Long> quantities);
}
