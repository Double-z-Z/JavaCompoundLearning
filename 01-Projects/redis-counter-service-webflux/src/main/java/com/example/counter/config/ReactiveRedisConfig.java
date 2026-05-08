package com.example.counter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * WebFlux Redis 配置
 * 使用响应式 RedisTemplate
 */
@Configuration
public class ReactiveRedisConfig {

    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveStringRedisTemplate(connectionFactory);
    }

    /**
     * Lua 脚本：库存扣减（原子操作，防止超卖）
     */
    @Bean
    public RedisScript<Long> decrementScript() {
        String script = """
            local stock = tonumber(redis.call('GET', KEYS[1]) or 0)
            local quantity = tonumber(ARGV[1])
            if stock >= quantity then
                return redis.call('DECRBY', KEYS[1], quantity)
            else
                return -1
            end
            """;
        return new DefaultRedisScript<>(script, Long.class);
    }
}