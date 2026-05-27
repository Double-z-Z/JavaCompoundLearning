package com.redis.lock.lock;

import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisLock 集成测试 - 使用真实 Redis 集群
 */
@DisplayName("RedisLock 集成测试 (真实Redis)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RedisLockIntegrationTest {

    private static StringRedisTemplate redisTemplate;
    private static LuaScripts luaScripts;
    private static RedisLock redisLock;

    @BeforeAll
    static void setUp() {
        com.redis.lock.config.RedisConfig config = new com.redis.lock.config.RedisConfig();
        redisTemplate = config.stringRedisTemplate();
        luaScripts = new LuaScripts();
        luaScripts.init();
        redisLock = new RedisLock(redisTemplate, luaScripts);
    }

    @AfterAll
    static void tearDown() {
        String[] keys = {
            "test:lock:1", "test:lock:2", "test:lock:3", "test:lock:4", "test:lock:5",
            "test:lock:try1", "test:lock:try2", "test:lock:try3",
            "test:lock:wd1", "test:lock:wd2", "test:lock:wd3"
        };
        for (String key : keys) {
            redisTemplate.delete(key);
        }
    }

    @Nested
    @DisplayName("基础功能测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class BasicFunctionTests {

        @Test
        @Order(1)
        @DisplayName("lock() 应该返回 true 当锁可用时")
        void lock_ShouldReturnTrue_WhenLockAvailable() {
            redisTemplate.delete("test:lock:1");

            boolean result = redisLock.lock("test:lock:1", 30);

            assertTrue(result);
            assertTrue(redisLock.isLocked("test:lock:1"));

            redisLock.unlock("test:lock:1");
        }

        @Test
        @Order(2)
        @DisplayName("lock() 应该返回 false 当锁已被持有时")
        void lock_ShouldReturnFalse_WhenLockHeld() {
            String key = "test:lock:2";
            redisTemplate.delete(key);
            assertTrue(redisLock.lock(key, 30));

            LuaScripts luaScripts2 = new LuaScripts();
            luaScripts2.init();
            RedisLock redisLock2 = new RedisLock(redisTemplate, luaScripts2);

            boolean result = redisLock2.lock(key, 30);

            assertFalse(result);

            redisLock.unlock(key);
        }

        @Test
        @Order(3)
        @DisplayName("unlock() 应该返回 true 当释放自己的锁时")
        void unlock_ShouldReturnTrue_WhenReleasingOwnLock() {
            String key = "test:lock:3";
            redisTemplate.delete(key);

            assertTrue(redisLock.lock(key, 30));
            assertTrue(redisLock.unlock(key));
            assertFalse(redisLock.isLocked(key));
        }

        @Test
        @Order(4)
        @DisplayName("unlock() 应该返回 false 当释放他人的锁时")
        void unlock_ShouldReturnFalse_WhenReleasingOthersLock() {
            String key = "test:lock:4";
            redisTemplate.delete(key);

            LuaScripts luaScripts2 = new LuaScripts();
            luaScripts2.init();
            RedisLock redisLock2 = new RedisLock(redisTemplate, luaScripts2);

            assertTrue(redisLock.lock(key, 30));

            boolean result = redisLock2.unlock(key);

            assertFalse(result);
            assertTrue(redisLock.isLocked(key));

            redisLock.unlock(key);
        }

        @Test
        @Order(5)
        @DisplayName("unlock 后锁立即释放，其他实例可获取")
        void unlock_ShouldReleaseImmediately_ForOtherInstance() {
            String key = "test:lock:5";
            redisTemplate.delete(key);

            assertTrue(redisLock.lock(key, 30));
            redisLock.unlock(key);

            LuaScripts luaScripts2 = new LuaScripts();
            luaScripts2.init();
            RedisLock redisLock2 = new RedisLock(redisTemplate, luaScripts2);

            assertTrue(redisLock2.lock(key, 30));
            redisLock2.unlock(key);
        }
    }

    @Nested
    @DisplayName("tryLock 测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class TryLockTests {

        @Test
        @Order(1)
        @DisplayName("tryLock() 应该立即返回 true 当锁可用时")
        void tryLock_ShouldReturnTrueImmediately_WhenLockAvailable() {
            String key = "test:lock:try1";
            redisTemplate.delete(key);

            long start = System.currentTimeMillis();
            boolean result = redisLock.tryLock(key, 30, 5000);
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(result);
            assertTrue(elapsed < 100);

            redisLock.unlock(key);
        }

        @Test
        @Order(2)
        @DisplayName("tryLock() 应该等待后返回 true 当锁最终可用时")
        void tryLock_ShouldWaitAndReturnTrue_WhenLockBecomesAvailable() throws InterruptedException {
            String key = "test:lock:try2";
            redisTemplate.delete(key);

            assertTrue(redisLock.lock(key, 30));

            LuaScripts luaScripts2 = new LuaScripts();
            luaScripts2.init();
            RedisLock redisLock2 = new RedisLock(redisTemplate, luaScripts2);

            long start = System.currentTimeMillis();
            boolean result = redisLock2.tryLock(key, 30, 3000);
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(elapsed >= 50);

            redisLock.unlock(key);
            Thread.sleep(100);

            result = redisLock2.tryLock(key, 30, 1000);
            assertTrue(result);

            redisLock2.unlock(key);
        }

        @Test
        @Order(3)
        @DisplayName("tryLock() 应该等待后返回 false 当超时后仍不可用")
        void tryLock_ShouldReturnFalse_WhenTimeoutExpires() {
            String key = "test:lock:try3";
            redisTemplate.delete(key);

            assertTrue(redisLock.lock(key, 30));

            LuaScripts luaScripts2 = new LuaScripts();
            luaScripts2.init();
            RedisLock redisLock2 = new RedisLock(redisTemplate, luaScripts2);

            long start = System.currentTimeMillis();
            boolean result = redisLock2.tryLock(key, 30, 500);
            long elapsed = System.currentTimeMillis() - start;

            assertFalse(result);
            assertTrue(elapsed >= 500);

            redisLock.unlock(key);
        }
    }

    @Nested
    @DisplayName("看门狗测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class WatchdogTests {

        @Test
        @Order(1)
        @DisplayName("看门狗应该在 TTL 过后保持锁存活")
        void watchdog_ShouldKeepLockAlive_PastInitialTTL() throws InterruptedException {
            String key = "test:lock:wd1";
            redisTemplate.delete(key);

            // 获取锁，TTL=3 秒。看门狗每 1 秒续期
            assertTrue(redisLock.lock(key, 3));

            // 等待 5 秒（超过 TTL 但看门狗应该续期了）
            Thread.sleep(5000);

            // 锁应该仍然有效
            assertTrue(redisLock.isLocked(key));

            redisLock.unlock(key);
            assertFalse(redisLock.isLocked(key));
        }

        @Test
        @Order(2)
        @DisplayName("unlock 后看门狗应停止，锁应正常过期")
        void watchdog_ShouldStopAfterUnlock() throws InterruptedException {
            String key = "test:lock:wd2";
            redisTemplate.delete(key);

            assertTrue(redisLock.lock(key, 2));
            redisLock.unlock(key);

            // 等待超过 TTL，锁不应该存在
            Thread.sleep(3000);

            LuaScripts luaScripts2 = new LuaScripts();
            luaScripts2.init();
            RedisLock redisLock2 = new RedisLock(redisTemplate, luaScripts2);
            assertTrue(redisLock2.lock(key, 5));
            redisLock2.unlock(key);
        }

        @Test
        @Order(3)
        @DisplayName("renew() 应返回 false 当锁不归属当前实例")
        void renew_ShouldReturnFalse_WhenLockNotOwned() {
            String key = "test:lock:wd3";
            redisTemplate.delete(key);

            LuaScripts luaScripts2 = new LuaScripts();
            luaScripts2.init();
            RedisLock redisLock2 = new RedisLock(redisTemplate, luaScripts2);

            assertTrue(redisLock.lock(key, 30));

            // redisLock2 尝试续期 redisLock 持有的锁
            boolean result = redisLock2.renew(key, 30);
            assertFalse(result);

            redisLock.unlock(key);
        }
    }
}
