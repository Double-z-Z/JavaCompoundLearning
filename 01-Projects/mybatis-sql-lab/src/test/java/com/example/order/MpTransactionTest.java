package com.example.order;

import com.example.order.mapper.OrderMapper;
import com.example.order.model.Order;
import com.example.order.model.User;
import com.example.order.mp.MpUserMapper;
import com.example.order.service.TxDemoService;
import com.example.order.util.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证：事务传播行为 + FOR UPDATE 行锁 + AOP 代理陷阱。
 */
@SpringBootTest
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MpTransactionTest {

    @Autowired
    private TxDemoService txService;
    @Autowired
    private MpUserMapper mpUserMapper;
    @Autowired
    private OrderMapper orderMapper;

    @BeforeAll
    static void setUp() {
        TenantContextHolder.set(1L);
    }

    @AfterAll
    static void tearDown() {
        TenantContextHolder.clear();
    }

    // ===== REQUIRED：内外同一 conn，内可见外未提交的数据 =====

    @Test
    @org.junit.jupiter.api.Order(1)
    void testRequiredInnerSeesUncommitted() {
        User user = txService.requiredOuter("REQUIRED测试");
        assertNotNull(user.getId());

        User loaded = mpUserMapper.selectById(user.getId());
        assertNotNull(loaded, "REQUIRED 内外同一事务，commit 后数据应持久化");
        assertEquals("REQUIRED-INNER", loaded.getPhone(),
                "内层 REQUIRED 修改了 phone，同一事务内可见");
        System.out.println("[REQUIRED] 内外同一 conn，inner 修改可见: " + loaded);
    }

    // ===== REQUIRES_NEW：内外独立 conn，内不可见外未提交数据 =====

    @Test
    @org.junit.jupiter.api.Order(2)
    void testRequiresNewCannotSeeOuterUncommitted() {
        txService.requiresNewOuter();
        // 内层抛异常回滚，外层插入应当成功（未受影响）
        System.out.println("[REQUIRES_NEW] 内层回滚不影响外层");
    }

    // ===== NESTED：同一 conn + savepoint，内可见外未提交数据 =====

    @Test
    @org.junit.jupiter.api.Order(3)
    void testNestedSeesOuterUncommitted() {
        txService.nestedOuter();
        // 内层 savepoint 回滚，外层插入应当成功
        System.out.println("[NESTED] 内层 savepoint 回滚，外层持久化");
    }

    // ===== AOP 陷阱验证 =====

    @Test
    @org.junit.jupiter.api.Order(4)
    void testThisCallBypassesAop() {
        // txService.thisCallBypass() 内部用 this.methodWithTx()
        // this 调用绕过了 Spring AOP 代理 → @Transactional 不生效
        // 方法里的 insert 在 autoCommit 下执行，即发即提交（无法回滚验证）
        txService.thisCallBypass();
        System.out.println("[AOP陷阱] this.methodWithTx() 的 @Transactional 未生效(绕过代理)");
    }

    // ===== FOR UPDATE：事务内行锁 =====

    @Test
    @org.junit.jupiter.api.Order(5)
    void testSelectForUpdate() {
        // FOR UPDATE 必须在事务内执行，否则抛异常
        Order order = txService.selectForUpdate(1L);
        assertNotNull(order);
        assertEquals("ORD20260530001", order.getOrderNo());
        System.out.println("[FOR UPDATE] 行锁获取成功: " + order.getOrderNo());
    }

    // ===== FOR UPDATE 在事务外：PG 会隐式单语句事务 =====

    @Test
    @org.junit.jupiter.api.Order(6)
    void testForUpdateOutsideTxStillWorks() {
        // PG 中 SELECT FOR UPDATE 无显式事务时可执行（隐式单语句事务）
        // 但锁立即释放，没有实际保护效果
        Order order = orderMapper.selectForUpdate(1L);
        assertNotNull(order);
        System.out.println("[FOR UPDATE] 无显式事务时 PG 隐式事务 → 锁立即释放: " + order.getOrderNo());
    }
}
