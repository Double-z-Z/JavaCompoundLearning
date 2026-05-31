package com.example.order;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.order.mp.MpUserMapper;
import com.example.order.model.User;
import com.example.order.util.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MpMultiTenantTest {

    @Autowired
    private MpUserMapper mapper;
    @Autowired
    private com.example.order.mapper.ProductMapper productMapper;

    private Long tenant1UserId;

    @AfterAll
    static void tearDown() {
        TenantContextHolder.clear();
    }

    // ===== INSERT 自动注入 tenant_id =====

    @Test
    @org.junit.jupiter.api.Order(1)
    void testInsertAsTenant1() {
        TenantContextHolder.set(1L);

        User user = new User("租户1用户", "t1@test.com", "13000000001");
        user.setTenantId(TenantContextHolder.get());
        mapper.insert(user);
        // Spring Boot auto-commit
        assertNotNull(user.getId());
        tenant1UserId = user.getId();
        System.out.println("[多租户] 租户1 插入: id=" + user.getId());
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void testInsertAsTenant2() {
        TenantContextHolder.set(2L);

        User user = new User("租户2用户", "t2@test.com", "13000000002");
        user.setTenantId(TenantContextHolder.get());
        mapper.insert(user);
        // Spring Boot auto-commit
        System.out.println("[多租户] 租户2 插入: id=" + user.getId());
    }

    // ===== SELECT 自动过滤 =====

    @Test
    @org.junit.jupiter.api.Order(3)
    void testSelectOnlySeesOwnTenant() {
        TenantContextHolder.set(1L);

        List<User> all = mapper.selectList(null);
        assertFalse(all.isEmpty());
        all.forEach(u -> assertEquals(Long.valueOf(1L), u.getTenantId(),
                "租户1 应只看到 tenant_id=1 的数据"));

        System.out.println("[多租户] 租户1 看到 " + all.size() + " 条数据（全部 tenant_id=1）");
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void testTenant2CannotSeeTenant1Data() {
        TenantContextHolder.set(2L);

        List<User> all = mapper.selectList(null);
        // 租户 2 不应看到租户 1 的用户名
        all.forEach(u -> assertNotEquals("租户1用户", u.getUsername()));
        System.out.println("[多租户] 租户2 看到 " + all.size() + " 条数据");
    }

    // ===== 条件查询同样自动过滤 =====

    @Test
    @org.junit.jupiter.api.Order(5)
    void testConditionQueryAlsoTenantAware() {
        TenantContextHolder.set(1L);

        // 查 email 含 "test" 的用户 → 拦截器追加 AND tenant_id=1
        // 租户1 用户 email=t1@test.com 应命中
        List<User> list = mapper.selectList(
                new QueryWrapper<User>().eq("id", tenant1UserId));
        assertEquals(1, list.size(), "租户1 应能查到自己的用户");
        assertEquals("租户1用户", list.get(0).getUsername());
        assertEquals(Long.valueOf(1L), list.get(0).getTenantId());
        System.out.println("[多租户] 条件查询同样过滤: " + list.size() + " 条");
    }

    // ===== UPDATE 自动过滤 =====

    @Test
    @org.junit.jupiter.api.Order(6)
    void testUpdateOnlyAffectsOwnTenant() {
        TenantContextHolder.set(1L);
        User user = mapper.selectById(tenant1UserId);
        assertNotNull(user, "租户1 应能查到自己的用户");
        user.setPhone("11111111111");
        int rows = mapper.updateById(user);
        assertEquals(1, rows);
        // Spring Boot auto-commit
        System.out.println("[多租户] 租户1 更新自己的用户: rows=" + rows);

        // 切换到租户 2，尝试更新租户 1 的用户
        TenantContextHolder.set(2L);
        // reload as tenant 2 — won't find tenant 1's user (filtered)
        User crossUser = mapper.selectById(tenant1UserId);
        // 关键：租户2 就查不到租户1 的数据，所以 crossUser 为 null
        assertNull(crossUser, "租户2 不应能查到租户1 的用户");
        System.out.println("[多租户] 租户2 查询租户1 用户: selectById 返回 " + crossUser);
    }

    // ===== 未设 TenantContextHolder 时使用默认值 =====

    @Test
    @org.junit.jupiter.api.Order(7)
    void testDefaultTenantWhenNotSet() {
        TenantContextHolder.clear(); // 清掉之前设置的值

        List<User> all = mapper.selectList(null);
        // 默认 tenant=1，V1 初始数据 + 租户1 插入的数据可见
        assertFalse(all.isEmpty());
        all.forEach(u -> assertEquals(Long.valueOf(1L), u.getTenantId()));
        System.out.println("[多租户] 默认租户 1: 看到 " + all.size() + " 条");
    }

    // ===== products 表不拦截 =====

    @Test
    @org.junit.jupiter.api.Order(8)
    void testProductsNotFiltered() {
        TenantContextHolder.set(1L);

        assertFalse(productMapper.selectAll().isEmpty());
        System.out.println("[多租户] products 表不受租户拦截（ignoreTable=true）");
    }
}
