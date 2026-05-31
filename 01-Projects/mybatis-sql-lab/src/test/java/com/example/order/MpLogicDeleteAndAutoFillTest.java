package com.example.order;

import com.example.order.mp.MpUserMapper;
import com.example.order.model.User;
import com.example.order.util.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 @TableLogic（逻辑删除）和 @TableField(fill)（自动填充）。
 */
@SpringBootTest
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MpLogicDeleteAndAutoFillTest {

    @Autowired
    private MpUserMapper mapper;
    @Autowired
    private com.example.order.mapper.UserMapper nativeMapper;

    @BeforeAll
    static void setUp() {
        TenantContextHolder.set(1L);
    }

    @AfterAll
    static void tearDown() {
        TenantContextHolder.clear();
    }

    // ===== @TableLogic：逻辑删除 =====

    @Test
    @org.junit.jupiter.api.Order(1)
    void testLogicDeleteUpdatesInsteadOfDelete() {
        User user = new User("逻辑删除测试", "logic@test.com", "13000000000");
        user.setTenantId(1L);
        user.setVersion(0);
        mapper.insert(user);
        Long id = user.getId();

        // deleteById → 应当生成 UPDATE SET deleted=1 WHERE id=? AND deleted=0
        int rows = mapper.deleteById(id);
        assertEquals(1, rows, "逻辑删除应是 UPDATE 返回 1 行");
        System.out.println("[@TableLogic] deleteById 返回 rows=" + rows + " (UPDATE)");

        // 再次查询 → 应查不到（delete 条件自动过滤）
        User reloaded = mapper.selectById(id);
        assertNull(reloaded, "逻辑删除后 selectById 应返回 null（WHERE deleted=0 过滤）");
        System.out.println("[@TableLogic] selectById(" + id + ") = null (deleted=1 被过滤)");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void testLogicDeleteDoesNotDeleteInDb() {
        User user = new User("物理验证", "physical@test.com", "13000000000");
        user.setTenantId(1L);
        user.setVersion(0);
        mapper.insert(user);
        Long id = user.getId();

        mapper.deleteById(id);  // UPDATE SET deleted=1

        // 用原生 Mapper 查（XML 不走 @TableLogic 过滤，SQL 中无 deleted 条件）
        User physical = nativeMapper.selectById(id);
        assertNotNull(physical, "原生 Mapper 查不到 -> 行仍物理存在");
        System.out.println("[@TableLogic] 原生 selectById(" + id + ") 物理行还在: " + physical);
    }

    // ===== @TableField(fill)：自动填充 =====

    @Test
    @org.junit.jupiter.api.Order(3)
    void testAutoFillOnInsert() {
        User user = new User("自动填充测试", "fill@test.com", "13000000000");
        user.setTenantId(1L);
        user.setVersion(0);
        mapper.insert(user);

        assertNotNull(user.getCreateTime(), "INSERT 时 createTime 应被自动填充");
        assertNotNull(user.getUpdateTime(), "INSERT 时 updateTime 应被自动填充");
        System.out.println("[@TableField fill] INSERT: createTime=" + user.getCreateTime()
                + ", updateTime=" + user.getUpdateTime());
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void testAutoFillOnUpdate() {
        User user = new User("更新填充测试", "update-fill@test.com", "13000000000");
        user.setTenantId(1L);
        user.setVersion(0);
        mapper.insert(user);
        LocalDateTime insertTime = user.getUpdateTime();

        // 等一下确保时间不同
        try { Thread.sleep(10); } catch (InterruptedException e) {}

        user.setPhone("99999999999");
        mapper.updateById(user);

        User reloaded = mapper.selectById(user.getId());
        assertNotNull(reloaded.getUpdateTime());
        assertTrue(reloaded.getUpdateTime().isAfter(insertTime),
                "UPDATE 时 updateTime 应被自动填充为更新时间");
        System.out.println("[@TableField fill] UPDATE: updateTime " + insertTime
                + " → " + reloaded.getUpdateTime());
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    void testAutoFillNotOverrideManualValue() {
        // deleteById 不会触发 updateFill（delete 不走 update 路径）
        User user = new User("手动填充测试", "manual-fill@test.com", "13000000000");
        user.setTenantId(1L);
        user.setVersion(0);
        mapper.insert(user);

        assertNotNull(user.getCreateTime());
        System.out.println("[@TableField fill] createTime 未手动设值 → 自动填充生效");
    }
}
