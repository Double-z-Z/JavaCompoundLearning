package com.example.order;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.order.mp.MpUserMapper;
import com.example.order.model.User;
import com.example.order.util.SqlSessionUtil;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryWrapper 能力边界验证。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MpPhase6Test {

    private static SqlSession session;
    private static MpUserMapper mapper;

    @BeforeAll
    static void setUp() {
        session = SqlSessionUtil.getFactory().openSession();
        mapper = session.getMapper(MpUserMapper.class);
    }

    @AfterAll
    static void tearDown() {
        if (session != null) session.close();
    }

    // ===== QueryWrapper 能做的（WHERE 子句全覆盖） =====

    @Test @org.junit.jupiter.api.Order(1)
    void testComparisonOps() {
        // eq / ne / gt / ge / lt / le
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .ge("id", 1)      // >=
                .lt("id", 10)     // <
                .ne("username", "已删除")  // !=
        );
        assertFalse(list.isEmpty());
        System.out.println("比较运算: " + list.size() + " 条");
    }

    @Test @org.junit.jupiter.api.Order(2)
    void testStringOps() {
        // like / notLike / likeLeft / likeRight
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .like("email", "test")         // '%test%'
                .likeLeft("username", "MP")    // '%MP'
                .likeRight("username", "测试")  // '测试%'
        );
        System.out.println("模糊查询: " + list.size() + " 条");
    }

    @Test @org.junit.jupiter.api.Order(3)
    void testInOps() {
        // in / notIn
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .in("id", 1, 2, 3)   // IN (1,2,3)
        );
        assertEquals(3, list.size());
        System.out.println("IN 查询: " + list.size() + " 条");
    }

    @Test @org.junit.jupiter.api.Order(4)
    void testGroupByAndHaving() {
        // GROUP BY + HAVING → 对应 <if> + 聚合
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .select("username, COUNT(*) AS cnt")  // 投影列
                .groupBy("username")
                .having("COUNT(*) > 0")
                .orderByDesc("cnt")
        );
        assertFalse(list.isEmpty());
        System.out.println("GROUP BY + HAVING: " + list.size() + " 组");
    }

    @Test @org.junit.jupiter.api.Order(5)
    void testOrderBy() {
        // 多列排序
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .orderByAsc("username")
                .orderByDesc("id")
        );
        assertFalse(list.isEmpty());
        System.out.println("多列排序: " + list.size() + " 条");
    }

    // ===== 嵌套条件 =====

    @Test @org.junit.jupiter.api.Order(6)
    void testNestedAnd() {
        // (username='张三' OR username='李四') AND email LIKE '%example%'
        // 对应 XML:
        // <where>
        //   <if test="...">(username=#{a} OR username=#{b}) AND email LIKE #{c}</if>
        // </where>
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .and(w -> w.eq("username", "张三").or().eq("username", "李四"))
                .like("email", "example")
        );
        assertEquals(2, list.size());
        System.out.println("嵌套 AND: " + list.size() + " 条");
    }

    @Test @org.junit.jupiter.api.Order(7)
    void testNestedOr() {
        // (id BETWEEN 1 AND 2) OR (id IN (3, 4, 5))
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .nested(w -> w.between("id", 1, 2))
                .or()
                .nested(w -> w.in("id", 3, 4, 5))
        );
        assertFalse(list.isEmpty());
        System.out.println("嵌套 OR: " + list.size() + " 条");
    }

    @Test @org.junit.jupiter.api.Order(8)
    void testConditionalEq() {
        // eq(condition, column, value): 第一个参数为 true 才加条件
        // 对应 XML 的 <if test="condition">
        String username = null;  // 比如从请求参数来的
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .eq(username != null, "username", username)  // false → 跳过
                .like("email", "example")                     // 始终执行
        );
        assertFalse(list.isEmpty());
        // 没有按 username 过滤，因为 condition=false
        System.out.println("条件式 eq(username=null→跳过): " + list.size() + " 条");
    }

    // ===== 子查询 =====

    @Test @org.junit.jupiter.api.Order(9)
    void testInSql() {
        // WHERE id IN (SELECT user_id FROM orders WHERE status='PAID')
        // inSql 的第二个参数是原始 SQL 片段（不做占位符转换）
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .inSql("id", "SELECT user_id FROM orders WHERE status='PAID'")
        );
        assertFalse(list.isEmpty());
        System.out.println("子查询 IN (SELECT...): " + list.size() + " 条");
    }

    @Test @org.junit.jupiter.api.Order(10)
    void testExists() {
        // WHERE EXISTS (SELECT 1 FROM orders WHERE orders.user_id = users.id)
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .exists("SELECT 1 FROM orders WHERE orders.user_id = users.id")
        );
        assertFalse(list.isEmpty());
        System.out.println("EXISTS 子查询: " + list.size() + " 条");
    }

    @Test @org.junit.jupiter.api.Order(11)
    void testApply() {
        // apply: 直接拼接任意 SQL 片段（最后一个参数是占位符值）
        // WHERE id IN (SELECT user_id FROM orders WHERE total_amount > ?)
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .apply("id IN (SELECT user_id FROM orders WHERE total_amount > {0})", 100)
        );
        assertFalse(list.isEmpty());
        System.out.println("apply 自定义子查询: " + list.size() + " 条");
    }

    // ===== UpdateWrapper: 替代 <set> =====

    @Test @org.junit.jupiter.api.Order(9)
    void testUpdateWrapper() {
        // 对应 XML 的 <set> + <if>
        // <update id="update">
        //   UPDATE users
        //   <set>
        //     <if test="email!=null">email=#{email},</if>
        //   </set>
        //   WHERE username=#{username}
        // </update>
        UpdateWrapper<User> uw = new UpdateWrapper<>();
        uw.set("phone", "00000000000")
          .eq("username", "张三");

        int rows = mapper.update(uw);  // update(entity=null, wrapper=uw)
        assertEquals(1, rows);
        User u = mapper.selectById(1L);
        assertEquals("00000000000", u.getPhone());
        System.out.println("UpdateWrapper 替代 <set>: phone 已更新");
    }

    // ===== 关联查询：MP + 手写 =====

    @Test @org.junit.jupiter.api.Order(14)
    void testAtSelectAnnotation() {
        // @Select 注解做简单 JOIN —— 不需要 XML，但只能平铺映射
        List<com.example.order.model.Order> orders = mapper.selectOrdersByUserId(1L);
        assertFalse(orders.isEmpty());
        orders.forEach(o -> assertNotNull(o.getOrderNo()));
        System.out.println("@Select JOIN 查询: 用户1有 " + orders.size() + " 个订单（平铺，无嵌套 User 对象）");
    }

    // ===== QueryWrapper 不能做的 =====

    @Test @org.junit.jupiter.api.Order(15)
    void testWrapperCannotDo() {
        System.out.println("=== QueryWrapper 不能做的事 ===");
        System.out.println("❌ <resultMap>/<association>/<collection> — 嵌套对象映射 → 必须回退到 XML");
        System.out.println("❌ <sql>/<include>                      — 列名复用 → 用 LambdaQueryWrapper.select() 替代");
        System.out.println("❌ <choose>/<when>/<otherwise>          — 互斥分支 → 用 Java if-else 替代");
        System.out.println("❌ <foreach> 批量 INSERT               — 用 BaseMapper.insertBatch()");
        System.out.println("❌ <trim>                              — 用 nested() 组合替代");
        System.out.println("❌ <bind>                              — 用 Java 变量替代");
        assertTrue(true);
    }
}
