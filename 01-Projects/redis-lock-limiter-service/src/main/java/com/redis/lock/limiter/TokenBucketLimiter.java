package com.redis.lock.limiter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.List;

/**
 * 令牌桶限流器 (Token Bucket)
 *
 * 算法：维护桶内令牌数，按固定速率补充，请求消耗令牌
 * 特点：支持突发流量，桶满后按速率平滑放行
 */
public class TokenBucketLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;

    private static final String LUA_SCRIPT = """
        local key = KEYS[1]
        local capacity = tonumber(ARGV[1])
        local rate_per_sec = tonumber(ARGV[2])
        local requested = tonumber(ARGV[3])
        local now_ms = tonumber(ARGV[4])

        -- 获取状态
        local state = redis.call('HMGET', key, 'tokens', 'last_time_ms')
        local tokens = tonumber(state[1])
        local last_time_ms = tonumber(state[2])

        -- 初始化
        if tokens == nil then
            tokens = capacity
            last_time_ms = now_ms
        end

        -- 按时间差填充令牌
        local delta_ms = now_ms - last_time_ms
        local fill = (delta_ms / 1000) * rate_per_sec
        tokens = math.min(capacity, tokens + fill)

        if tokens >= requested then
            tokens = tokens - requested
            redis.call('HMSET', key, 'tokens', tokens, 'last_time_ms', now_ms)
            redis.call('EXPIRE', key, 60)
            return {1, math.floor(tokens)}
        else
            redis.call('HMSET', key, 'tokens', tokens, 'last_time_ms', now_ms)
            redis.call('EXPIRE', key, 60)
            return {0, math.floor(tokens)}
        end
        """;

    private final DefaultRedisScript<List> script;

    public TokenBucketLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(LUA_SCRIPT, List.class);
    }

    @Override
    public boolean allow(String key, long limit, long windowSeconds) {
        long capacity = limit;
        long ratePerSec = limit / windowSeconds;
        if (ratePerSec < 1) ratePerSec = 1;

        String redisKey = "limiter:token:" + key;
        long nowMs = System.currentTimeMillis();

        List<Long> result = redisTemplate.execute(script,
            List.of(redisKey),
            String.valueOf(capacity), String.valueOf(ratePerSec), "1", String.valueOf(nowMs));

        return result != null && result.get(0) == 1;
    }

    /**
     * 自定义令牌桶参数
     * @param key 限流 key
     * @param capacity 桶容量（最大令牌数）
     * @param ratePerSec 每秒补充令牌数
     */
    public boolean allowWithParams(String key, long capacity, long ratePerSec) {
        String redisKey = "limiter:token:" + key;
        long nowMs = System.currentTimeMillis();

        List<Long> result = redisTemplate.execute(script,
            List.of(redisKey),
            String.valueOf(capacity), String.valueOf(ratePerSec), "1", String.valueOf(nowMs));

        return result != null && result.get(0) == 1;
    }

    @Override
    public long remaining(String key, long windowSeconds) {
        String redisKey = "limiter:token:" + key;
        Object tokensObj = redisTemplate.opsForHash().get(redisKey, "tokens");
        if (tokensObj == null) {
            return windowSeconds;
        }
        return Long.parseLong(tokensObj.toString());
    }
}