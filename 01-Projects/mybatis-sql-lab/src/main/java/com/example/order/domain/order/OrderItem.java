package com.example.order.domain.order;

import java.math.BigDecimal;

/**
 * 订单项 — 值对象 (不可变 record).
 */
public record OrderItem(
    Long id,
    Long orderId,
    Long userId,       // 分片键，同父订单
    String productName,
    int quantity,
    BigDecimal unitPrice
) {}
