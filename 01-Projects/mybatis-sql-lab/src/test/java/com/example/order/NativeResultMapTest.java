package com.example.order;

import com.example.order.mapper.OrderMapper;
import com.example.order.model.Order;
import com.example.order.model.OrderItem;
import com.example.order.model.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NativeResultMapTest {

    @Autowired
    private OrderMapper mapper;

    // ===== 一对一：<association> 嵌套结果映射 =====

    @Test
    @org.junit.jupiter.api.Order(1)
    void testAssociation() {
        // Order id=1 的 user_id=1（张三）
        Order order = mapper.selectOrderWithUser(1L);
        assertNotNull(order);
        assertEquals("ORD20260530001", order.getOrderNo());

        // 关键断言：user 对象被自动填充了
        User user = order.getUser();
        assertNotNull(user, "association 应自动填充 User 对象");
        assertEquals("张三", user.getUsername());
        assertEquals("zhangsan@example.com", user.getEmail());

        System.out.println("=== 一对一 (association) ===");
        System.out.printf("订单: %s | 用户: %s (%s)%n",
                order.getOrderNo(), user.getUsername(), user.getEmail());
    }

    // ===== 一对多：<collection> 嵌套结果映射 =====

    @Test
    @org.junit.jupiter.api.Order(2)
    void testCollection() {
        // Order id=1 有 2 个 order_items（机械键盘 + 显示器）
        Order order = mapper.selectOrderWithItems(1L);
        assertNotNull(order);
        assertNotNull(order.getItems(), "collection 应自动填充 items 列表");
        assertEquals(2, order.getItems().size());

        System.out.println("=== 一对多 (collection) ===");
        for (OrderItem item : order.getItems()) {
            assertNotNull(item.getProduct(), "OrderItem 里的 Product 也应被填充");
            System.out.printf("  商品: %s | 数量: %d | 单价: %s | 小计: %s%n",
                    item.getProduct().getName(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getUnitPrice().multiply(
                        java.math.BigDecimal.valueOf(item.getQuantity())));
        }
    }

    // ===== 完整嵌套：association + collection 同时使用 =====

    @Test
    @org.junit.jupiter.api.Order(3)
    void testFullNested() {
        Order order = mapper.selectOrderFull(1L);
        assertNotNull(order);
        assertNotNull(order.getUser(), "同时有 User");
        assertNotNull(order.getItems(), "同时有 OrderItem 列表");
        assertEquals("张三", order.getUser().getUsername());
        assertEquals(2, order.getItems().size());
        // 每个 item 也有 product
        order.getItems().forEach(i -> assertNotNull(i.getProduct()));

        System.out.println("=== 完整嵌套 (association + collection) ===");
        System.out.printf("订单: %s | 用户: %s | 商品数: %d%n",
                order.getOrderNo(), order.getUser().getUsername(), order.getItems().size());
        order.getItems().forEach(i ->
            System.out.printf("  → %s x%d%n", i.getProduct().getName(), i.getQuantity()));
    }

    // ===== 嵌套查询 (Nested Select) — N+1 演示 =====

    @Test
    @org.junit.jupiter.api.Order(4)
    void testNestedSelect() {
        Order order = mapper.selectOrderNestedSelect(1L);
        assertNotNull(order);
        assertNotNull(order.getUser());
        assertEquals("张三", order.getUser().getUsername());

        System.out.println("=== 嵌套查询 (Nested Select) ===");
        System.out.printf("订单: %s | 用户: %s%n",
                order.getOrderNo(), order.getUser().getUsername());
        System.out.println("注意日志: 执行了 2 条 SQL（1 查 Order + 1 查 User）");
    }

    // ===== 一对多时的 <id> 分组验证 =====

    @Test
    @org.junit.jupiter.api.Order(5)
    void testCollectionGrouping() {
        // Order id=1 有 2 个 items，验证 MyBatis 正确聚合成一个 Order 对象
        Order order = mapper.selectOrderWithItems(1L);
        assertNotNull(order);
        assertEquals(2, order.getItems().size(),
                "2 个 order_items 应被 <collection> 聚合到一个 List 中（由 <id> 字段分组）");

        System.out.println("=== <id> 分组验证 ===");
        System.out.printf("订单: %s | items: %d (正确聚合到一个 Order)%n",
                order.getOrderNo(), order.getItems().size());
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    void testEmptyCollection() {
        // 没有订单项的订单：collection 为 null 或空列表
        Order order = mapper.selectOrderWithItems(999L);
        assertNull(order, "不存在订单应返回 null");
    }

    // ===== 存储过程 =====

    @Test
    @org.junit.jupiter.api.Order(7)
    void testStoredProcedure() {
        // 先用存储过程把订单状态改为 CONFIRMED
        mapper.callUpdateOrderStatus(3L, "CONFIRMED");

        // 验证
        Order order = mapper.selectById(3L);
        assertEquals("CONFIRMED", order.getStatus());

        System.out.println("=== 存储过程 ===");
        System.out.printf("订单 id=3 状态已变更为: %s%n", order.getStatus());
    }
}
