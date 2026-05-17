package com.redis.lock.limiter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import java.util.List;

/**
 * 滑动窗口限流器 (Sliding Window Log)
 *
 * 算法：使用 Redis Sorted Set 存储请求时间戳，每次请求清理窗口外的数据，统计窗口内数量决定是否放行
 * 特点：精确限流，无边界穿透，但不支持突发流量
 */
public class SlidingWindowLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;

    private static final String LUA_SCRIPT = """
        local key = KEYS[1]
        local now = tonumber(ARGV[1])
        local window = tonumber(ARGV[2])
        local limit = tonumber(ARGV[3])
        local member = ARGV[4]

        local window_start = now - window

        -- 清理窗口外的旧记录
        redis.call('ZREMRANGEBYSCORE', key, 0, window_start)

        -- 获取当前窗口内的请求数
        local current = redis.call('ZCARD', key)

        if current < limit then
            -- 允许通过，添加当前请求
            redis.call('ZADD', key, now, member)
            -- 延长 key 过期时间，防止冷 key 残留
            redis.call('PEXPIRE', key, window)
            return {1, current + 1, limit - current - 1}
        else
            return {0, current, 0}
        end
        """;

    private final DefaultRedisScript<List> script;

    public SlidingWindowLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(LUA_SCRIPT, List.class);
    }

    @Override
    public boolean allow(String key, long limit, long windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000;
        String redisKey = "limiter:sliding:" + key;
        String member = now + ":" + (int)(Math.random() * 1000000);

        List<Long> result = redisTemplate.execute(script,
            java.util.Collections.singletonList(redisKey),
            String.valueOf(now), String.valueOf(windowMs), String.valueOf(limit), member);

        return result != null && result.get(0) == 1;
    }

    @Override
    public long remaining(String key, long windowSeconds) {
        long now = System.currentTimeMillis();
        long windowStart = now - (windowSeconds * 1000);
        String redisKey = "limiter:sliding:" + key;

        redisTemplate.opsForZSet().removeRange(redisKey, 0, windowStart);
        Long count = redisTemplate.opsForZSet().zCard(redisKey);

        return count != null ? count : 0;
    }

    /**
     * 获取当前窗口内的请求数
     */
    public long currentCount(String key, long windowSeconds) {
        long now = System.currentTimeMillis();
        long windowStart = now - (windowSeconds * 1000);
        String redisKey = "limiter:sliding:" + key;

        redisTemplate.opsForZSet().removeRange(redisKey, 0, windowStart);
        Long count = redisTemplate.opsForZSet().zCard(redisKey);

        return count != null ? count : 0;
    }
}