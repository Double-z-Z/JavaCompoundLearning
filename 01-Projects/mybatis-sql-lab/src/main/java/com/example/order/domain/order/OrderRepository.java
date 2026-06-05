package com.example.order.domain.order;

import java.util.List;

/**
 * 订单仓储接口 — 纯领域概念，不含运维/迁移语义.
 */
public interface OrderRepository {

    List<Order> findByUserId(Long userId);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);

    Order save(Order order);

    long countAll();

    long countByStatus(OrderStatus status);

    List<Order> pageByUserId(Long userId, int page, int size);
}
