package com.example.order.infrastructure.persistence.sharding;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.order.domain.order.*;
import com.example.order.infrastructure.persistence.sharding.ShardOrderItemMapper;
import com.example.order.infrastructure.persistence.sharding.ShardOrderMapper;
import com.example.order.infrastructure.persistence.sharding.ShardUserMapper;
import com.example.order.infrastructure.persistence.sharding.ShardOrder;
import com.example.order.infrastructure.persistence.sharding.ShardOrderItem;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ShardingSphere 分片实现.
 *
 * 将 domain Order <-> infrastructure ShardOrder 之间的映射封装在此.
 * Controller/Service 只接触 domain 类型.
 */
@Repository("sharding")
@org.springframework.context.annotation.Primary
public class ShardingOrderRepository implements OrderRepository {

    private final ShardOrderMapper orderMapper;
    private final ShardOrderItemMapper orderItemMapper;

    public ShardingOrderRepository(ShardOrderMapper orderMapper,
                                    ShardOrderItemMapper orderItemMapper,
                                    ShardUserMapper userMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return orderMapper.selectList(
                Wrappers.<ShardOrder>lambdaQuery().eq(ShardOrder::getUserId, userId))
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Order> findByStatus(OrderStatus status) {
        return orderMapper.selectList(
                Wrappers.<ShardOrder>lambdaQuery().eq(ShardOrder::getStatus, status.name()))
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Order> findByUserIdAndStatus(Long userId, OrderStatus status) {
        return orderMapper.selectList(
                Wrappers.<ShardOrder>lambdaQuery()
                        .eq(ShardOrder::getUserId, userId)
                        .eq(ShardOrder::getStatus, status.name()))
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Order save(Order order) {
        ShardOrder so = toInfra(order);
        orderMapper.insert(so);
        order.setId(so.getId());  // 回填 Snowflake ID
        for (OrderItem item : order.getItems()) {
            ShardOrderItem si = toInfraItem(item, so.getId());
            orderItemMapper.insert(si);
        }
        return order;
    }

    @Override
    public long countAll() {
        return orderMapper.selectCount(Wrappers.emptyWrapper());
    }

    @Override
    public long countByStatus(OrderStatus status) {
        return orderMapper.selectCount(
                Wrappers.<ShardOrder>lambdaQuery().eq(ShardOrder::getStatus, status.name()));
    }

    @Override
    public List<Order> pageByUserId(Long userId, int page, int size) {
        return orderMapper.selectPage(new Page<>(page, size),
                Wrappers.<ShardOrder>lambdaQuery().eq(ShardOrder::getUserId, userId))
                .getRecords().stream().map(this::toDomain).collect(Collectors.toList());
    }

    // ===== 映射方法 =====

    private Order toDomain(ShardOrder so) {
        Order order = new Order();
        order.setId(so.getId());
        order.setUserId(so.getUserId());
        order.setOrderNo(so.getOrderNo());
        order.setTotalAmount(so.getTotalAmount());
        order.setStatus(OrderStatus.valueOf(so.getStatus()));
        order.setCreatedAt(so.getCreatedAt());
        return order;
    }

    private ShardOrder toInfra(Order order) {
        ShardOrder so = new ShardOrder();
        so.setId(order.getId());
        so.setUserId(order.getUserId());
        so.setOrderNo(order.getOrderNo());
        so.setTotalAmount(order.getTotalAmount());
        so.setStatus(order.getStatus().name());
        so.setCreatedAt(order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.now());
        return so;
    }

    private ShardOrderItem toInfraItem(OrderItem item, Long orderId) {
        ShardOrderItem si = new ShardOrderItem();
        si.setOrderId(orderId);
        si.setUserId(item.userId());
        si.setProductName(item.productName());
        si.setQuantity(item.quantity());
        si.setUnitPrice(item.unitPrice());
        return si;
    }
}
