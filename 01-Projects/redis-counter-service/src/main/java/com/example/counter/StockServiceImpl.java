package com.example.counter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 库存服务实现 - 基于 Redis Lua 脚本保证原子性
 */
@Service
public class StockServiceImpl implements StockService {

    private final StringRedisTemplate redisTemplate;

    // Lua 脚本：库存扣减（原子操作，防止超卖）
    // 返回值：剩余库存 或 -1（库存不足）
    private static final String DECREMENT_SCRIPT = """
            local stock = tonumber(redis.call('GET', KEYS[1]) or 0)
            local quantity = tonumber(ARGV[1])
            if stock >= quantity then
                return redis.call('DECRBY', KEYS[1], quantity)
            else
                return -1
            end
            """;

    public StockServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void initStock(String sku, long quantity) {
        redisTemplate.opsForValue().set("stock:" + sku, String.valueOf(quantity));
    }

    @Override
    public Long decrementStock(String sku, long quantity) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(DECREMENT_SCRIPT, Long.class);
        return redisTemplate.execute(
                script,
                Collections.singletonList("stock:" + sku),
                String.valueOf(quantity)
        );
    }

    @Override
    public Long getStock(String sku) {
        String value = redisTemplate.opsForValue().get("stock:" + sku);
        return value != null ? Long.parseLong(value) : 0L;
    }
}