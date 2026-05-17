package com.redis.lock.lock;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁实现
 */
public class RedisLock implements DistributedLock {

    private final StringRedisTemplate redisTemplate;
    private final LuaScripts luaScripts;
    private final String uniqueId;

    public RedisLock(StringRedisTemplate redisTemplate, LuaScripts luaScripts) {
        this.redisTemplate = redisTemplate;
        this.luaScripts = luaScripts;
        this.uniqueId = UUID.randomUUID().toString();
    }

    @Override
    public boolean lock(String key, long ttl) {
        Long result = redisTemplate.execute(
            luaScripts.getAcquireLockScript(),
            luaScripts.getAcquireKeys(key),
            uniqueId,
            String.valueOf(ttl)
        );
        return result != null && result == 1;
    }

    @Override
    public boolean tryLock(String key, long ttl, long waitTime) {
        long deadline = System.currentTimeMillis() + waitTime;
        while (System.currentTimeMillis() < deadline) {
            if (lock(key, ttl)) {
                return true;
            }
            // 等待一段时间后重试
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean unlock(String key) {
        Long result = redisTemplate.execute(
            luaScripts.getReleaseLockScript(),
            luaScripts.getReleaseKeys(key),
            uniqueId
        );
        return result != null && result == 1;
    }

    @Override
    public boolean isLocked(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 获取当前锁持有者的唯一标识
     */
    public String getUniqueId() {
        return uniqueId;
    }
}