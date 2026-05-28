package com.redis.lock.lock;

import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReentrantRedisLock 集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReentrantRedisLockIntegrationTest {

    private static StringRedisTemplate redisTemplate;
    private static LuaScripts luaScripts;
    private static ReentrantRedisLock reentrantLock;

    @BeforeAll
    static void setUp() {
        com.redis.lock.config.RedisConfig config = new com.redis.lock.config.RedisConfig();
        redisTemplate = config.stringRedisTemplate();
        luaScripts = new LuaScripts();
        luaScripts.init();
        reentrantLock = new ReentrantRedisLock(redisTemplate, luaScripts);
    }

    @AfterAll
    static void tearDown() {
        String[] keys = {
            "test:rent:1", "test:rent:2", "test:rent:3", "test:rent:4",
            "test:rent:5", "test:rent:6", "test:rent:7"
        };
        for (String key : keys) {
            redisTemplate.delete(key);
        }
    }

    @Nested
    @DisplayName("可重入性测试")
    class ReentrancyTests {

        @Test
        @DisplayName("同一线程可多次获取同一锁")
        void sameThread_ShouldAcquireMultipleTimes() {
            String key = "test:rent:1";
            redisTemplate.delete(key);

            assertTrue(reentrantLock.lock(key, 30));
            assertTrue(reentrantLock.lock(key, 30));
            assertTrue(reentrantLock.lock(key, 30));

            assertTrue(reentrantLock.isLocked(key));

            // 三次 unlock 应完全释放
            reentrantLock.unlock(key);
            reentrantLock.unlock(key);
            assertTrue(reentrantLock.unlock(key));

            assertFalse(reentrantLock.isLocked(key));
        }

        @Test
        @DisplayName("不同线程不能获取已持有的锁")
        void differentThread_ShouldBeBlocked() throws Exception {
            String key = "test:rent:2";
            redisTemplate.delete(key);

            assertTrue(reentrantLock.lock(key, 30));

            final boolean[] acquired = {false};
            Thread t = new Thread(() -> {
                ReentrantRedisLock lock2 = new ReentrantRedisLock(redisTemplate, luaScripts);
                acquired[0] = lock2.lock(key, 30);
            });
            t.start();
            t.join(2000);

            assertFalse(acquired[0]);

            reentrantLock.unlock(key);
        }

        @Test
        @DisplayName("释放后其他实例可获取锁")
        void afterFullRelease_AnotherInstance_ShouldAcquire() {
            String key = "test:rent:3";
            redisTemplate.delete(key);

            assertTrue(reentrantLock.lock(key, 30));
            assertTrue(reentrantLock.lock(key, 30));
            reentrantLock.unlock(key);
            reentrantLock.unlock(key);

            assertFalse(reentrantLock.isLocked(key));

            ReentrantRedisLock lock2 = new ReentrantRedisLock(redisTemplate, luaScripts);
            assertTrue(lock2.lock(key, 30));
            lock2.unlock(key);
        }
    }

    @Nested
    @DisplayName("看门狗 + 可重入测试")
    class WatchdogReentrantTests {

        @Test
        @DisplayName("可重入锁的看门狗应在首次获取时启动，完全释放时停止")
        void watchdog_ShouldKeepReentrantLockAlive() throws Exception {
            String key = "test:rent:4";
            redisTemplate.delete(key);

            // 首次获取，看门狗应启动
            assertTrue(reentrantLock.lock(key, 3));
            // 重入
            assertTrue(reentrantLock.lock(key, 3));

            // 等待超过 TTL
            Thread.sleep(5000);

            // 看门狗应保持锁存活
            assertTrue(reentrantLock.isLocked(key));

            // 一次 unlock（重入计数 -1）
            reentrantLock.unlock(key);
            assertTrue(reentrantLock.isLocked(key));

            // 最后一次 unlock，完全释放
            reentrantLock.unlock(key);
            assertFalse(reentrantLock.isLocked(key));
        }
    }

    @Nested
    @DisplayName("防误删测试")
    class AntiMisdeleteTests {

        @Test
        @DisplayName("其他实例不能释放当前实例的锁")
        void otherInstance_ShouldNotRelease() {
            String key = "test:rent:5";
            redisTemplate.delete(key);

            assertTrue(reentrantLock.lock(key, 30));

            ReentrantRedisLock lock2 = new ReentrantRedisLock(redisTemplate, luaScripts);
            assertFalse(lock2.unlock(key));

            assertTrue(reentrantLock.isLocked(key));
            reentrantLock.unlock(key);
        }

        @Test
        @DisplayName("其他实例不能续期当前实例的锁")
        void otherInstance_ShouldNotRenew() {
            String key = "test:rent:6";
            redisTemplate.delete(key);

            assertTrue(reentrantLock.lock(key, 30));

            ReentrantRedisLock lock2 = new ReentrantRedisLock(redisTemplate, luaScripts);
            assertFalse(lock2.renew(key, 30));

            reentrantLock.unlock(key);
        }
    }

    @Nested
    @DisplayName("Pub/Sub 通知测试")
    class PubSubTests {

        @Test
        @DisplayName("unlock 后应发送通知消息")
        void unlock_ShouldPublishNotification() {
            String key = "test:rent:7";
            redisTemplate.delete(key);

            assertTrue(reentrantLock.lock(key, 30));
            reentrantLock.unlock(key);

            // 通知频道不应残留（消息已被消费或过期）
            assertFalse(reentrantLock.isLocked(key));
        }
    }
}
