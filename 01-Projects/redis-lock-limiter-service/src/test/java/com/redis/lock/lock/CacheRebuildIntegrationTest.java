package com.redis.lock.lock;

import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 场景 B：缓存热点重建防护
 *
 * 方案 A（业界标准）：单 key 分布式锁 + DCL（双重检查锁）
 * 1000 个并发请求，只有 1 次 DB 查询，其余 999 从缓存读取。
 */
@DisplayName("缓存重建集成测试 (场景B)")
class CacheRebuildIntegrationTest {

    private static StringRedisTemplate redisTemplate;
    private static LuaScripts luaScripts;

    @BeforeAll
    static void setUp() {
        com.redis.lock.config.RedisConfig config = new com.redis.lock.config.RedisConfig();
        redisTemplate = config.stringRedisTemplate();
        luaScripts = new LuaScripts();
        luaScripts.init();
    }

    @AfterAll
    static void tearDown() {
        redisTemplate.delete("cache:product:1001");
        redisTemplate.delete("lock:rebuild:product:1001");
    }

    @Test
    @DisplayName("1000 并发缓存穿透 → 只有 1 次 DB 查询")
    void oneThousandConcurrent_OnlyOneRebuild() throws Exception {
        String cacheKey = "cache:product:1001";
        String lockKey = "lock:rebuild:product:1001";

        // 确保缓存为空，模拟过期
        redisTemplate.delete(cacheKey);
        redisTemplate.delete(lockKey);

        AtomicInteger dbQueryCount = new AtomicInteger(0);
        AtomicInteger rebuildSuccess = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(1000);

        for (int i = 0; i < 1000; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    String cached = getOrRebuild(cacheKey, lockKey, dbQueryCount);
                    if (cached != null) {
                        rebuildSuccess.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            }).start();
        }

        // 同时释放所有线程
        startLatch.countDown();
        finishLatch.await();

        assertEquals(1, dbQueryCount.get(), "只能有 1 次 DB 查询");
        assertEquals(1000, rebuildSuccess.get(), "1000 个请求都拿到缓存数据");

        redisTemplate.delete(cacheKey);
        redisTemplate.delete(lockKey);
    }

    @Test
    @DisplayName("DCL 二次检查：缓存已存在时不进入锁")
    void doubleCheckLock_ShouldSkipLockWhenCacheExists() {
        String cacheKey = "cache:product:2001";
        String lockKey = "lock:rebuild:product:2001";

        redisTemplate.delete(cacheKey);
        redisTemplate.delete(lockKey);

        AtomicInteger dbQueryCount = new AtomicInteger(0);
        AtomicInteger lockAttempts = new AtomicInteger(0);

        // 预填缓存
        redisTemplate.opsForValue().set(cacheKey, "cached-data");

        // 请求：缓存命中，不应加锁
        String result = redisTemplate.opsForValue().get(cacheKey);
        if (result == null) {
            lockAttempts.incrementAndGet();
            RedisLock lock = new RedisLock(redisTemplate, luaScripts);
            if (lock.lock(lockKey, 10)) {
                try {
                    result = redisTemplate.opsForValue().get(cacheKey);
                    if (result == null) {
                        dbQueryCount.incrementAndGet();
                        result = "fresh-data";
                        redisTemplate.opsForValue().set(cacheKey, result);
                    }
                } finally {
                    lock.unlock(lockKey);
                }
            }
        }

        assertEquals("cached-data", result);
        assertEquals(0, lockAttempts.get(), "缓存命中时不应尝试加锁");
        assertEquals(0, dbQueryCount.get());

        redisTemplate.delete(cacheKey);
    }

    /**
     * 场景 B 核心逻辑：单 key 锁 + DCL + 看门狗
     */
    private String getOrRebuild(String cacheKey, String lockKey,
                                AtomicInteger dbQueryCount) throws InterruptedException {
        // 1. 先查缓存
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 2. 缓存为空 → 尝试获取重建锁
        RedisLock lock = new RedisLock(redisTemplate, luaScripts);
        if (lock.tryLock(lockKey, 30, 5000)) {
            try {
                // 3. DCL 二次检查（另一个线程可能已重建好）
                cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    return cached;
                }

                // 4. 查 DB + 写缓存
                dbQueryCount.incrementAndGet();
                Thread.sleep(10); // 模拟 DB 查询

                cached = "product-data-" + cacheKey;
                redisTemplate.opsForValue().set(cacheKey, cached);
                return cached;
            } finally {
                lock.unlock(lockKey);
            }
        }

        // 5. 没拿到锁 → 其他线程在重建，轮询等缓存
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return cached;
            }
            Thread.sleep(20);
        }

        return null;
    }
}
