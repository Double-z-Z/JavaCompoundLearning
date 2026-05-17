package com.redis.lock.limiter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.List;

/**
 * 固定窗口限流器 (Fixed Window)
 *
 * 算法：将时间划分为固定窗口（如每分钟），每个窗口独立计数
 * 特点：实现简单，性能高，但有边界穿透问题
 */
public class FixedWindowLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;

    private static final String LUA_SCRIPT = """
        local key = KEYS[1]
        local limit = tonumber(ARGV[1])
        local window_sec = tonumber(ARGV[2])

        local current = redis.call('INCR', key)

        -- 只有第一次创建 key 时才设置过期
        if current == 1 then
            redis.call('EXPIRE', key, window_sec)
        end

        if current <= limit then
            return {1, current, limit - current}
        else
            return {0, current, 0}
        end
        """;

    private final DefaultRedisScript<List> script;

    public FixedWindowLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(LUA_SCRIPT, List.class);
    }

    /**
     * 生成时间片对齐的 key
     * @param prefix 前缀，如 "user:12345"
     * @param windowSeconds 窗口大小（秒）
     */
    public String makeKey(String prefix, int windowSeconds) {
        long epochSec = System.currentTimeMillis() / 1000;
        long timeSlice = epochSec / windowSeconds;
        return String.format("limiter:fixed:%s:%d", prefix, timeSlice);
    }

    @Override
    public boolean allow(String key, long limit, long windowSeconds) {
        String redisKey = "limiter:fixed:" + key;

        List<Long> result = redisTemplate.execute(script,
            List.of(redisKey),
            String.valueOf(limit), String.valueOf(windowSeconds));

        return result != null && result.get(0) == 1;
    }

    /**
     * 使用时间片 key 的 allow 方法
     */
    public boolean allowWithTimeSlice(String key, long limit, int windowSeconds) {
        String redisKey = makeKey(key, windowSeconds);

        List<Long> result = redisTemplate.execute(script,
            List.of(redisKey),
            String.valueOf(limit), String.valueOf(windowSeconds));

        return result != null && result.get(0) == 1;
    }

    @Override
    public long remaining(String key, long windowSeconds) {
        String redisKey = "limiter:fixed:" + key;
        String value = redisTemplate.opsForValue().get(redisKey);
        if (value == null) {
            return windowSeconds;
        }
        long current = Long.parseLong(value);
        return Math.max(0, windowSeconds - current);
    }
}