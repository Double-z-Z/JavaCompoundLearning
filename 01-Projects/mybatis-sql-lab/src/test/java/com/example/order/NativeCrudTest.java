package com.example.order;

import com.example.order.mapper.UserMapper;
import com.example.order.model.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NativeCrudTest {

    @Autowired
    private UserMapper mapper;

    private Long testUserId;

    @Test
    @org.junit.jupiter.api.Order(1)
    void testInsert() {
        User user = new User("测试用户", "test@example.com", "13000000000");
        int rows = mapper.insert(user);
        assertEquals(1, rows);
        assertNotNull(user.getId(), "insert 后 id 应被回填");
        testUserId = user.getId();
        System.out.println("插入成功: " + user);
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void testSelectById() {
        User user = mapper.selectById(testUserId);
        assertNotNull(user);
        assertEquals("测试用户", user.getUsername());
        System.out.println("查询结果: " + user);
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void testSelectAll() {
        List<User> users = mapper.selectAll();
        assertTrue(users.size() >= 4); // 3 条初始 + 1 条测试
        users.forEach(u -> System.out.println(u));
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void testSelectByUsername() {
        List<User> users = mapper.selectByUsername("张三");
        assertEquals(1, users.size());
        assertEquals("张三", users.get(0).getUsername());
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    void testSelectByEmailAndPhone() {
        List<User> users = mapper.selectByEmailAndPhone("test@example.com", "13000000000");
        assertEquals(1, users.size());
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    void testSelectByMap() {
        Map<String, Object> params = new HashMap<>();
        params.put("username", "张三");
        params.put("email", "zhangsan@example.com");
        List<User> users = mapper.selectByMap(params);
        assertEquals(1, users.size());
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    void testUpdate() {
        User user = mapper.selectById(testUserId);
        user.setUsername("测试用户-已更新");
        int rows = mapper.update(user);
        assertEquals(1, rows);
        // Spring SqlSessionTemplate 每次调用独立 session，无一级缓存共享
        User u1 = mapper.selectById(testUserId);
        User u2 = mapper.selectById(testUserId);
        assertEquals(u1.getId(), u2.getId());
        assertEquals(u1.getUsername(), u2.getUsername());
        System.out.println("更新后: " + u1.getUsername());
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    void testSelectAllOrderBy() {
        // ${} 方式 ORDER BY
        List<User> users = mapper.selectAllOrderBy("id DESC");
        assertFalse(users.isEmpty());
        Long prevId = Long.MAX_VALUE;
        for (User u : users) {
            assertTrue(u.getId() <= prevId, "应按 id DESC 排序");
            prevId = u.getId();
        }
        System.out.println("排序查询通过: 共 " + users.size() + " 条");
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    void testDeleteById() {
        int rows = mapper.deleteById(testUserId);
        assertEquals(1, rows);
        User user = mapper.selectById(testUserId);
        assertNull(user, "删除后应查不到");
    }
}
