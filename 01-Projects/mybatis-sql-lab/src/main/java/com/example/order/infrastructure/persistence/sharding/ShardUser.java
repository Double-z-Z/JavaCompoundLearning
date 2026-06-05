package com.example.order.infrastructure.persistence.sharding;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("t_user")
public class ShardUser {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String username;
    private String email;
    private String phone;
    private LocalDateTime createdAt;

    @Version
    private Integer version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
