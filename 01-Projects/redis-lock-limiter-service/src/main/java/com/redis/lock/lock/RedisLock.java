package com.redis.lock.lock;

import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.UUID;

/**
 * Redis 分布式锁实现
 */
public class RedisLock implements DistributedLock {

    private final StringRedisTemplate redisTemplate;
    private final LuaScripts luaScripts;
    private final String uniqueId;
    private final Watchdog watchdog;

    public RedisLock(StringRedisTemplate redisTemplate, LuaScripts luaScripts) {
        this.redisTemplate = redisTemplate;
        this.luaScripts = luaScripts;
        this.uniqueId = UUID.randomUUID().toString();
        this.watchdog = new Watchdog();
    }

    @Override
    public boolean lock(String key, long ttl) {
        Long result = redisTemplate.execute(
            luaScripts.getAcquireLockScript(),
            luaScripts.getAcquireKeys(key),
            uniqueId,
            String.valueOf(ttl)
        );
        boolean acquired = result != null && result == 1;
        if (acquired) {
            watchdog.start(key, ttl, this);
        }
        return acquired;
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
        watchdog.stop(key);
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
     * 续期锁（package-private，供 Watchdog 调用）
     * @return true 续期成功，false 锁已不属于当前实例（应停止看门狗）
     */
    boolean renew(String key, long ttl) {
        Long result = redisTemplate.execute(
            luaScripts.getRenewLockScript(),
            luaScripts.getRenewKeys(key),
            uniqueId,
            String.valueOf(ttl)
        );
        return result != null && result == 1;
    }

    /**
     * 获取当前锁持有者的唯一标识
     */
    public String getUniqueId() {
        return uniqueId;
    }
}