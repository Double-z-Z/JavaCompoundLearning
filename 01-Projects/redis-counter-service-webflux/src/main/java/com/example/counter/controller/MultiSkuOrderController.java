package com.example.counter.controller;

import com.example.counter.dto.MultiSkuOrderRequest;
import com.example.counter.dto.OrderResult;
import com.example.counter.service.MultiSkuOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * 多SKU下单控制器
 * 提供购物车批量下单接口
 */
@RestController
@RequestMapping("/order")
public class MultiSkuOrderController {

    private final MultiSkuOrderService orderService;

    public MultiSkuOrderController(MultiSkuOrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/multi-sku")
    public Mono<ResponseEntity<OrderResult>> placeOrder(
            @RequestBody Mono<MultiSkuOrderRequest> requestMono) {
        return requestMono
                .flatMap(orderService::placeOrder)
                .map(ResponseEntity::ok);
    }
}