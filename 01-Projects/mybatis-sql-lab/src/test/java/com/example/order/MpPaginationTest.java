package com.example.order;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.order.mp.MpUserMapper;
import com.example.order.model.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MpPaginationTest {

    @Autowired
    private MpUserMapper mapper;

    // ===== 分页插件基础 =====

    @Test
    @org.junit.jupiter.api.Order(1)
    void testPagination() {
        Page<User> page = new Page<>(1, 2);
        IPage<User> result = mapper.selectPage(page, null);

        assertEquals(2, result.getRecords().size());
        assertTrue(result.getTotal() >= 4);

        System.out.printf("[分页] page=%d/%d, size=%d, total=%d%n",
                result.getCurrent(), result.getPages(), result.getRecords().size(), result.getTotal());
        result.getRecords().forEach(u -> System.out.println("  -> " + u.getUsername()));
    }

    // ===== 分页拦截器验证 =====

    @Test
    @org.junit.jupiter.api.Order(2)
    void testPaginationRewritesSql() {
        // MP 会发两条 SQL:
        // 1. SELECT COUNT(*) FROM users (查总数)
        // 2. SELECT ... FROM users LIMIT ? (查当前页)
        Page<User> page = new Page<>(1, 2);
        IPage<User> result = mapper.selectPage(page, null);

        assertEquals(2, result.getRecords().size());
        assertTrue(result.getTotal() >= 4);
        assertTrue(result.getPages() >= 2);
        assertEquals(2, result.getSize());
        System.out.printf("[分页拦截] SQL 被改写, total=%d, pages=%d%n",
                result.getTotal(), result.getPages());
    }
}
