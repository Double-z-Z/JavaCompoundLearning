package com.example.counter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 计数器服务实现类
 * 基于 Redis String 类型实现高性能计数器
 */
@Service
public class CounterServiceImpl implements CounterService {
    
    private final StringRedisTemplate redisTemplate;
    
    public CounterServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public Long incr(String key) {
        return redisTemplate.opsForValue().increment(key);
    }
    
    @Override
    public Long incrBy(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }
    
    @Override
    public Long decr(String key) {
        return redisTemplate.opsForValue().decrement(key);
    }
    
    @Override
    public Long decrBy(String key, long delta) {
        return redisTemplate.opsForValue().decrement(key, delta);
    }
    
    @Override
    public Long get(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : 0L;
    }
    
    @Override
    public void set(String key, long value) {
        redisTemplate.opsForValue().set(key, String.valueOf(value));
    }
    
    @Override
    public void set(String key, long value, long expireSeconds) {
        redisTemplate.opsForValue().set(
            key, 
            String.valueOf(value), 
            expireSeconds, 
            TimeUnit.SECONDS
        );
    }
    
    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }
}
