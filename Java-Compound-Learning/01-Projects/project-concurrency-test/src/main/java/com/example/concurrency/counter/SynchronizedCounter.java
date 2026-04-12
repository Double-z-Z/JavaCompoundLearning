package com.example.concurrency.counter;

/**
 * 基于synchronized的计数器实现
 * 
 * 知识点：
 * 1. synchronized关键字保证原子性和可见性
 * 2. 锁粒度是整个对象，性能相对较低
 * 3. 适合低并发场景
 */
public class SynchronizedCounter implements Counter {
    
    private long count = 0;
    private final String name;
    
    public SynchronizedCounter(String name) {
        this.name = name;
    }
    
    @Override
    public synchronized void increment() {
        count++;
    }
    
    @Override
    public synchronized long getCount() {
        return count;
    }
    
    @Override
    public synchronized void reset() {
        count = 0;
    }
    
    @Override
    public String getName() {
        return name;
    }
}
