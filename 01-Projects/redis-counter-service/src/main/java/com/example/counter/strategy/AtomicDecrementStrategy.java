package com.example.counter.strategy;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 原子扣减策略 - Lua 脚本保证原子性
 * 适用于日常运营模式，0 超卖风险
 */
@Component
public class AtomicDecrementStrategy implements DecrementStrategy {

    private final StringRedisTemplate redisTemplate;

    // Lua 脚本：库存扣减（原子操作，防止超卖）
    private static final String DECREMENT_SCRIPT = """
            local stock = tonumber(redis.call('GET', KEYS[1]) or 0)
            local quantity = tonumber(ARGV[1])
            if stock >= quantity then
                return redis.call('DECRBY', KEYS[1], quantity)
            else
                return -1
            end
            """;

    public AtomicDecrementStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String getName() {
        return "atomic";
    }

    @Override
    public Long decrement(String sku, long quantity) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(DECREMENT_SCRIPT, Long.class);
        return redisTemplate.execute(
                script,
                Collections.singletonList("stock:" + sku),
                String.valueOf(quantity)
        );
    }

    @Override
    public List<Long> batchDecrement(String sku, List<Long> quantities) {
        // 原子策略：每次独立 RTT，保证严格一致性
        return quantities.stream()
                .map(q -> decrement(sku, q))
                .toList();
    }
}
