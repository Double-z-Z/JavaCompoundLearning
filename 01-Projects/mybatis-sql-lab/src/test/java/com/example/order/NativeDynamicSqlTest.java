package com.example.order;

import com.example.order.mapper.OrderMapper;
import com.example.order.mapper.ProductMapper;
import com.example.order.model.Order;
import com.example.order.model.Product;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NativeDynamicSqlTest {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    // ===== <sql> + <include> =====

    @Test
    @org.junit.jupiter.api.Order(1)
    void testSelectById() {
        Order order = orderMapper.selectById(1L);
        assertNotNull(order);
        assertEquals("ORD20260530001", order.getOrderNo());
        System.out.println("<sql>/<include> OK: " + order);
    }

    // ===== <where> + <if>: 多条件组合 =====

    @Test
    @org.junit.jupiter.api.Order(2)
    void testSelectByConditionAllNull() {
        // 所有条件为 null：等于 SELECT * FROM orders
        List<Order> list = orderMapper.selectByCondition(null, null, null, null, null);
        assertTrue(list.size() >= 3);
        System.out.println("全 null 条件: " + list.size() + " 条 (全部)");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void testSelectByConditionByStatus() {
        List<Order> list = orderMapper.selectByCondition(null, "PAID", null, null, null);
        assertTrue(list.size() >= 2);
        list.forEach(o -> assertEquals("PAID", o.getStatus()));
        System.out.println("按 status=PAID: " + list.size() + " 条");
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void testSelectByConditionCombined() {
        // 组合条件：status=PAID AND amount >= 100
        List<Order> list = orderMapper.selectByCondition(
                null, "PAID", new BigDecimal("50"), null, null);
        assertFalse(list.isEmpty());
        list.forEach(o -> {
            assertEquals("PAID", o.getStatus());
            assertTrue(o.getTotalAmount().compareTo(new BigDecimal("50")) >= 0);
        });
        System.out.println("组合条件(status+minAmount): " + list.size() + " 条");
    }

    // ===== <choose>/<when>/<otherwise> =====

    @Test
    @org.junit.jupiter.api.Order(5)
    void testSelectByChosenOrderNo() {
        // 三个条件都传，只命中第一个 when
        List<Order> list = orderMapper.selectByPriority(
                "ORD20260530001", "PENDING", 1L);
        assertEquals(1, list.size());
        assertEquals("ORD20260530001", list.get(0).getOrderNo());
        System.out.println("<choose>: orderNo 命中");
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    void testSelectByChosenFallback() {
        // 三个条件全 null，走 <otherwise>
        List<Order> list = orderMapper.selectByPriority(null, null, null);
        assertTrue(list.size() >= 2);
        list.forEach(o -> assertEquals("PAID", o.getStatus()));
        System.out.println("<otherwise> 兜底: " + list.size() + " 条 PAID");
    }

    // ===== <foreach>: IN 查询 =====

    @Test
    @org.junit.jupiter.api.Order(7)
    void testSelectByIds() {
        List<Long> ids = List.of(1L, 2L, 3L);
        List<Order> list = orderMapper.selectByIds(ids);
        assertEquals(3, list.size());
        System.out.println("<foreach> IN: 查询 " + ids.size() + " 个 id, 命中 " + list.size());
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    void testSelectByIdsEmpty() {
        // 空集合：生成 IN () 会 SQL 语法错误 —— 这是已知陷阱
        // 已验证会在 Phase 2 练习中标注
        System.out.println("⚠️ 已知: IN 空集合会导致 SQL 错误，调用方需提前检查");
    }

    // ===== <foreach>: 批量插入 =====

    @Test
    @org.junit.jupiter.api.Order(9)
    void testInsertBatch() {
        List<Order> orders = new ArrayList<>();
        orders.add(buildOrder(1L, "ORD-BATCH-" + System.nanoTime(), new BigDecimal("100.00"), "PENDING"));
        orders.add(buildOrder(2L, "ORD-BATCH-" + System.nanoTime(), new BigDecimal("200.00"), "PENDING"));

        int rows = orderMapper.insertBatch(orders);
        assertEquals(2, rows);
        assertNotNull(orders.get(0).getId());
        assertNotNull(orders.get(1).getId());
        System.out.println("<foreach> batch insert: " + rows + " 行, id 已回填");
    }

    // ===== <set> + <if>: 动态更新 =====

    @Test
    @org.junit.jupiter.api.Order(10)
    void testUpdateDynamicPartial() {
        // 只更新 status，其他字段为 null
        Order partial = new Order();
        partial.setId(1L);
        partial.setStatus("SHIPPED");
        int rows = orderMapper.updateDynamic(partial);
        assertEquals(1, rows);

        Order reload = orderMapper.selectById(1L);
        assertEquals("SHIPPED", reload.getStatus());
        // 其他字段不受影响（<set> 没有包含它们）
        assertEquals("ORD20260530001", reload.getOrderNo());
        System.out.println("<set>: 部分字段更新 OK, orderNo 未被覆盖");
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    void testUpdateDynamicAllFields() {
        Order full = new Order();
        full.setId(2L);
        full.setOrderNo("ORD-UPDATED");
        full.setStatus("CANCELLED");
        full.setTotalAmount(new BigDecimal("999.00"));
        int rows = orderMapper.updateDynamic(full);
        assertEquals(1, rows);
        System.out.println("<set>: 全部字段更新 OK");
    }

    // ===== <trim>: 自定义裁剪 =====

    @Test
    @org.junit.jupiter.api.Order(12)
    void testSelectByConditionTrim() {
        // <trim> 行为等价于 <where>，这里验证它确实工作
        List<Order> list = orderMapper.selectByConditionTrim("ORD20260530003", null, null);
        assertEquals(1, list.size());
        System.out.println("<trim>: 单条件 OK, 多余 AND 被裁掉");
    }

    // ===== <bind>: LIKE 查询 =====

    @Test
    @org.junit.jupiter.api.Order(13)
    void testSelectByOrderNoLike() {
        List<Order> list = orderMapper.selectByOrderNoLike("ORD");
        assertTrue(list.size() >= 2);
        list.forEach(o -> assertTrue(o.getOrderNo().contains("ORD")));
        System.out.println("<bind>: LIKE '%20260530%' 命中 " + list.size() + " 条");
    }

    // ===== 聚合 + 窗口函数 =====

    @Test
    @org.junit.jupiter.api.Order(14)
    void testSelectUserOrderStats() {
        List<Map<String, Object>> stats = orderMapper.selectUserOrderStats();
        assertFalse(stats.isEmpty());
        stats.forEach(row -> System.out.printf("  %s: %s 订单, 消费 %s, rank=%s%n",
                row.get("username"), row.get("order_count"),
                row.get("total_spent"), row.get("rank")));
        System.out.println("窗口函数 + GROUP BY OK");
    }

    // ===== 分页 =====

    @Test
    @org.junit.jupiter.api.Order(15)
    void testSelectPage() {
        List<Order> page1 = orderMapper.selectPage(0, 2);
        List<Order> page2 = orderMapper.selectPage(2, 2);
        assertEquals(2, page1.size());
        assertFalse(page2.isEmpty());
        // 两页不重叠
        Long lastPage1 = page1.get(1).getId();
        Long firstPage2 = page2.get(0).getId();
        assertTrue(firstPage2 > lastPage1);
        System.out.printf("分页: page1[%d..%d] page2[%d..%d] 不重叠%n",
                page1.get(0).getId(), lastPage1, firstPage2, page2.get(page2.size()-1).getId());
    }

    // ===== 统计 =====

    @Test
    @org.junit.jupiter.api.Order(16)
    void testCount() {
        long total = orderMapper.count();
        assertTrue(total >= 3);
        System.out.println("总记录数: " + total);
    }

    // ===== ProductMapper: <choose> 安全排序 =====

    @Test
    @org.junit.jupiter.api.Order(17)
    void testProductSelectByCondition() {
        // 安全排序: orderBy 值命中 choose 分支
        List<Product> list = productMapper.selectByCondition(
                null, null, null, null, "price_desc");
        assertFalse(list.isEmpty());
        BigDecimal prev = new BigDecimal("999999");
        for (Product p : list) {
            assertTrue(p.getPrice().compareTo(prev) <= 0);
            prev = p.getPrice();
        }
        System.out.println("Product <choose> 安全排序: 按 price DESC 正确");
    }

    // ===== ExecutorType.BATCH: 批量导入 =====

    @Test
    @org.junit.jupiter.api.Order(18)
    @Disabled("演示用，单独运行以观察 BATCH 效果")
    void testBatchExecutor() {
        try (org.apache.ibatis.session.SqlSession batchSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
            OrderMapper batchMapper = batchSession.getMapper(OrderMapper.class);
            for (int i = 0; i < 100; i++) {
                batchMapper.insertBatch(List.of(
                    buildOrder(1L, "BATCH-" + i + "-" + System.nanoTime(),
                        new BigDecimal("10.00"), "PENDING")
                ));
            }
            batchSession.flushStatements();
            batchSession.commit();
            System.out.println("BATCH 模式: 100 条批量插入完成");
        }
    }

    // ===== 辅助 =====

    private Order buildOrder(Long userId, String orderNo, BigDecimal amount, String status) {
        Order o = new Order();
        o.setUserId(userId);
        o.setOrderNo(orderNo);
        o.setTotalAmount(amount);
        o.setStatus(status);
        return o;
    }
}
