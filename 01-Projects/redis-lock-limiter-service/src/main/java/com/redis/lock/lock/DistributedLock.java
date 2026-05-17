package com.redis.lock.lock;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁接口
 */
public interface DistributedLock {

    /**
     * 尝试获取锁（阻塞直到获取或放弃）
     * @param key 锁 key
     * @param ttl 过期时间
     * @return 是否获取成功
     */
    boolean lock(String key, long ttl);

    /**
     * 尝试获取锁（阻塞等待）
     * @param key 锁 key
     * @param ttl 过期时间
     * @param waitTime 最大等待时间
     * @return 是否获取成功
     */
    boolean tryLock(String key, long ttl, long waitTime);

    /**
     * 释放锁
     * @param key 锁 key
     * @return 是否释放成功
     */
    boolean unlock(String key);

    /**
     * 检查锁是否被持有
     * @param key 锁 key
     * @return 是否被持有
     */
    boolean isLocked(String key);
}