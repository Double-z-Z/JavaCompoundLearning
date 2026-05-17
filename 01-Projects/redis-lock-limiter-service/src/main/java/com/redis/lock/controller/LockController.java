package com.redis.lock.controller;

import com.redis.lock.lock.DistributedLock;
import com.redis.lock.lock.LuaScripts;
import com.redis.lock.lock.RedisLock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * 分布式锁 REST API
 */
@RestController
@RequestMapping("/api/lock")
public class LockController {

    private final DistributedLock distributedLock;
    private final String uniqueId = UUID.randomUUID().toString();

    public LockController(StringRedisTemplate redisTemplate, LuaScripts luaScripts) {
        this.distributedLock = new RedisLock(redisTemplate, luaScripts);
    }

    @PostMapping("/acquire")
    public Mono<Map<String, Object>> acquireLock(@RequestBody Map<String, Object> request) {
        String key = (String) request.get("key");
        Number ttlNum = (Number) request.get("ttlSeconds");
        long ttl = ttlNum != null ? ttlNum.longValue() : 30;

        boolean success = distributedLock.lock(key, ttl);

        return Mono.just(Map.of(
            "success", success,
            "lockKey", key,
            "ttl", ttl,
            "uniqueId", uniqueId
        ));
    }

    @PostMapping("/release")
    public Mono<Map<String, Object>> releaseLock(@RequestBody Map<String, Object> request) {
        String key = (String) request.get("key");

        boolean released = distributedLock.unlock(key);

        return Mono.just(Map.of(
            "success", true,
            "released", released,
            "key", key
        ));
    }

    @GetMapping("/status/{key}")
    public Mono<Map<String, Object>> getLockStatus(@PathVariable String key) {
        boolean locked = distributedLock.isLocked(key);

        return Mono.just(Map.of(
            "key", key,
            "locked", locked
        ));
    }
}