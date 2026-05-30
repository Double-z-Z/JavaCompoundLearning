package com.example.order.mp;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.order.model.Order;
import com.example.order.model.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 混合 Mapper：BaseMapper 提供单表 CRUD + 手写方法提供关联查询。
 * MP 和原生 MyBatis 可以在同一个接口中共存。
 */
public interface MpUserMapper extends BaseMapper<User> {

    // ===== 以下方法来自 BaseMapper（零 XML）：=====
    // insert, deleteById, updateById, selectById, selectList, selectPage, ...

    // ===== 以下方法是手写的关联查询（需要 XML）：=====

    /** 一对一：用户 → 所有订单（需要 XML ResultMap） */
    List<User> selectUserWithOrders(@Param("userId") Long userId);

    /** 用 @Select 注解做简单 JOIN（不需要 XML，但只能平铺映射） */
    @org.apache.ibatis.annotations.Select(
        "SELECT o.id, o.order_no, o.total_amount, o.status " +
        "FROM orders o WHERE o.user_id = #{userId}")
    List<Order> selectOrdersByUserId(@Param("userId") Long userId);
}
