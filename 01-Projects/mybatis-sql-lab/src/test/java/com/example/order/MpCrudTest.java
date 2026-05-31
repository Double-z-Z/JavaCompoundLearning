package com.example.order;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.order.mp.MpUserMapper;
import com.example.order.model.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MpCrudTest {

    @Autowired
    private MpUserMapper mpMapper;
    @Autowired
    private com.example.order.mapper.UserMapper nativeMapper;
    @Autowired
    private com.example.order.mapper.ProductMapper productMapper;

    // ===== BaseMapper: 单表 CRUD 零代码 =====

    @Test
    @org.junit.jupiter.api.Order(1)
    void testBaseMapperInsert() {
        User user = new User("MP测试", "mp@test.com", "13900000000");
        user.setTenantId(1L);
        int rows = mpMapper.insert(user);
        assertEquals(1, rows);
        assertNotNull(user.getId(), "MP 自动回填了自增 ID");
        System.out.println("[BaseMapper] insert: " + user);
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void testBaseMapperSelectById() {
        User user = mpMapper.selectById(1L);
        assertNotNull(user);
        assertEquals("张三", user.getUsername());
        System.out.println("[BaseMapper] selectById: " + user);
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void testBaseMapperSelectList() {
        List<User> all = mpMapper.selectList(null);
        assertTrue(all.size() >= 4);
        System.out.println("[BaseMapper] selectList(null): " + all.size() + " 条");
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void testBaseMapperUpdateById() {
        User user = mpMapper.selectById(1L);
        user.setPhone("13888888888");
        int rows = mpMapper.updateById(user);
        assertEquals(1, rows);
        assertEquals("13888888888", mpMapper.selectById(1L).getPhone());
        System.out.println("[BaseMapper] updateById OK");
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    void testBaseMapperDeleteById() {
        User tmp = new User("待删除", "del@test.com", "13600000000");
        tmp.setTenantId(1L);
        mpMapper.insert(tmp);
        int rows = mpMapper.deleteById(tmp.getId());
        assertEquals(1, rows);
        assertNull(mpMapper.selectById(tmp.getId()));
        System.out.println("[BaseMapper] deleteById OK");
    }

    // ===== SqlInjector: 验证 SQL 自动生成 =====

    @Test
    @org.junit.jupiter.api.Order(6)
    void testInjectorSelectById() {
        // 没有为 selectById 写任何 XML 或注解 SQL
        // 调用成功 → SQL 是 MP 的 AutoSqlInjector 在启动时注入的
        User user = mpMapper.selectById(1L);
        assertNotNull(user);
        System.out.println("[SqlInjector] 注入了 selectById，无 XML");
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    void testInjectorInsert() {
        User user = new User("注入测试", "inject@test.com", "13000000000");
        user.setTenantId(1L);
        int rows = mpMapper.insert(user);
        assertEquals(1, rows);
        assertNotNull(user.getId());
        System.out.println("[SqlInjector] 注入了 insert(id=" + user.getId() + ")，无 XML");
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    void testInjectorCount() {
        Long count = mpMapper.selectCount(null);
        assertTrue(count >= 4);
        System.out.println("[SqlInjector] 注入了 selectCount=" + count);
    }

    // ===== MP 与原生共存验证 =====

    @Test
    @org.junit.jupiter.api.Order(9)
    void testMpDoesNotBreakNative() {
        List<User> byNative = nativeMapper.selectByUsername("张三");
        assertEquals(1, byNative.size());

        List<User> byMp = mpMapper.selectList(
                new QueryWrapper<User>().eq("username", "张三"));
        assertEquals(1, byMp.size());

        assertEquals(byNative.get(0).getEmail(), byMp.get(0).getEmail());
        System.out.println("[共存] 原生 XML 和 MP SqlInjector 结果一致");
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    void testMpWrapperOnNativeTable() {
        assertFalse(productMapper.selectAll().isEmpty());
        System.out.println("[共存] 原生 Mapper 不受 MP 影响: products 正常查询");
    }

    // ===== 对比: 同一操作，MP vs 原生 =====

    @Test
    @org.junit.jupiter.api.Order(11)
    void testCompareNativeVsMp() {
        String targetUsername = "张三";

        List<User> nativeResult = nativeMapper.selectByUsername(targetUsername);

        List<User> mpResult = mpMapper.selectList(
                new QueryWrapper<User>().eq("username", targetUsername));

        assertEquals(nativeResult.size(), mpResult.size());
        assertEquals(nativeResult.get(0).getEmail(), mpResult.get(0).getEmail());
        System.out.println("[对比] 原生 vs MP 结果一致, email=" + nativeResult.get(0).getEmail());
    }
}
