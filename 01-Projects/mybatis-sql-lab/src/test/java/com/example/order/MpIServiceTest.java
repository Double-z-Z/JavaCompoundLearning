package com.example.order;

import com.example.order.model.User;
import com.example.order.service.UserService;
import com.example.order.util.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IService 验证：链式查询、批量操作、saveOrUpdate。
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MpIServiceTest {

    @Autowired
    private UserService userService;

    @BeforeAll
    static void setUp() {
        TenantContextHolder.set(1L);
    }

    @AfterAll
    static void tearDown() {
        TenantContextHolder.clear();
    }

    // ===== 链式查询 lambdaQuery() —— 不需要手动 new LambdaQueryWrapper =====

    @Test
    @org.junit.jupiter.api.Order(1)
    void testLambdaQueryChain() {
        // 相当于: mapper.selectList(new LambdaQueryWrapper<User>().eq(User::getUsername, "张三"))
        List<User> list = userService.lambdaQuery()
                .eq(User::getUsername, "张三")
                .list();

        assertEquals(1, list.size());
        assertEquals("张三", list.get(0).getUsername());
        System.out.println("[IService] lambdaQuery().eq().list(): " + list.get(0));
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void testLambdaQueryGetOne() {
        User user = userService.lambdaQuery()
                .eq(User::getUsername, "李四")
                .one();  // getOne 期望一条结果

        assertNotNull(user);
        assertEquals("李四", user.getUsername());
        System.out.println("[IService] lambdaQuery().one(): " + user);
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void testLambdaQueryCount() {
        long count = userService.lambdaQuery()
                .like(User::getEmail, "example")
                .count();

        assertTrue(count >= 3, "至少张三、李四、王五的邮箱含 example");
        System.out.println("[IService] lambdaQuery().count(): " + count);
    }

    // ===== 链式更新 lambdaUpdate() —— 一行写完 UPDATE =====

    @Test
    @org.junit.jupiter.api.Order(4)
    void testLambdaUpdateChain() {
        // 相当于: UPDATE users SET phone=? WHERE username=? AND tenant_id=?
        boolean ok = userService.lambdaUpdate()
                .eq(User::getUsername, "王五")
                .set(User::getPhone, "ISERVICE-UPDATE")
                .update();

        assertTrue(ok);
        // 验证
        User reloaded = userService.lambdaQuery()
                .eq(User::getUsername, "王五")
                .one();
        assertEquals("ISERVICE-UPDATE", reloaded.getPhone());
        System.out.println("[IService] lambdaUpdate().set().update() → phone=" + reloaded.getPhone());
    }

    // ===== saveOrUpdate：id 为 null 则 insert，否则 update =====

    @Test
    @org.junit.jupiter.api.Order(5)
    void testSaveOrUpdateInsert() {
        User user = new User("IService新用户", "iservice@test.com", "13000000000");
        user.setTenantId(1L);
        user.setVersion(0);
        // id == null → INSERT
        boolean ok = userService.saveOrUpdate(user);
        assertTrue(ok);
        assertNotNull(user.getId());
        System.out.println("[IService] saveOrUpdate(无id) → INSERT: " + user);
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    void testSaveOrUpdateUpdate() {
        // 先查一个已有用户
        User user = userService.lambdaQuery()
                .eq(User::getUsername, "张三")
                .one();
        user.setPhone("SAVEORUPDATE");
        // id != null → UPDATE
        boolean ok = userService.saveOrUpdate(user);
        assertTrue(ok);

        User reloaded = userService.getById(user.getId());
        assertEquals("SAVEORUPDATE", reloaded.getPhone());
        System.out.println("[IService] saveOrUpdate(有id) → UPDATE: phone=" + reloaded.getPhone());
    }

    // ===== 批量操作 =====

    @Test
    @org.junit.jupiter.api.Order(7)
    void testSaveBatch() {
        List<User> users = List.of(
                newUser("批量1", "batch1@test.com"),
                newUser("批量2", "batch2@test.com"),
                newUser("批量3", "batch3@test.com")
        );
        boolean ok = userService.saveBatch(users);
        assertTrue(ok);
        users.forEach(u -> assertNotNull(u.getId(), "批量 insert 后 id 应被回填"));
        System.out.println("[IService] saveBatch(3): ids=" + users.stream().map(User::getId).toList());
    }

    // ===== count / list / getById =====

    @Test
    @org.junit.jupiter.api.Order(8)
    void testBaseMethods() {
        long total = userService.count();
        assertTrue(total >= 7);

        User user = userService.getById(1L);
        assertNotNull(user);
        assertEquals("张三", user.getUsername());

        List<User> list = userService.list();
        assertFalse(list.isEmpty());

        System.out.printf("[IService] count=%d, getById(1)=%s, list.size=%d%n",
                total, user.getUsername(), list.size());
    }

    // ===== 分页 =====

    @Test
    @org.junit.jupiter.api.Order(9)
    void testPage() {
        com.baomidou.mybatisplus.core.metadata.IPage<User> page =
                userService.lambdaQuery()
                        .orderByDesc(User::getId)
                        .page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 3));

        assertEquals(3, page.getRecords().size());
        assertTrue(page.getTotal() >= 3);
        System.out.printf("[IService] page(1,3): records=%d, total=%d%n",
                page.getRecords().size(), page.getTotal());
    }

    private User newUser(String username, String email) {
        User u = new User(username, email, "13000000000");
        u.setTenantId(1L);
        u.setVersion(0);
        return u;
    }
}
