package com.example.order.infrastructure.persistence.sharding;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface ShardOrderMapper extends BaseMapper<ShardOrder> {

    // 1. <sql> + <include>: SQL片段复用
    ShardOrder selectById(Long id);

    // 2. <where> + <if>: 多条件可选查询
    List<ShardOrder> selectByCondition(@Param("orderNo") String orderNo,
                                        @Param("status") String status,
                                        @Param("minAmount") BigDecimal minAmount,
                                        @Param("maxAmount") BigDecimal maxAmount,
                                        @Param("userId") Long userId);

    // 3. <choose>/<when>/<otherwise>: 互斥分支
    List<ShardOrder> selectByPriority(@Param("orderNo") String orderNo,
                                       @Param("status") String status,
                                       @Param("userId") Long userId);

    // 4. <foreach>: IN查询
    List<ShardOrder> selectByIds(@Param("ids") List<Long> ids);

    // 5. <foreach>: 批量插入
    int insertBatch(@Param("orders") List<ShardOrder> orders);

    // 6. <set> + <if>: 动态更新
    int updateDynamic(ShardOrder order);

    // 7. <trim>: 自定义裁剪
    List<ShardOrder> selectByConditionTrim(@Param("orderNo") String orderNo,
                                            @Param("status") String status,
                                            @Param("minAmount") BigDecimal minAmount);

    // 8. <bind>: LIKE 安全拼接
    List<ShardOrder> selectByOrderNoLike(@Param("keyword") String keyword);

    // 9. 窗口函数 + GROUP BY
    List<Map<String, Object>> selectUserOrderStats();

    // 10. 分页
    List<ShardOrder> selectPage(@Param("offset") int offset, @Param("limit") int limit);

    // 11. 统计
    long count();

    // 12a. <association>: 一对一
    ShardOrder selectOrderWithUser(@Param("id") Long id);

    // 12b. <collection>: 一对多
    ShardOrder selectOrderWithItems(@Param("id") Long id);

    // 12c. 完整嵌套
    ShardOrder selectOrderFull(@Param("id") Long id);

    // 13. N+1 演示
    ShardOrder selectOrderNestedSelect(@Param("id") Long id);

    // 14. 存储过程
    void callUpdateOrderStatus(@Param("orderId") Long orderId, @Param("newStatus") String newStatus);

    // 15. 悲观锁
    ShardOrder selectForUpdate(@Param("id") Long id);
}
