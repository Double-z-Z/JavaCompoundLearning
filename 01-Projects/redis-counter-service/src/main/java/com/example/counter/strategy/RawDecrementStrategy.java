package com.example.counter.strategy;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 原始扣减策略 - 直接使用 DECRBY，不执行 Lua 脚本
 * 用于性能对比测试，验证瓶颈位置
 *
 * 注意：此策略不保证原子性，可能出现超卖
 */
@Component
public class RawDecrementStrategy implements DecrementStrategy {

    private final StringRedisTemplate redisTemplate;

    public RawDecrementStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String getName() {
        return "raw";
    }

    @Override
    public Long decrement(String sku, long quantity) {
        return redisTemplate.opsForValue().decrement("stock:" + sku, quantity);
    }

    @Override
    public List<Long> batchDecrement(String sku, List<Long> quantities) {
        // 批量场景下退化为串行执行
        List<Long> responses = new ArrayList<>();
        for (Long quantity : quantities) {
            responses.add(decrement(sku, quantity));
        }
        return responses;
    }
}
