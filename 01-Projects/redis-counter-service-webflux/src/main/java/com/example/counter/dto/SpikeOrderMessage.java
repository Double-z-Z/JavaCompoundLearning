package com.example.counter.dto;

import java.io.Serializable;
import java.util.List;

/**
 * MQ 订单消息
 * 预扣库存成功后写入MQ，异步创建订单
 */
public class SpikeOrderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderId;
    private String userId;
    private List<OrderItem> items;
    private long timestamp;
    private String requestId;

    public SpikeOrderMessage() {}

    public SpikeOrderMessage(String orderId, String userId, List<OrderItem> items, long timestamp, String requestId) {
        this.orderId = orderId;
        this.userId = userId;
        this.items = items;
        this.timestamp = timestamp;
        this.requestId = requestId;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}