package com.example.concurrency.counter;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 分段计数器实现（进阶）
 * 
 * 知识点：
 * 1. 类似ConcurrentHashMap的分段锁思想
 * 2. 每个线程操作不同的counter，减少竞争
 * 3. 最后汇总时求和
 * 4. 适合极高并发场景
 * 
 * 对比：
 * - AtomicLong：单个变量，所有线程竞争同一个CAS
 * - LongAdder：分段累加，最后汇总（Java 8推荐）
 * - 本实现：手动分段，理解原理
 */
public class StripedCounter implements Counter {
    
    private final AtomicLong[] counters;
    private final int stripes;
    private final String name;
    
    // 使用ThreadLocal来分配线程到不同的槽位
    private final ThreadLocal<Integer> threadIndex;
    
    public StripedCounter(String name, int stripes) {
        this.name = name;
        this.stripes = stripes;
        this.counters = new AtomicLong[stripes];
        for (int i = 0; i < stripes; i++) {
            counters[i] = new AtomicLong(0);
        }
        this.threadIndex = ThreadLocal.withInitial(() -> {
            // 简单哈希分配，实际可使用更好的分配策略
            return (int) (Thread.currentThread().getId() % stripes);
        });
    }
    
    @Override
    public void increment() {
        int index = threadIndex.get();
        counters[index].incrementAndGet();
    }
    
    @Override
    public long getCount() {
        long sum = 0;
        for (AtomicLong counter : counters) {
            sum += counter.get();
        }
        return sum;
    }
    
    @Override
    public void reset() {
        for (AtomicLong counter : counters) {
            counter.set(0);
        }
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    /**
     * 获取每个分段的计数值（用于调试和分析）
     */
    public long[] getStripedCounts() {
        long[] result = new long[stripes];
        for (int i = 0; i < stripes; i++) {
            result[i] = counters[i].get();
        }
        return result;
    }
}
