package com.example.order.interfaces.rest;

import com.example.order.application.order.OrderApplicationService;
import com.example.order.domain.order.Order;
import com.example.order.domain.order.OrderStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 订单 REST 控制器 — 只依赖 application 层，不感知分片/单库.
 */
@RestController
@RequestMapping("/api/v2/orders")
public class OrderController {

    private final OrderApplicationService orderService;

    public OrderController(OrderApplicationService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody CreateOrderRequest req) {
        Order order = orderService.createOrder(
                req.userId(), req.orderNo(), req.totalAmount(), req.productNames());
        return Map.of("id", order.getId(), "userId", order.getUserId(), "status", order.getStatus().name());
    }

    @GetMapping
    public List<Order> listByUser(@RequestParam Long userId) {
        return orderService.findByUserId(userId);
    }

    @GetMapping("/by-status")
    public List<Order> listByStatus(@RequestParam String status) {
        return orderService.findByStatus(OrderStatus.valueOf(status));
    }

    @GetMapping("/page-by-user")
    public List<Order> pageByUser(@RequestParam Long userId,
                                   @RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "10") int size) {
        return orderService.pageByUserId(userId, page, size);
    }

    @GetMapping("/count")
    public Map<String, Long> count(@RequestParam(required = false) String status) {
        long count = status != null
                ? orderService.countByStatus(OrderStatus.valueOf(status))
                : orderService.countAll();
        return Map.of("count", count);
    }

}
