package com.example.order.mapper;

import com.example.order.model.Product;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface ProductMapper {

    Product selectById(Long id);
    List<Product> selectAll();
    int insert(Product product);
    int update(Product product);
    int deleteById(Long id);

    // ---- 动态 SQL 将在 Phase 2 扩展 ----
    List<Product> selectByCondition(@Param("name") String name,
                                    @Param("category") String category,
                                    @Param("minPrice") java.math.BigDecimal minPrice,
                                    @Param("maxPrice") java.math.BigDecimal maxPrice,
                                    @Param("orderBy") String orderBy);
}
