package com.example.order.interfaces.rest;

import com.example.order.infrastructure.persistence.sharding.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

import java.util.List;
import java.util.Map;

/**
 * MyBatis 14 项核心特性逐一演示.
 *
 * 每个端点对应 ShardOrderMapper.xml 中的一项特性.
 * 验证方式: curl + 观察 ShardingSphere SQL 日志中的 Actual SQL.
 */
@RestController
@RequestMapping("/api/v2/features")
public class FeatureDemoController {

    private final ShardOrderMapper orderMapper;
    private final ShardUserMapper userMapper;

    public FeatureDemoController(ShardOrderMapper orderMapper, ShardUserMapper userMapper) {
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
    }

    // === 1. <sql> + <include> ===
    @GetMapping("/1-sql-include")
    public ShardOrder sqlInclude(@RequestParam Long id) {
        return orderMapper.selectById(id);
    }

    // === 2. <where> + <if> ===
    @GetMapping("/2-where-if")
    public List<ShardOrder> whereIf(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) Long userId) {
        return orderMapper.selectByCondition(null, status, null, null, userId);
    }

    // === 3. <choose>/<when>/<otherwise> ===
    @GetMapping("/3-choose")
    public List<ShardOrder> choose(@RequestParam(required = false) String status) {
        return orderMapper.selectByPriority(null, status, null);
    }

    // === 4. <foreach> IN ===
    @GetMapping("/4-foreach-in")
    public List<ShardOrder> foreachIn(@RequestParam List<Long> ids) {
        return orderMapper.selectByIds(ids);
    }

    // === 5. <foreach> 批量插入 ===
    @PostMapping("/5-foreach-batch")
    public Map<String, Integer> foreachBatch(@RequestBody List<Map<String, Object>> orders) {
        var list = orders.stream().map(o -> {
            ShardOrder so = new ShardOrder();
            so.setUserId(Long.valueOf(o.get("userId").toString()));
            so.setOrderNo((String) o.get("orderNo"));
            so.setTotalAmount(new BigDecimal(o.get("totalAmount").toString()));
            so.setStatus((String) o.getOrDefault("status", "PENDING"));
            return so;
        }).toList();
        return Map.of("rows", orderMapper.insertBatch(list));
    }

    // === 6. <set> + <if> 动态更新 ===
    @PutMapping("/6-set-if")
    public Map<String, Integer> setIf(@RequestBody Map<String, Object> body) {
        ShardOrder so = new ShardOrder();
        so.setId(Long.valueOf(body.get("id").toString()));
        if (body.containsKey("status")) so.setStatus((String) body.get("status"));
        if (body.containsKey("orderNo")) so.setOrderNo((String) body.get("orderNo"));
        if (body.containsKey("totalAmount")) so.setTotalAmount(new BigDecimal(body.get("totalAmount").toString()));
        return Map.of("rows", orderMapper.updateDynamic(so));
    }

    // === 7. <trim> ===
    @GetMapping("/7-trim")
    public List<ShardOrder> trim(@RequestParam(required = false) String status,
                                  @RequestParam(required = false) BigDecimal minAmount) {
        return orderMapper.selectByConditionTrim(null, status, minAmount);
    }

    // === 8. <bind> LIKE ===
    @GetMapping("/8-bind-like")
    public List<ShardOrder> bindLike(@RequestParam String keyword) {
        return orderMapper.selectByOrderNoLike(keyword);
    }

    // === 9. 窗口函数 + GROUP BY ===
    @GetMapping("/9-window-func")
    public List<Map<String, Object>> windowFunc() {
        return orderMapper.selectUserOrderStats();
    }

    // === 10. LIMIT/OFFSET 分页 ===
    @GetMapping("/10-pagination")
    public List<ShardOrder> pagination(@RequestParam(defaultValue = "0") int offset,
                                        @RequestParam(defaultValue = "10") int limit) {
        return orderMapper.selectPage(offset, limit);
    }

    // === 12.a <association> 一对一 ===
    @GetMapping("/12a-association")
    public ShardOrder association(@RequestParam Long id) {
        return orderMapper.selectOrderWithUser(id);
    }

    // === 12.b <collection> 一对多 ===
    @GetMapping("/12b-collection")
    public ShardOrder collection(@RequestParam Long id) {
        return orderMapper.selectOrderWithItems(id);
    }

    // === 12.c 完整嵌套 ===
    @GetMapping("/12c-nested")
    public ShardOrder fullNested(@RequestParam Long id) {
        return orderMapper.selectOrderFull(id);
    }

    // === 13. N+1 演示 ===
    @GetMapping("/13-n-plus-one")
    public ShardOrder nPlusOne(@RequestParam Long id) {
        return orderMapper.selectOrderNestedSelect(id);
    }

    // === 14. 存储过程 ===
    @PostMapping("/14-procedure")
    public Map<String, String> callProcedure(@RequestParam Long orderId,
                                               @RequestParam String newStatus) {
        orderMapper.callUpdateOrderStatus(orderId, newStatus);
        return Map.of("result", "ok");
    }

    // === 15. 悲观锁 ===
    @GetMapping("/15-pessimistic-lock")
    public ShardOrder pessimisticLock(@RequestParam Long id) {
        return orderMapper.selectForUpdate(id);
    }

    // === 16. 乐观锁 (@Version) ===
    @PutMapping("/16-optimistic-lock")
    public Map<String, Object> optimisticLock(@RequestParam Long userId,
                                               @RequestParam String newName) {
        ShardUser user = userMapper.selectById(userId);
        user.setUsername(newName);
        int rows = userMapper.updateById(user);  // WHERE id=? AND version=?
        return Map.of("updated", rows > 0, "newVersion", user.getVersion(),
                      "userId", userId, "newName", newName);
    }
}
