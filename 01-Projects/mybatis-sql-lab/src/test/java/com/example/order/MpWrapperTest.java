package com.example.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.order.mp.MpUserMapper;
import com.example.order.model.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MpWrapperTest {

    @Autowired
    private MpUserMapper mapper;

    // ===== QueryWrapper 基础 =====

    @Test
    @org.junit.jupiter.api.Order(1)
    void testQueryWrapperEq() {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("username", "张三");

        List<User> users = mapper.selectList(qw);
        assertEquals(1, users.size());
        assertEquals("张三", users.get(0).getUsername());
        System.out.println("[QW] eq: " + users.get(0));
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void testQueryWrapperCombined() {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.like("email", "zhang")
          .or()
          .eq("username", "李四");

        List<User> users = mapper.selectList(qw);
        assertTrue(users.size() >= 2);
        users.forEach(u -> System.out.println("[QW] 组合命中: " + u.getUsername()));
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void testQueryWrapperBetween() {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.between("id", 1, 3)
          .orderByDesc("id");

        List<User> users = mapper.selectList(qw);
        assertEquals(3, users.size());
        assertTrue(users.get(0).getId() > users.get(1).getId());
        System.out.println("[QW] between + orderByDesc: " + users.size() + " 条");
    }

    // ===== LambdaQueryWrapper: 类型安全 =====

    @Test
    @org.junit.jupiter.api.Order(4)
    void testLambdaQueryWrapper() {
        LambdaQueryWrapper<User> lqw = new LambdaQueryWrapper<>();
        lqw.eq(User::getUsername, "王五")
           .gt(User::getId, 0L);

        List<User> users = mapper.selectList(lqw);
        assertEquals(1, users.size());
        assertEquals("王五", users.get(0).getUsername());
        System.out.println("[LQW] 类型安全: " + users.get(0));
    }

    // ===== Wrapper SQL 生成验证 =====

    @Test
    @org.junit.jupiter.api.Order(5)
    void testWrapperGeneratesLike() {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.like("email", "example");
        List<User> list = mapper.selectList(qw);
        assertFalse(list.isEmpty());
        System.out.println("[SQL生成] LIKE: " + list.size() + " 条");
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    void testWrapperGeneratesNestedCondition() {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.nested(w -> w.eq("username", "张三").or().eq("username", "李四"))
          .like("email", "example");

        List<User> list = mapper.selectList(qw);
        assertFalse(list.isEmpty());
        System.out.println("[SQL生成] 嵌套条件: " + list.size() + " 条");
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    void testWrapperNullConditionProducesNoWhere() {
        QueryWrapper<User> qw = new QueryWrapper<>();
        List<User> list = mapper.selectList(qw);
        List<User> all = mapper.selectList(null);
        assertEquals(all.size(), list.size());
        System.out.println("[SQL生成] 空 Wrapper = 无 WHERE: " + list.size() + " 条");
    }

    // ===== 比较 / 模糊 / IN / 分组 / 排序 =====

    @Test @org.junit.jupiter.api.Order(8)
    void testComparisonOps() {
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .ge("id", 1).lt("id", 10).ne("username", "已删除"));
        assertFalse(list.isEmpty());
        System.out.println("[比较] >=, <, != : " + list.size() + " 条");
    }

    @Test @org.junit.jupiter.api.Order(9)
    void testStringOps() {
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .like("email", "test")
                .likeLeft("username", "MP")
                .likeRight("username", "测试"));
        System.out.println("[模糊] like/likeLeft/likeRight: " + list.size() + " 条");
    }

    @Test @org.junit.jupiter.api.Order(10)
    void testInOps() {
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .in("id", 1, 2, 3));
        assertEquals(3, list.size());
        System.out.println("[IN] 查询: " + list.size() + " 条");
    }

    @Test @org.junit.jupiter.api.Order(11)
    void testGroupByAndHaving() {
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .select("username, COUNT(*) AS cnt")
                .groupBy("username")
                .having("COUNT(*) > 0")
                .orderByDesc("cnt"));
        assertFalse(list.isEmpty());
        System.out.println("[GROUP BY] + HAVING: " + list.size() + " 组");
    }

    @Test @org.junit.jupiter.api.Order(12)
    void testOrderBy() {
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .orderByAsc("username")
                .orderByDesc("id"));
        assertFalse(list.isEmpty());
        System.out.println("[排序] 多列: " + list.size() + " 条");
    }

    // ===== 嵌套条件 =====

    @Test @org.junit.jupiter.api.Order(13)
    void testNestedAnd() {
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .and(w -> w.eq("username", "张三").or().eq("username", "李四"))
                .like("email", "example"));
        assertEquals(2, list.size());
        System.out.println("[嵌套] AND: " + list.size() + " 条");
    }

    @Test @org.junit.jupiter.api.Order(14)
    void testNestedOr() {
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .nested(w -> w.between("id", 1, 2))
                .or()
                .nested(w -> w.in("id", 3, 4, 5)));
        assertFalse(list.isEmpty());
        System.out.println("[嵌套] OR: " + list.size() + " 条");
    }

    @Test @org.junit.jupiter.api.Order(15)
    void testConditionalEq() {
        String username = null;
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .eq(username != null, "username", username)
                .like("email", "example"));
        assertFalse(list.isEmpty());
        System.out.println("[条件式] eq(username=null→跳过): " + list.size() + " 条");
    }

    // ===== 子查询 =====

    @Test @org.junit.jupiter.api.Order(16)
    void testInSql() {
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .inSql("id", "SELECT user_id FROM orders WHERE status='PAID'"));
        assertFalse(list.isEmpty());
        System.out.println("[子查询] IN (SELECT...): " + list.size() + " 条");
    }

    @Test @org.junit.jupiter.api.Order(17)
    void testExists() {
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .exists("SELECT 1 FROM orders WHERE orders.user_id = users.id"));
        assertFalse(list.isEmpty());
        System.out.println("[子查询] EXISTS: " + list.size() + " 条");
    }

    @Test @org.junit.jupiter.api.Order(18)
    void testApply() {
        List<User> list = mapper.selectList(new QueryWrapper<User>()
                .apply("id IN (SELECT user_id FROM orders WHERE total_amount > {0})", 100));
        assertFalse(list.isEmpty());
        System.out.println("[子查询] apply: " + list.size() + " 条");
    }

    // ===== UpdateWrapper: 替代 <set> =====

    @Test @org.junit.jupiter.api.Order(19)
    void testUpdateWrapper() {
        UpdateWrapper<User> uw = new UpdateWrapper<>();
        uw.set("phone", "00000000000")
          .eq("username", "张三");

        int rows = mapper.update(uw);
        assertEquals(1, rows);
        User u = mapper.selectById(1L);
        assertEquals("00000000000", u.getPhone());
        System.out.println("[UpdateWrapper] 替代 <set>: phone 已更新");
    }

    // ===== @Select 注解 JOIN =====

    @Test @org.junit.jupiter.api.Order(20)
    void testAtSelectAnnotation() {
        List<com.example.order.model.Order> orders = mapper.selectOrdersByUserId(1L);
        assertFalse(orders.isEmpty());
        orders.forEach(o -> assertNotNull(o.getOrderNo()));
        System.out.println("[@Select] JOIN: 用户1有 " + orders.size() + " 个订单（平铺，无嵌套 User）");
    }

    // ===== QueryWrapper 不能做的 =====

    @Test @org.junit.jupiter.api.Order(21)
    void testWrapperCannotDo() {
        System.out.println("=== QueryWrapper 不能做的事 ===");
        System.out.println("X <resultMap>/<association>/<collection> — 嵌套对象映射 → 回退到 XML");
        System.out.println("X <sql>/<include>                      — 列名复用 → 用 LambdaQueryWrapper.select() 替代");
        System.out.println("X <choose>/<when>/<otherwise>          — 互斥分支 → 用 Java if-else 替代");
        System.out.println("X <foreach> 批量 INSERT               — 用 BaseMapper.insertBatch()");
        System.out.println("X <trim>                              — 用 nested() 组合替代");
        System.out.println("X <bind>                              — 用 Java 变量替代");
        assertTrue(true);
    }
}
