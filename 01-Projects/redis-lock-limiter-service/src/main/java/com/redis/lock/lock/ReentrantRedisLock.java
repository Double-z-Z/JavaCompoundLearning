package com.redis.lock.lock;

import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 可重入分布式锁 — Hash 结构，支持同一线程多次获取同一把锁。
 *
 * Redis 数据结构:
 *   lock:reentrant:{resource} (Hash)
 *     owner: uuid
 *     {threadId}: count
 */
public class ReentrantRedisLock implements DistributedLock {

    private final StringRedisTemplate redisTemplate;
    private final LuaScripts luaScripts;
    private final String uniqueId;
    private final Watchdog watchdog;

    /** 本地追踪: key → threadId → 重入计数 (仅用于判断是否需要启动/停止看门狗) */
    private final Map<String, Map<Long, Integer>> localRefs = new ConcurrentHashMap<>();

    public ReentrantRedisLock(StringRedisTemplate redisTemplate, LuaScripts luaScripts) {
        this.redisTemplate = redisTemplate;
        this.luaScripts = luaScripts;
        this.uniqueId = UUID.randomUUID().toString();
        this.watchdog = new Watchdog();
    }

    @Override
    public boolean lock(String key, long ttl) {
        long threadId = Thread.currentThread().getId();
        List<Long> result = redisTemplate.execute(
            luaScripts.getAcquireReentrantScript(),
            luaScripts.getAcquireKeys(key),
            uniqueId,
            String.valueOf(threadId),
            String.valueOf(ttl)
        );
        boolean acquired = result != null && result.get(0) == 1;
        if (acquired) {
            int count = result.get(1).intValue();
            trackLocal(key, threadId, count);
            if (count == 1) {
                watchdog.start(key, ttl, () -> isLocked(key), this::renew);
            }
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
        long threadId = Thread.currentThread().getId();
        Long result = redisTemplate.execute(
            luaScripts.getReleaseReentrantScript(),
            luaScripts.getReleaseKeys(key),
            uniqueId,
            String.valueOf(threadId)
        );
        boolean released = result != null && result > 0;
        if (released) {
            untrackLocal(key, threadId);
            if (result == 2) {
                watchdog.stop(key);
                redisTemplate.convertAndSend("lock:notify:" + key, "released");
            }
        }
        return released;
    }

    @Override
    public boolean isLocked(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    boolean renew(String key, long ttl) {
        Long result = redisTemplate.execute(
            luaScripts.getRenewReentrantScript(),
            luaScripts.getRenewKeys(key),
            uniqueId,
            String.valueOf(ttl)
        );
        return result != null && result == 1;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    private void trackLocal(String key, long threadId, int count) {
        localRefs.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                 .put(threadId, count);
    }

    private void untrackLocal(String key, long threadId) {
        Map<Long, Integer> threads = localRefs.get(key);
        if (threads != null) {
            threads.remove(threadId);
            if (threads.isEmpty()) {
                localRefs.remove(key);
            }
        }
    }
}
