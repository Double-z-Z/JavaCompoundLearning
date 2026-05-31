package com.example.order;

import com.example.order.mp.MpUserMapper;
import com.example.order.model.User;
import com.example.order.util.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MpOptimisticLockTest {

    @Autowired
    private MpUserMapper mapper;
    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    private Long testUserId;

    @BeforeAll
    static void setUp() {
        TenantContextHolder.set(1L);
    }

    @AfterAll
    static void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    void testInsertHasVersionZero() {
        User user = new User("乐观锁测试", "lock@test.com", "13900000000");
        user.setTenantId(1L);
        user.setVersion(0); // 显式设 0，确保 DB 的值不为 null
        mapper.insert(user);
        // Spring Boot auto-commit: no manual commit needed
        assertNotNull(user.getId());
        assertEquals(0, user.getVersion(), "插入后 version 应为 0");
        testUserId = user.getId();
        System.out.println("[乐观锁] insert: " + user);
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void testUpdateIncrementsVersion() {
        User user = mapper.selectById(testUserId);
        assertNotNull(user, "应能查到刚插入的用户");
        assertEquals(0, user.getVersion());

        user.setPhone("11111111111");
        int rows = mapper.updateById(user);
        assertEquals(1, rows);
        // Spring Boot auto-commit: no manual commit needed

        // 重新查询，验证 version 已 +1
        User reloaded = mapper.selectById(testUserId);
        assertEquals(1, reloaded.getVersion(), "更新后 version 应为 1");
        System.out.printf("[乐观锁] updateById: version %d→%d, phone=%s%n",
                user.getVersion(), reloaded.getVersion(), reloaded.getPhone());
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void testConcurrentUpdateFails() {
        // 模拟并发：两个独立请求加载同一记录
        User userA = mapper.selectById(testUserId);  // version=1
        // 清一级缓存，模拟第二个连接
        sqlSessionTemplate.clearCache();
        User userB = mapper.selectById(testUserId);  // version=1（独立对象）
        assertNotSame(userA, userB, "两个 select 应返回不同对象");
        assertEquals(userA.getVersion(), userB.getVersion());

        // A 先更新 → 成功，version → 2
        userA.setPhone("AAAAAAAAAAA");
        int rowsA = mapper.updateById(userA);
        assertEquals(1, rowsA);
        // Spring Boot auto-commit: no manual commit needed

        // B 拿着 version=1 的旧对象更新 → WHERE version=1 不匹配 → 失败
        userB.setPhone("BBBBBBBBBBB");
        int rowsB = mapper.updateById(userB);
        assertEquals(0, rowsB, "乐观锁应阻止 B 的更新（version 已变）");

        // 确认最终是 A 的值
        User finalUser = mapper.selectById(testUserId);
        assertEquals(2, finalUser.getVersion());
        assertEquals("AAAAAAAAAAA", finalUser.getPhone());
        System.out.printf("[乐观锁] 并发冲突: A 成功, B 被拒绝 (rows=%d), 最终 phone=%s%n",
                rowsB, finalUser.getPhone());
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void testUpdateWithWrapperAlsoInjectsVersion() {
        User user = mapper.selectById(testUserId); // version=2
        assertNotNull(user);
        user.setPhone("WRAPPER-PHONE");

        int rows = mapper.update(user,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<User>()
                        .eq("id", testUserId));
        assertEquals(1, rows);
        // Spring Boot auto-commit: no manual commit needed

        User reloaded = mapper.selectById(testUserId);
        assertEquals(3, reloaded.getVersion());
        System.out.println("[乐观锁] update(et, wrapper) 同样注入 version 条件 OK");
    }
}
