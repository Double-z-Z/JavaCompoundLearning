package com.redis.lock.limiter;

import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 限流器集成测试 - 包含三种限流策略
 */
@DisplayName("限流器集成测试 (真实Redis)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RateLimiterIntegrationTest {

    private static StringRedisTemplate redisTemplate;
    private static SlidingWindowLimiter slidingWindowLimiter;
    private static FixedWindowLimiter fixedWindowLimiter;
    private static TokenBucketLimiter tokenBucketLimiter;

    @BeforeAll
    static void setUp() {
        com.redis.lock.config.RedisConfig config = new com.redis.lock.config.RedisConfig();
        redisTemplate = config.stringRedisTemplate();
        slidingWindowLimiter = new SlidingWindowLimiter(redisTemplate);
        fixedWindowLimiter = new FixedWindowLimiter(redisTemplate);
        tokenBucketLimiter = new TokenBucketLimiter(redisTemplate);
    }

    @AfterAll
    static void tearDown() {
        // 清理测试数据
        redisTemplate.delete("limiter:sliding:test");
        redisTemplate.delete("limiter:fixed:test");
        redisTemplate.delete("limiter:token:test");
        redisTemplate.delete("limiter:sliding:test:burst");
        redisTemplate.delete("limiter:token:test:burst");
    }

    // 生成唯一key，确保测试隔离
    private String uniqueKey(String prefix) {
        return prefix + ":" + System.currentTimeMillis() + ":" + (int)(Math.random() * 10000);
    }

    @Nested
    @DisplayName("滑动窗口限流器测试 (SlidingWindowLimiter)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class SlidingWindowTests {

        @Test
        @Order(1)
        @DisplayName("应该允许前 N 个请求通过")
        void shouldAllowFirstNRequests() {
            String key = uniqueKey("sliding");
            long limit = 5;
            long window = 60;

            for (int i = 0; i < 5; i++) {
                assertTrue(slidingWindowLimiter.allow(key, limit, window),
                    "请求 " + (i + 1) + " 应该通过");
            }
        }

        @Test
        @Order(2)
        @DisplayName("超过限制后应该拒绝")
        void shouldRejectWhenLimitExceeded() {
            String key = uniqueKey("sliding-limit");
            long limit = 3;
            long window = 60;

            assertTrue(slidingWindowLimiter.allow(key, limit, window));
            assertTrue(slidingWindowLimiter.allow(key, limit, window));
            assertTrue(slidingWindowLimiter.allow(key, limit, window));
            assertFalse(slidingWindowLimiter.allow(key, limit, window));
        }

        @Test
        @Order(3)
        @DisplayName("窗口过期后应该重新允许")
        void shouldAllowAgainAfterWindowExpires() throws InterruptedException {
            String key = uniqueKey("sliding-expire");
            long limit = 2;
            long window = 3;

            assertTrue(slidingWindowLimiter.allow(key, limit, window));
            assertTrue(slidingWindowLimiter.allow(key, limit, window));
            assertFalse(slidingWindowLimiter.allow(key, limit, window));

            Thread.sleep(window * 1000 + 500);

            // 清理残留
            redisTemplate.delete("limiter:sliding:" + key);

            assertTrue(slidingWindowLimiter.allow(key, limit, window));
        }
    }

    @Nested
    @DisplayName("固定窗口限流器测试 (FixedWindowLimiter)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class FixedWindowTests {

        @Test
        @Order(1)
        @DisplayName("应该允许前 N 个请求通过")
        void shouldAllowFirstNRequests() {
            String key = uniqueKey("fixed");
            int limit = 5;
            int windowSec = 60;

            for (int i = 0; i < 5; i++) {
                assertTrue(fixedWindowLimiter.allowWithTimeSlice(key, limit, windowSec),
                    "请求 " + (i + 1) + " 应该通过");
            }
        }

        @Test
        @Order(2)
        @DisplayName("超过限制后应该拒绝")
        void shouldRejectWhenLimitExceeded() {
            String key = uniqueKey("fixed-limit");
            int limit = 3;
            int windowSec = 60;

            assertTrue(fixedWindowLimiter.allowWithTimeSlice(key, limit, windowSec));
            assertTrue(fixedWindowLimiter.allowWithTimeSlice(key, limit, windowSec));
            assertTrue(fixedWindowLimiter.allowWithTimeSlice(key, limit, windowSec));
            assertFalse(fixedWindowLimiter.allowWithTimeSlice(key, limit, windowSec));
        }

        @Test
        @Order(3)
        @DisplayName("边界穿透：窗口切换时可能通过超限请求")
        void shouldHaveBoundaryPenetration() throws InterruptedException {
            String key = uniqueKey("fixed-boundary");
            int limit = 2;
            int windowSec = 1;

            // 窗口1：放行 limit 个
            assertTrue(fixedWindowLimiter.allowWithTimeSlice(key, limit, windowSec));
            assertTrue(fixedWindowLimiter.allowWithTimeSlice(key, limit, windowSec));
            assertFalse(fixedWindowLimiter.allowWithTimeSlice(key, limit, windowSec));

            // 等待窗口切换
            Thread.sleep(1200);

            // 窗口2：又可以放行 limit 个（边界穿透）
            assertTrue(fixedWindowLimiter.allowWithTimeSlice(key, limit, windowSec));
            assertTrue(fixedWindowLimiter.allowWithTimeSlice(key, limit, windowSec));
            assertFalse(fixedWindowLimiter.allowWithTimeSlice(key, limit, windowSec));
        }
    }

    @Nested
    @DisplayName("令牌桶限流器测试 (TokenBucketLimiter)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class TokenBucketTests {

        @Test
        @Order(1)
        @DisplayName("应该允许前 N 个请求通过")
        void shouldAllowFirstNRequests() {
            String key = uniqueKey("token");
            long capacity = 5;
            long rate = 1; // 每秒补充1个

            // 初始桶满，可以一次通过 capacity 个
            for (int i = 0; i < capacity; i++) {
                assertTrue(tokenBucketLimiter.allowWithParams(key, capacity, rate),
                    "请求 " + (i + 1) + " 应该通过");
            }
        }

        @Test
        @Order(2)
        @DisplayName("桶空后应该拒绝")
        void shouldRejectWhenBucketEmpty() {
            String key = uniqueKey("token-empty");
            long capacity = 3;
            long rate = 1;

            // 消耗完桶内令牌
            assertTrue(tokenBucketLimiter.allow(key, capacity, rate));
            assertTrue(tokenBucketLimiter.allow(key, capacity, rate));
            assertTrue(tokenBucketLimiter.allow(key, capacity, rate));

            // 桶已空，拒绝
            assertFalse(tokenBucketLimiter.allow(key, capacity, rate));
        }

        @Test
        @Order(3)
        @DisplayName("令牌补充后应该重新允许")
        void shouldAllowAfterTokenRefill() throws InterruptedException {
            String key = uniqueKey("token-refill");
            long capacity = 2;
            long rate = 1; // 每秒补充1个

            assertTrue(tokenBucketLimiter.allow(key, capacity, rate));
            assertTrue(tokenBucketLimiter.allow(key, capacity, rate));
            assertFalse(tokenBucketLimiter.allow(key, capacity, rate));

            // 等待补充 1 个令牌
            Thread.sleep(1500);

            // 应该能通过 1 个
            assertTrue(tokenBucketLimiter.allow(key, capacity, rate));
        }

        @Test
        @Order(4)
        @DisplayName("突发能力：可以一次性消耗累积的令牌")
        void shouldSupportBurst() throws InterruptedException {
            String key = uniqueKey("token-burst");
            long capacity = 5;
            long rate = 2; // 每秒补充2个

            // 快速消耗所有令牌
            for (int i = 0; i < 5; i++) {
                assertTrue(tokenBucketLimiter.allowWithParams(key, capacity, rate),
                    "突发请求 " + (i + 1));
            }

            // 桶空，拒绝
            assertFalse(tokenBucketLimiter.allow(key, capacity, rate));

            // 等待补充（2秒补充4个，加上原有的1个 = 5个）
            Thread.sleep(2500);

            // 可以再次突发通过
            for (int i = 0; i < 5; i++) {
                assertTrue(tokenBucketLimiter.allowWithParams(key, capacity, rate),
                    "补充后突发请求 " + (i + 1));
            }
        }
    }

    @Nested
    @DisplayName("多维度限流测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class MultiDimensionTests {

        @Test
        @Order(1)
        @DisplayName("不同 key 之间互不影响")
        void differentKeysShouldNotAffectEachOther() {
            String key1 = "dim:sliding:user1:" + System.currentTimeMillis();
            String key2 = "dim:sliding:user2:" + System.currentTimeMillis();
            long limit = 2;
            long window = 60;

            assertTrue(slidingWindowLimiter.allow(key1, limit, window));
            assertTrue(slidingWindowLimiter.allow(key1, limit, window));
            assertFalse(slidingWindowLimiter.allow(key1, limit, window));

            // key2 完全独立
            assertTrue(slidingWindowLimiter.allow(key2, limit, window));
            assertTrue(slidingWindowLimiter.allow(key2, limit, window));
            assertFalse(slidingWindowLimiter.allow(key2, limit, window));
        }
    }
}