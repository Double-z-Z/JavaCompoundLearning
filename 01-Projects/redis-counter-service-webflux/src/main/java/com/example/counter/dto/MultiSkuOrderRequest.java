package com.example.counter.dto;

import java.util.List;

public class MultiSkuOrderRequest {
    private List<OrderItem> items;

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
}