package com.example.concurrency.counter;

/**
 * 计数器接口
 * 所有计数器实现都需要遵循此接口
 */
public interface Counter {
    
    /**
     * 递增计数器
     */
    void increment();
    
    /**
     * 获取当前计数值
     * @return 当前计数值
     */
    long getCount();
    
    /**
     * 重置计数器为0
     */
    void reset();
    
    /**
     * 获取计数器名称
     * @return 计数器名称
     */
    String getName();
}
