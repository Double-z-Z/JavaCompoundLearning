package com.example.order.interfaces.rest;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
    Long userId,
    String orderNo,
    BigDecimal totalAmount,
    List<String> productNames
) {}
