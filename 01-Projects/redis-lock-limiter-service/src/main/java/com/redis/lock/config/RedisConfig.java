package com.redis.lock.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis 集群配置 - 连接到真实 Redis 集群
 * 使用 ansible-redis-cluster 部署的集群
 */
@Configuration
public class RedisConfig {

    /**
     * Redis 集群节点列表
     * 从 ansible-redis-cluster/inventory/hosts.ini 获取
     */
    private static final String[] REDIS_NODES = {
        "10.0.0.102:6379",
        "10.0.0.103:6379",
        "10.0.0.104:6379",
        "10.0.0.105:6379",
        "10.0.0.106:6379",
        "10.0.0.107:6379"
    };

    @Bean
    public StringRedisTemplate stringRedisTemplate() {
        List<RedisNode> nodes = new ArrayList<>();

        for (String addr : REDIS_NODES) {
            String[] parts = addr.split(":");
            nodes.add(new RedisNode(parts[0], Integer.parseInt(parts[1])));
        }

        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration();
        clusterConfig.setClusterNodes(nodes);

        LettuceConnectionFactory factory = new LettuceConnectionFactory(clusterConfig);
        factory.afterPropertiesSet();

        return new StringRedisTemplate(factory);
    }
}