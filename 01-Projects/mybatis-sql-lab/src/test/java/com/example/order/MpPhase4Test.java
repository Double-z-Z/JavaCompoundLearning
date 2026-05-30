package com.example.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.order.mp.MpUserMapper;
import com.example.order.model.User;
import com.example.order.util.SqlSessionUtil;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MpPhase4Test {

    private static SqlSession session;
    private static MpUserMapper mpMapper;    // MP 版本
    private static com.example.order.mapper.UserMapper nativeMapper; // 原生版本

    @BeforeAll
    static void setUp() {
        session = SqlSessionUtil.getFactory().openSession();
        mpMapper = session.getMapper(MpUserMapper.class);
        nativeMapper = session.getMapper(com.example.order.mapper.UserMapper.class);
    }

    @AfterAll
    static void tearDown() {
        if (session != null) session.close();
    }

    // ===== 4.1 BaseMapper: 单表 CRUD 零代码 =====

    @Test
    @org.junit.jupiter.api.Order(1)
    void testBaseMapperInsert() {
        // 原生写法: UserMapper.xml 里手写 <insert> + mapper.insert(user)
        // MP 写法：直接调用继承的 insert
        User user = new User("MP测试", "mp@test.com", "13900000000");
        int rows = mpMapper.insert(user);
        assertEquals(1, rows);
        assertNotNull(user.getId(), "MP 自动回填了自增 ID");
        System.out.println("[4.1] BaseMapper.insert: " + user);
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void testBaseMapperSelectById() {
        // 原生: <select id="selectById" resultType="User"> SELECT ... WHERE id=#{id}
        // MP: 一行继承
        User user = mpMapper.selectById(1L);
        assertNotNull(user);
        assertEquals("张三", user.getUsername());
        System.out.println("[4.1] BaseMapper.selectById: " + user);
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void testBaseMapperSelectList() {
        // 原生: <select id="selectAll"> + mapper.selectAll()
        // MP: selectList(null) = 无条件查全部
        List<User> all = mpMapper.selectList(null);
        assertTrue(all.size() >= 4);
        System.out.println("[4.1] BaseMapper.selectList(null): " + all.size() + " 条");
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void testBaseMapperUpdateById() {
        // 原生: <update> + mapper.update(user)
        // MP: updateById
        User user = mpMapper.selectById(1L);
        user.setPhone("13888888888");
        int rows = mpMapper.updateById(user);
        assertEquals(1, rows);
        assertEquals("13888888888", mpMapper.selectById(1L).getPhone());
        System.out.println("[4.1] BaseMapper.updateById OK");
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    void testBaseMapperDeleteById() {
        // 原生: <delete> + mapper.deleteById(id)
        // MP: deleteById
        User tmp = new User("待删除", "del@test.com", "13600000000");
        mpMapper.insert(tmp);
        int rows = mpMapper.deleteById(tmp.getId());
        assertEquals(1, rows);
        assertNull(mpMapper.selectById(tmp.getId()));
        System.out.println("[4.1] BaseMapper.deleteById OK");
    }

    // ===== 4.2 条件构造器: QueryWrapper =====

    @Test
    @org.junit.jupiter.api.Order(6)
    void testQueryWrapperEq() {
        // 原生: <where><if test="username!=null">AND username=#{username}</if></where>
        // MP: QueryWrapper
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("username", "张三");  // WHERE username = ?

        List<User> users = mpMapper.selectList(qw);
        assertEquals(1, users.size());
        assertEquals("张三", users.get(0).getUsername());
        System.out.println("[4.2] QueryWrapper.eq: " + users.get(0));
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    void testQueryWrapperCombined() {
        // 原生: <where> 里多个 <if>
        // MP: 链式调用
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.like("email", "zhang")      // email LIKE '%zhang%'
          .or()                         // OR
          .eq("username", "李四");      // username = '李四'

        List<User> users = mpMapper.selectList(qw);
        assertTrue(users.size() >= 2);
        users.forEach(u -> System.out.println("[4.2] 命中: " + u.getUsername()));
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    void testQueryWrapperBetween() {
        // 原生: <if test="minId!=null">AND id>=#{minId}</if> <if test="maxId!=null">AND id<=#{maxId}</if>
        // MP: between
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.between("id", 1, 3)
          .orderByDesc("id");

        List<User> users = mpMapper.selectList(qw);
        assertEquals(3, users.size());
        assertTrue(users.get(0).getId() > users.get(1).getId()); // DESC
        System.out.println("[4.2] between + orderByDesc: " + users.size() + " 条");
    }

    // ===== 4.2 LambdaQueryWrapper: 类型安全的列引用 =====

    @Test
    @org.junit.jupiter.api.Order(9)
    void testLambdaQueryWrapper() {
        // QueryWrapper 的问题: eq("username", ...) 中的列名是字符串，重构时不会自动改
        // LambdaQueryWrapper: 用方法引用，IDE 自动重构
        LambdaQueryWrapper<User> lqw = new LambdaQueryWrapper<>();
        lqw.eq(User::getUsername, "王五")     // 不是字符串"username"，是 User::getUsername
           .gt(User::getId, 0L);             // id > 0

        List<User> users = mpMapper.selectList(lqw);
        assertEquals(1, users.size());
        assertEquals("王五", users.get(0).getUsername());
        System.out.println("[4.2] LambdaQueryWrapper: " + users.get(0));
    }

    // ===== 4.4 分页插件 =====

    @Test
    @org.junit.jupiter.api.Order(10)
    void testPagination() {
        // 原生: <select id="selectPage"> SELECT ... LIMIT #{limit} OFFSET #{offset} + count
        // MP: Page + selectPage
        Page<User> page = new Page<>(1, 2);  // 第1页，每页2条
        IPage<User> result = mpMapper.selectPage(page, null);

        assertEquals(2, result.getRecords().size());
        assertTrue(result.getTotal() >= 4);  // 总记录数

        System.out.printf("[4.4] 分页: page=%d/%d, size=%d, total=%d%n",
                result.getCurrent(), result.getPages(), result.getRecords().size(), result.getTotal());
        result.getRecords().forEach(u -> System.out.println("  → " + u.getUsername()));
    }

    // ===== 对比: 同一操作，MP vs 原生 =====

    @Test
    @org.junit.jupiter.api.Order(11)
    void testCompareNativeVsMp() {
        // 同一条查询：按 username 查
        String targetUsername = "张三";

        // 原生方式（需要 XML + Mapper 方法）
        List<User> nativeResult = nativeMapper.selectByUsername(targetUsername);

        // MP 方式（零 XML）
        List<User> mpResult = mpMapper.selectList(
                new QueryWrapper<User>().eq("username", targetUsername));

        assertEquals(nativeResult.size(), mpResult.size());
        assertEquals(nativeResult.get(0).getEmail(), mpResult.get(0).getEmail());
        System.out.println("[对比] 原生 vs MP: 结果一致, email=" + nativeResult.get(0).getEmail());
    }
}
