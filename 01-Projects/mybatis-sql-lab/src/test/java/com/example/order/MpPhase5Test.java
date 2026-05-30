package com.example.order;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.order.mapper.UserMapper;
import com.example.order.mp.MpUserMapper;
import com.example.order.model.User;
import com.example.order.util.SqlSessionUtil;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 5: MP 原理验证。
 * 每个测试有明确目的——验证 MP 的某个内部机制。
 * 配合 logback DEBUG 日志观察实际执行的 SQL。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MpPhase5Test {

    private static SqlSession session;
    private static MpUserMapper mpMapper;
    private static UserMapper nativeMapper;

    @BeforeAll
    static void setUp() {
        session = SqlSessionUtil.getFactory().openSession();
        mpMapper = session.getMapper(MpUserMapper.class);
        nativeMapper = session.getMapper(UserMapper.class);
    }

    @AfterAll
    static void tearDown() {
        if (session != null) session.close();
    }

    // ===== 5.1 SqlInjector: 验证 SQL 自动生成 =====

    @Test
    @org.junit.jupiter.api.Order(1)
    void testInjectorSelectById() {
        // 我们没有为 selectById 写任何 XML 或注解 SQL
        // 如果调用成功 → SQL 是 MP 的 AutoSqlInjector 在启动时注入的
        User user = mpMapper.selectById(1L);
        assertNotNull(user);
        // 观察日志，SQL 应该是:
        // SELECT id,username,email,phone,created_at FROM users WHERE id=?
        System.out.println("[5.1] SqlInjector 注入了 selectById，无 XML");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void testInjectorInsert() {
        User user = new User("注入测试", "inject@test.com", "13000000000");
        int rows = mpMapper.insert(user);
        assertEquals(1, rows);
        assertNotNull(user.getId()); // 自增回填也在
        System.out.println("[5.1] SqlInjector 注入了 insert(id=" + user.getId() + ")，无 XML");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void testInjectorCount() {
        // BaseMapper 提供了 selectCount 方法，同样是无 XML 自动注入
        Long count = mpMapper.selectCount(null);
        assertTrue(count >= 4);
        System.out.println("[5.1] SqlInjector 注入了 selectCount=" + count);
    }

    // ===== 5.2 Wrapper SQL 生成 =====

    @Test
    @org.junit.jupiter.api.Order(4)
    void testWrapperGeneratesLike() {
        // eq("username","张三") 生成 WHERE username = ?
        // 观察日志确认 MP 生成的 WHERE 子句
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.like("email", "example");
        List<User> list = mpMapper.selectList(qw);
        assertFalse(list.isEmpty());
        // 日志应显示: WHERE email LIKE '%example%'
        System.out.println("[5.2] Wrapper 生成 LIKE: " + list.size() + " 条");
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    void testWrapperGeneratesNestedCondition() {
        // 嵌套条件: (username=? OR username=?) AND email LIKE ?
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.nested(w -> w.eq("username", "张三").or().eq("username", "李四"))
          .like("email", "example");

        List<User> list = mpMapper.selectList(qw);
        assertFalse(list.isEmpty());
        // 日志应显示: WHERE (username=? OR username=?) AND email LIKE ?
        System.out.println("[5.2] Wrapper 嵌套条件: " + list.size() + " 条");
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    void testWrapperNullConditionProducesNoWhere() {
        // 所有条件为 null 时，Wrapper 不生成 WHERE
        QueryWrapper<User> qw = new QueryWrapper<>();
        // 什么条件都不加
        List<User> list = mpMapper.selectList(qw);
        // 等价于 selectList(null)
        List<User> all = mpMapper.selectList(null);
        assertEquals(all.size(), list.size());
        System.out.println("[5.2] 空 Wrapper = 无 WHERE: " + list.size() + " 条");
    }

    // ===== 5.3 分页拦截器 =====

    @Test
    @org.junit.jupiter.api.Order(7)
    void testPaginationRewritesSql() {
        // 观察日志，MP 会发两条 SQL:
        // 1. SELECT COUNT(*) FROM users (查总数)
        // 2. SELECT ... FROM users LIMIT ? (查当前页)
        Page<User> page = new Page<>(1, 2);
        IPage<User> result = mpMapper.selectPage(page, null);

        assertEquals(2, result.getRecords().size());
        assertTrue(result.getTotal() >= 4);
        // 验证分页元数据正确
        assertTrue(result.getPages() >= 2);  // 至少 2 页
        assertEquals(2, result.getSize());    // 每页 2 条
        System.out.printf("[5.3] 分页拦截: SQL 被改写, total=%d, pages=%d%n",
                result.getTotal(), result.getPages());
    }

    // ===== 5.4 MP 与原生共存验证 =====

    @Test
    @org.junit.jupiter.api.Order(8)
    void testMpDoesNotBreakNative() {
        // 原生 UserMapper 依然通过 XML 工作
        List<User> byNative = nativeMapper.selectByUsername("张三");
        assertEquals(1, byNative.size());

        // MP 的 MpUserMapper 通过 SqlInjector 工作
        List<User> byMp = mpMapper.selectList(
                new QueryWrapper<User>().eq("username", "张三"));
        assertEquals(1, byMp.size());

        // 两者结果完全一致
        assertEquals(byNative.get(0).getEmail(), byMp.get(0).getEmail());
        System.out.println("[5.4] 原生 XML 和 MP SqlInjector 共存，结果一致");
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    void testMpWrapperOnNativeTable() {
        // MP 也可以操作 products 表（如果注册了对应的 Mapper）
        // 这里验证: 用原生 ProductMapper 的 selectAll 检查 products 数据
        com.example.order.mapper.ProductMapper productMapper =
                session.getMapper(com.example.order.mapper.ProductMapper.class);
        assertFalse(productMapper.selectAll().isEmpty());
        System.out.println("[5.4] 原生 Mapper 不受 MP 影响: products 正常查询");
    }
}
