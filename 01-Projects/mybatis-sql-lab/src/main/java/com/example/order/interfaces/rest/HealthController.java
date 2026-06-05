package com.example.order.interfaces.rest;

import com.example.order.application.order.OrderApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v2")
public class HealthController {

    private final OrderApplicationService orderService;

    public HealthController(OrderApplicationService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "service", "mybatis-sql-lab (DDD refactored)",
            "totalOrders", orderService.countAll()
        );
    }
}
