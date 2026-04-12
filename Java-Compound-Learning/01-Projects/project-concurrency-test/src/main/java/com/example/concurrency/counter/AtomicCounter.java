package com.example.concurrency.counter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于AtomicLong的计数器实现（推荐）
 * 
 * 知识点：
 * 1. CAS（Compare-And-Swap）无锁算法
 * 2. 底层使用Unsafe.compareAndSwapLong
 * 3. 高并发下性能优于synchronized
 * 4. 可能存在ABA问题（本场景不影响）
 */
public class AtomicCounter implements Counter {
    
    private final AtomicLong count;
    private final String name;
    
    public AtomicCounter(String name) {
        this.name = name;
        this.count = new AtomicLong(0);
    }
    
    @Override
    public void increment() {
        // CAS操作：自旋直到成功
        count.incrementAndGet();
    }
    
    @Override
    public long getCount() {
        return count.get();
    }
    
    @Override
    public void reset() {
        count.set(0);
    }
    
    @Override
    public String getName() {
        return name;
    }
}
