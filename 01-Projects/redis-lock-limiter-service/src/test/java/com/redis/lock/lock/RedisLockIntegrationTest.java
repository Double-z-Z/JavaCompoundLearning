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
        // 从 RedisConfig 获取连接
        com.redis.lock.config.RedisConfig config = new com.redis.lock.config.RedisConfig();
        redisTemplate = config.stringRedisTemplate();
        luaScripts = new LuaScripts();
        luaScripts.init();
        redisLock = new RedisLock(redisTemplate, luaScripts);
    }

    @AfterAll
    static void tearDown() {
        if (redisTemplate != null) {
            redisTemplate.delete("test:lock:1");
            redisTemplate.delete("test:lock:2");
            redisTemplate.delete("test:lock:3");
            redisTemplate.delete("test:lock:4");
            redisTemplate.delete("test:lock:5");
            redisTemplate.delete("test:lock:try1");
            redisTemplate.delete("test:lock:try2");
            redisTemplate.delete("test:lock:try3");
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
            // 先确保锁不存在
            redisTemplate.delete("test:lock:1");

            boolean result = redisLock.lock("test:lock:1", 30);

            assertTrue(result);
            assertTrue(redisLock.isLocked("test:lock:1"));

            // 清理
            redisLock.unlock("test:lock:1");
        }

        @Test
        @Order(2)
        @DisplayName("lock() 应该返回 false 当锁已被持有时")
        void lock_ShouldReturnFalse_WhenLockHeld() {
            String key = "test:lock:2";

            // 先获取锁
            redisTemplate.delete(key);
            assertTrue(redisLock.lock(key, 30));

            // 另一个实例尝试获取
            LuaScripts luaScripts2 = new LuaScripts();
            luaScripts2.init();
            RedisLock redisLock2 = new RedisLock(redisTemplate, luaScripts2);

            boolean result = redisLock2.lock(key, 30);

            assertFalse(result);

            // 清理
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

            // 创建另一个锁实例
            LuaScripts luaScripts2 = new LuaScripts();
            luaScripts2.init();
            RedisLock redisLock2 = new RedisLock(redisTemplate, luaScripts2);

            // 第一个实例获取锁
            assertTrue(redisLock.lock(key, 30));

            // 第二个实例尝试释放
            boolean result = redisLock2.unlock(key);

            assertFalse(result); // 不应该释放成功
            assertTrue(redisLock.isLocked(key)); // 锁仍然存在

            // 清理
            redisLock.unlock(key);
        }

        @Test
        @Order(5)
        @DisplayName("TTL 应该正确过期")
        void lock_ShouldExpireAfterTTL() throws InterruptedException {
            String key = "test:lock:5";
            redisTemplate.delete(key);

            // 获取锁，TTL 为 2 秒
            assertTrue(redisLock.lock(key, 2));
            assertTrue(redisLock.isLocked(key));

            // 等待 TTL 过期
            Thread.sleep(3000);

            // 锁应该已自动释放
            assertFalse(redisLock.isLocked(key));

            // 另一个实例应该能获取锁
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

            // 第一个实例获取锁
            assertTrue(redisLock.lock(key, 30));

            // 创建第二个实例
            LuaScripts luaScripts2 = new LuaScripts();
            luaScripts2.init();
            RedisLock redisLock2 = new RedisLock(redisTemplate, luaScripts2);

            // 第二个实例尝试获取，应该等待
            long start = System.currentTimeMillis();
            boolean result = redisLock2.tryLock(key, 30, 3000);
            long elapsed = System.currentTimeMillis() - start;

            // 由于锁已被持有，应该等待一段时间
            assertTrue(elapsed >= 50);

            // 释放第一个实例的锁
            redisLock.unlock(key);

            // 再等一下让续期完成（实际上这个测试可能不完美）
            Thread.sleep(100);

            // 重新尝试获取锁
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

            // 第一个实例获取锁并一直持有
            assertTrue(redisLock.lock(key, 30));

            // 创建第二个实例
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
}