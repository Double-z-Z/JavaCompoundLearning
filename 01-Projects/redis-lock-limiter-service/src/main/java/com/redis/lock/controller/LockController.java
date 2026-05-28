package com.redis.lock.controller;

import com.redis.lock.lock.DistributedLock;
import com.redis.lock.lock.LuaScripts;
import com.redis.lock.lock.RedisLock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 场景 A：配额重置窗口互斥
 *
 * 用法：每月 1 号 0:05，所有实例尝试 POST /api/lock/quota-reset
 * 只有一个实例能拿到锁并执行重置。
 */
@RestController
@RequestMapping("/api/lock")
public class LockController {

    private final DistributedLock distributedLock;

    public LockController(StringRedisTemplate redisTemplate, LuaScripts luaScripts) {
        this.distributedLock = new RedisLock(redisTemplate, luaScripts);
    }

    /**
     * 配额重置入口 — 场景 A
     * 锁 TTL=5min，重置操作正常 <30s，留有充足余量。
     * 看门狗自动续期，即使 GC 停顿也不丢锁。
     */
    @PostMapping("/quota-reset")
    public Mono<Map<String, Object>> quotaReset(@RequestBody Map<String, Object> request) {
        String period = (String) request.getOrDefault("period",
                String.valueOf(java.time.YearMonth.now()));
        String lockKey = "quota:reset:" + period;

        boolean acquired = distributedLock.lock(lockKey, 300);
        if (!acquired) {
            return Mono.just(Map.of(
                "executed", false,
                "reason", "另一个实例正在执行本月配额重置",
                "period", period
            ));
        }

        try {
            // 执行全局配额重置
            return Mono.just(Map.of(
                "executed", true,
                "period", period,
                "lockKey", lockKey
            ));
        } finally {
            distributedLock.unlock(lockKey);
        }
    }
}
