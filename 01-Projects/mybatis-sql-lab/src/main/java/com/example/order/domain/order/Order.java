package com.example.order.domain.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单聚合根 — 充血模型.
 *
 * MyBatis 结果映射到此类，业务逻辑也在同一个类中.
 * 和旧 com.example.order.model.Order（贫血 POJO）不同，
 * 这里有 create()、addItem()、pay() 等 behavior.
 */
public class Order {
    private Long id;
    private Long userId;
    private String orderNo;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private List<OrderItem> items = new ArrayList<>();

    // ===== JPA/MyBatis 需要无参构造 =====
    public Order() {}

    // ===== 工厂方法 =====
    public static Order create(Long id, Long userId, String orderNo,
                                BigDecimal amount, List<String> productNames) {
        Order order = new Order();
        order.id = id;
        order.userId = userId;
        order.orderNo = orderNo;
        order.totalAmount = amount;
        order.status = OrderStatus.PENDING;
        order.createdAt = LocalDateTime.now();
        for (String name : productNames) {
            order.addItem(name, new BigDecimal("100"), 1);
        }
        return order;
    }

    // ===== 业务行为 =====
    public void addItem(String productName, BigDecimal unitPrice, int quantity) {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("只能向 PENDING 订单添加明细，当前状态：" + status);
        }
        items.add(new OrderItem(null, this.id, this.userId, productName, quantity, unitPrice));
        recalculateTotal();
    }

    public void pay() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("只能支付 PENDING 订单");
        }
        this.status = OrderStatus.PAID;
    }

    public void cancel() {
        if (status != OrderStatus.PENDING && status != OrderStatus.PAID) {
            throw new IllegalStateException("当前状态不可取消：" + status);
        }
        this.status = OrderStatus.CANCELLED;
    }

    private void recalculateTotal() {
        this.totalAmount = items.stream()
            .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ===== getter/setter（MyBatis 映射需要）=====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
}
