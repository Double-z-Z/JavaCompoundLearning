package com.redis.lock.limiter;

/**
 * 限流器接口
 */
public interface RateLimiter {

    /**
     * 检查是否允许通过
     * @param key 限流 key（如用户ID、IP等）
     * @param limit 最大令牌数/窗口大小
     * @param windowSeconds 时间窗口（秒）
     * @return 是否允许请求通过
     */
    boolean allow(String key, long limit, long windowSeconds);

    /**
     * 限流检查结果
     */
    record LimiterResult(boolean allowed, long remaining, long resetAt) {
        public static LimiterResult allowed(long remaining, long resetAt) {
            return new LimiterResult(true, remaining, resetAt);
        }

        public static LimiterResult denied(long remaining, long retryAfter) {
            return new LimiterResult(false, remaining, retryAfter);
        }
    }

    /**
     * 获取剩余令牌数/请求数
     * @param key 限流 key
     * @param windowSeconds 时间窗口
     * @return 剩余数量
     */
    long remaining(String key, long windowSeconds);
}