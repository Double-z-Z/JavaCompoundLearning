package com.example.order.application.order;

import com.example.order.domain.order.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单应用服务 — 只依赖 OrderRepository 接口.
 *
 * 迁移/双写/灰度路由全部由基础设施层处理:
 * - 读路由: MigrationRoutingDataSource (AbstractRoutingDataSource)
 * - 双写: DoubleWriteInterceptor (MyBatis Plugin)
 * - 本服务零迁移感知
 */
@Service
public class OrderApplicationService {
    private static final Logger log = LoggerFactory.getLogger(OrderApplicationService.class);

    private final OrderRepository repository;

    public OrderApplicationService(OrderRepository repository) {
        this.repository = repository;
    }

    public Order createOrder(Long userId, String orderNo,
                              BigDecimal amount, List<String> productNames) {
        Order order = Order.create(null, userId, orderNo, amount, productNames);
        repository.save(order);
        log.info("Created order {} for user {}", order.getId(), userId);
        return order;
    }

    public List<Order> findByUserId(Long userId) {
        return repository.findByUserId(userId);
    }

    public List<Order> findByStatus(OrderStatus status) {
        return repository.findByStatus(status);
    }

    public List<Order> findByUserIdAndStatus(Long userId, OrderStatus status) {
        return repository.findByUserIdAndStatus(userId, status);
    }

    public long countAll() { return repository.countAll(); }
    public long countByStatus(OrderStatus status) { return repository.countByStatus(status); }
    public List<Order> pageByUserId(Long userId, int page, int size) {
        return repository.pageByUserId(userId, page, size);
    }
}
