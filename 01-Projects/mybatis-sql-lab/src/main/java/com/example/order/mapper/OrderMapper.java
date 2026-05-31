package com.example.order.mapper;

import com.example.order.model.Order;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface OrderMapper {

    // ===== <sql> + <include>: SQL 片段复用 =====
    Order selectById(Long id);

    // ===== <if> + <where>: 多条件可选查询 =====
    List<Order> selectByCondition(@Param("orderNo") String orderNo,
                                  @Param("status") String status,
                                  @Param("minAmount") BigDecimal minAmount,
                                  @Param("maxAmount") BigDecimal maxAmount,
                                  @Param("userId") Long userId);

    // ===== <choose>/<when>/<otherwise>: 互斥分支 =====
    List<Order> selectByPriority(@Param("orderNo") String orderNo,
                                  @Param("status") String status,
                                  @Param("userId") Long userId);

    // ===== <foreach>: IN 查询 =====
    List<Order> selectByIds(@Param("ids") List<Long> ids);

    // ===== <foreach>: 批量插入 =====
    int insertBatch(@Param("orders") List<Order> orders);

    // ===== <set> + <if>: 动态更新 =====
    int updateDynamic(Order order);

    // ===== <trim>: 自定义前缀/后缀裁剪 =====
    List<Order> selectByConditionTrim(@Param("orderNo") String orderNo,
                                       @Param("status") String status,
                                       @Param("minAmount") BigDecimal minAmount);

    // ===== <bind>: OGNL 变量绑定（LIKE 查询安全拼接） =====
    List<Order> selectByOrderNoLike(@Param("keyword") String keyword);

    // ===== 聚合查询：窗口函数 =====
    List<Map<String, Object>> selectUserOrderStats();

    // ===== 分页查询（配合 LIMIT/OFFSET） =====
    List<Order> selectPage(@Param("offset") int offset, @Param("limit") int limit);

    // ===== 统计 =====
    long count();

    // ===== Phase 3: ResultMap 关联映射 =====

    // 一对一：Order → User（JOIN 查询，单条 SQL）
    Order selectOrderWithUser(@Param("id") Long id);

    // 一对多：Order → List<OrderItem>（JOIN 查询）
    Order selectOrderWithItems(@Param("id") Long id);

    // 完整嵌套：Order → User + OrderItem → Product
    Order selectOrderFull(@Param("id") Long id);

    // 嵌套查询演示（N+1 问题）：先查 Order，再单独查 User
    Order selectOrderNestedSelect(@Param("id") Long id);

    // ===== Phase 3: 存储过程 =====
    void callUpdateOrderStatus(@Param("orderId") Long orderId,
                                @Param("newStatus") String newStatus);

    // ===== 悲观锁: SELECT ... FOR UPDATE =====
    Order selectForUpdate(@Param("id") Long id);
}
