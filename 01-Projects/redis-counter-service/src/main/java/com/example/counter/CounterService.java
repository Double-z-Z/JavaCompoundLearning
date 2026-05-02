package com.example.counter;

/**
 * 计数器服务接口
 * 提供基本的计数器操作：自增、自减、获取、设置
 */
public interface CounterService {
    
    /**
     * 计数器自增 1
     * @param key 计数器 key
     * @return 自增后的值
     */
    Long incr(String key);
    
    /**
     * 计数器自增指定值
     * @param key 计数器 key
     * @param delta 增量
     * @return 自增后的值
     */
    Long incrBy(String key, long delta);
    
    /**
     * 计数器自减 1
     * @param key 计数器 key
     * @return 自减后的值
     */
    Long decr(String key);
    
    /**
     * 计数器自减指定值
     * @param key 计数器 key
     * @param delta 减量
     * @return 自减后的值
     */
    Long decrBy(String key, long delta);
    
    /**
     * 获取计数器当前值
     * @param key 计数器 key
     * @return 当前值，如果不存在返回 0
     */
    Long get(String key);
    
    /**
     * 设置计数器值
     * @param key 计数器 key
     * @param value 值
     */
    void set(String key, long value);
    
    /**
     * 设置计数器值并指定过期时间
     * @param key 计数器 key
     * @param value 值
     * @param expireSeconds 过期时间（秒）
     */
    void set(String key, long value, long expireSeconds);
    
    /**
     * 删除计数器
     * @param key 计数器 key
     */
    void delete(String key);
}
