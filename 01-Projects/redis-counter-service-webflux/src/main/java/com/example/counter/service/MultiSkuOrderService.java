package com.example.counter.service;

import com.example.counter.dto.MultiSkuOrderRequest;
import com.example.counter.dto.OrderResult;
import reactor.core.publisher.Mono;

/**
 * 多SKU下单服务接口
 * 支持购物车批量下单，采用Saga补偿模式
 */
public interface MultiSkuOrderService {

    /**
     * 多SKU下单
     * @param request 包含多个SKU及其数量的下单请求
     * @return 下单结果（成功/失败+已扣减+失败信息）
     */
    Mono<OrderResult> placeOrder(MultiSkuOrderRequest request);
}