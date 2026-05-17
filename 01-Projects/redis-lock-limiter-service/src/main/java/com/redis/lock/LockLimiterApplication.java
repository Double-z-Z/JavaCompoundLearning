package com.redis.lock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 分布式锁与限流系统
 *
 * REST API 端点：
 * - POST /api/lock/acquire     - 获取锁
 * - POST /api/lock/release     - 释放锁
 * - GET  /api/lock/status/{key} - 查询锁状态
 * - POST /api/limiter/sliding  - 滑动窗口限流
 * - POST /api/limiter/fixed    - 固定窗口限流
 * - POST /api/limiter/token    - 令牌桶限流
 * - POST /api/limiter/check    - 统一限流接口
 */
@SpringBootApplication
public class LockLimiterApplication {

    public static void main(String[] args) {
        SpringApplication.run(LockLimiterApplication.class, args);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}