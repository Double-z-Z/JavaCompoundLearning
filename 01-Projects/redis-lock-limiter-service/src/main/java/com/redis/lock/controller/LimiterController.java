package com.redis.lock.controller;

import com.redis.lock.limiter.FixedWindowLimiter;
import com.redis.lock.limiter.SlidingWindowLimiter;
import com.redis.lock.limiter.TokenBucketLimiter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 限流器 REST API
 */
@RestController
@RequestMapping("/api/limiter")
public class LimiterController {

    private final SlidingWindowLimiter slidingWindowLimiter;
    private final FixedWindowLimiter fixedWindowLimiter;
    private final TokenBucketLimiter tokenBucketLimiter;

    public LimiterController(StringRedisTemplate redisTemplate) {
        this.slidingWindowLimiter = new SlidingWindowLimiter(redisTemplate);
        this.fixedWindowLimiter = new FixedWindowLimiter(redisTemplate);
        this.tokenBucketLimiter = new TokenBucketLimiter(redisTemplate);
    }

    /**
     * 滑动窗口限流检查
     * POST /api/limiter/sliding
     * Body: {"key": "user:123", "limit": 100, "windowSeconds": 60}
     */
    @PostMapping("/sliding")
    public Mono<Map<String, Object>> checkSlidingWindow(@RequestBody Map<String, Object> request) {
        String key = (String) request.get("key");
        Number limitNum = (Number) request.get("limit");
        Number windowNum = (Number) request.get("windowSeconds");

        long limit = limitNum != null ? limitNum.longValue() : 100;
        long window = windowNum != null ? windowNum.longValue() : 60;

        boolean allowed = slidingWindowLimiter.allow(key, limit, window);
        long remaining = slidingWindowLimiter.remaining(key, window);

        return Mono.just(Map.of(
            "allowed", allowed,
            "remaining", remaining,
            "resetAt", System.currentTimeMillis() / 1000 + window,
            "algorithm", "sliding_window"
        ));
    }

    /**
     * 固定窗口限流检查
     * POST /api/limiter/fixed
     * Body: {"key": "user:123", "limit": 100, "windowSeconds": 60}
     */
    @PostMapping("/fixed")
    public Mono<Map<String, Object>> checkFixedWindow(@RequestBody Map<String, Object> request) {
        String key = (String) request.get("key");
        Number limitNum = (Number) request.get("limit");
        Number windowNum = (Number) request.get("windowSeconds");

        int limit = limitNum != null ? limitNum.intValue() : 100;
        int window = windowNum != null ? windowNum.intValue() : 60;

        boolean allowed = fixedWindowLimiter.allowWithTimeSlice(key, limit, window);
        long remaining = fixedWindowLimiter.remaining(key, window);

        return Mono.just(Map.of(
            "allowed", allowed,
            "remaining", remaining,
            "resetAt", System.currentTimeMillis() / 1000 + window,
            "algorithm", "fixed_window"
        ));
    }

    /**
     * 令牌桶限流检查
     * POST /api/limiter/token
     * Body: {"key": "user:123", "capacity": 100, "ratePerSec": 10}
     */
    @PostMapping("/token")
    public Mono<Map<String, Object>> checkTokenBucket(@RequestBody Map<String, Object> request) {
        String key = (String) request.get("key");
        Number capacityNum = (Number) request.get("capacity");
        Number rateNum = (Number) request.get("ratePerSec");

        long capacity = capacityNum != null ? capacityNum.longValue() : 100;
        long rate = rateNum != null ? rateNum.longValue() : 10;

        boolean allowed = tokenBucketLimiter.allowWithParams(key, capacity, rate);
        long remaining = tokenBucketLimiter.remaining(key, 0);

        return Mono.just(Map.of(
            "allowed", allowed,
            "remaining", remaining,
            "algorithm", "token_bucket"
        ));
    }

    /**
     * 统一限流检查接口（默认滑动窗口）
     * POST /api/limiter/check
     * Body: {"key": "user:123", "limit": 100, "windowSeconds": 60}
     */
    @PostMapping("/check")
    public Mono<Map<String, Object>> checkLimit(@RequestBody Map<String, Object> request) {
        String algorithm = (String) request.getOrDefault("algorithm", "sliding");

        return switch (algorithm) {
            case "fixed" -> checkFixedWindow(request);
            case "token" -> checkTokenBucket(request);
            default -> checkSlidingWindow(request);
        };
    }
}