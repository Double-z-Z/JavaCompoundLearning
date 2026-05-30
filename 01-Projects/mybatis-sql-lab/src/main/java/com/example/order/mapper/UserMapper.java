package com.example.order.mapper;

import com.example.order.model.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface UserMapper {

    // ---- 静态 CRUD ----
    User selectById(Long id);
    List<User> selectAll();
    int insert(User user);
    int update(User user);
    int deleteById(Long id);

    // ---- 参数传递方式对比 ----
    // 1. 单个基本类型参数（xml 中可用任意名）
    List<User> selectByUsername(String username);

    // 2. @Param 命名参数
    List<User> selectByEmailAndPhone(@Param("email") String email, @Param("phone") String phone);

    // 3. Map 参数（不推荐，但常见于老项目）
    List<User> selectByMap(Map<String, Object> params);

    // ---- #{} vs ${} 对比 ----
    // ${} 用于 ORDER BY 等场景，需要调用方保证安全
    List<User> selectAllOrderBy(@Param("column") String column);

    // ---- 批量操作 ----
    int insertBatch(@Param("users") List<User> users);
}
