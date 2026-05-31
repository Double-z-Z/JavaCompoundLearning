package com.example.order.service;

import com.example.order.mapper.OrderMapper;
import com.example.order.mapper.UserMapper;
import com.example.order.model.Order;
import com.example.order.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 演示事务传播行为 + AOP 代理陷阱。
 */
@Service
public class TxDemoService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Lazy
    @Autowired
    private TxDemoService self;  // 注入自己的代理（@Lazy 避免循环依赖）

    // ===== REQUIRED（默认）：加入外层事务 =====

    @Transactional
    public User requiredOuter(String username) {
        User user = new User(username, username + "@test.com", "13000000000");
        user.setTenantId(1L);
        user.setVersion(0);
        userMapper.insert(user);         // connA
        self.requiredInner(user.getId()); // 同一 connA
        return user;
    }

    @Transactional  // REQUIRED
    public void requiredInner(Long userId) {
        User u = userMapper.selectById(userId);  // connA，可见未提交的 insert
        u.setPhone("REQUIRED-INNER");
        userMapper.update(u);
    }

    // ===== REQUIRES_NEW：独立事务 =====

    @Transactional
    public void requiresNewOuter() {
        User user = new User("REQUIRES_NEW-外", "outer@test.com", "13000000000");
        user.setTenantId(1L);
        user.setVersion(0);
        userMapper.insert(user);
        try {
            self.requiresNewInner(user.getId());
        } catch (Exception e) {
            // 内层事务已独立回滚
        }
        // 外层继续执行，不受内层回滚影响
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requiresNewInner(Long userId) {
        User u = userMapper.selectById(userId);
        // 内层事务看不到外层未提交的 insert！→ u 为 null
        if (u != null) {
            u.setPhone("NEW-INNER");
            userMapper.update(u);
        }
        throw new RuntimeException("内层事务故意回滚");
    }

    // ===== NESTED：savepoint 嵌套 =====

    @Transactional
    public void nestedOuter() {
        User user = new User("NESTED-外", "nested-outer@test.com", "13000000000");
        user.setTenantId(1L);
        user.setVersion(0);
        userMapper.insert(user);

        try {
            self.nestedInner(user.getId());
        } catch (Exception e) {
            // 内层 savepoint 回滚，外层 insert 还在
        }
        // 最终只有外层 insert 生效
    }

    @Transactional(propagation = Propagation.NESTED)
    public void nestedInner(Long userId) {
        User u = userMapper.selectById(userId);
        // NESTED 在同一 conn 上打 savepoint → 能看到外层未提交的 insert
        u.setPhone("NESTED-INNER");
        userMapper.update(u);
        throw new RuntimeException("内层 savepoint 回滚");
    }

    // ===== AOP 陷阱：this 调用绕过代理 =====

    public void thisCallBypass() {
        // 直接调用，不经过 Spring AOP 代理 → @Transactional 不生效！
        this.methodWithTx();
    }

    @Transactional
    public void methodWithTx() {
        User user = new User("AOP陷阱", "aop-trap@test.com", "13000000000");
        user.setTenantId(1L);
        user.setVersion(0);
        userMapper.insert(user);
        // 没有事务！autoCommit → 立刻提交，无法回滚
    }

    // ===== FOR UPDATE：悲观锁，只可在事务内使用 =====

    @Transactional
    public Order selectForUpdate(Long orderId) {
        return orderMapper.selectForUpdate(orderId);  // 行锁，事务提交释放
    }
}
