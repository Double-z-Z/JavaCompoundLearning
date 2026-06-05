package com.example.order.infrastructure.persistence.sharding;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@TableName("orders")
public class ShardOrder {
    @TableId(type = IdType.ASSIGN_ID)  // ShardingSphere Snowflake
    private Long id;
    private Long userId;
    private String orderNo;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime createdAt;

    // ResultMap 关联查询用 — 不是数据库列
    @TableField(exist = false)
    private ShardUser user;
    @TableField(exist = false)
    private List<ShardOrderItem> items;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public ShardUser getUser() { return user; }
    public void setUser(ShardUser user) { this.user = user; }
    public List<ShardOrderItem> getItems() { return items; }
    public void setItems(List<ShardOrderItem> items) { this.items = items; }
}
