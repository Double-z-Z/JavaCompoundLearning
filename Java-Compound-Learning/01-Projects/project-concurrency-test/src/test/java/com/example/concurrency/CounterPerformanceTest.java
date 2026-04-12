package com.example.concurrency;

import com.example.concurrency.counter.AtomicCounter;
import com.example.concurrency.counter.Counter;
import com.example.concurrency.counter.StripedCounter;
import com.example.concurrency.counter.SynchronizedCounter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 计数器性能测试
 * 
 * 测试目标：
 * 1. 验证各种计数器实现的正确性
 * 2. 对比不同实现的性能差异
 * 3. 理解synchronized、CAS、分段锁的区别
 * 
 * 测试场景：
 * - 10个线程，每个线程递增100万次
 * - 期望总时间 < 1秒（AtomicCounter）
 */
public class CounterPerformanceTest {
    
    private static final int THREAD_COUNT = 10;
    private static final int INCREMENTS_PER_THREAD = 1_000_000;
    private static final int TOTAL_INCREMENTS = THREAD_COUNT * INCREMENTS_PER_THREAD;
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== 计数器性能测试 ===");
        System.out.println("线程数: " + THREAD_COUNT);
        System.out.println("每线程递增次数: " + INCREMENTS_PER_THREAD);
        System.out.println("总递增次数: " + TOTAL_INCREMENTS);
        System.out.println();
        
        // 测试各种计数器实现
        testCounter(new SynchronizedCounter("SynchronizedCounter"));
        testCounter(new AtomicCounter("AtomicCounter"));
        testCounter(new StripedCounter("StripedCounter-4", 4));
        testCounter(new StripedCounter("StripedCounter-16", 16));
        
        System.out.println("\n=== 测试完成 ===");
    }
    
    private static void testCounter(Counter counter) throws Exception {
        System.out.println("测试: " + counter.getName());
        
        // 重置计数器
        counter.reset();
        
        // 使用CountDownLatch等待所有线程完成
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        
        long startTime = System.currentTimeMillis();
        
        // 提交任务
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < INCREMENTS_PER_THREAD; j++) {
                        counter.increment();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有线程完成
        latch.await();
        long endTime = System.currentTimeMillis();
        
        executor.shutdown();
        
        // 验证结果
        long finalCount = counter.getCount();
        boolean isCorrect = (finalCount == TOTAL_INCREMENTS);
        long duration = endTime - startTime;
        
        System.out.println("  结果: " + (isCorrect ? "✓ 正确" : "✗ 错误"));
        System.out.println("  期望值: " + TOTAL_INCREMENTS + ", 实际值: " + finalCount);
        System.out.println("  耗时: " + duration + " ms");
        System.out.println("  吞吐量: " + (TOTAL_INCREMENTS / (duration / 1000.0)) + " ops/sec");
        
        // 如果是分段计数器，显示分布情况
        if (counter instanceof StripedCounter) {
            StripedCounter striped = (StripedCounter) counter;
            long[] stripedCounts = striped.getStripedCounts();
            System.out.print("  分段分布: ");
            for (int i = 0; i < stripedCounts.length; i++) {
                System.out.print("[" + i + "]=" + stripedCounts[i] + " ");
            }
            System.out.println();
        }
        
        System.out.println();
        
        // 性能断言（AtomicCounter应该在1秒内完成）
        if (counter instanceof AtomicCounter && duration > 1000) {
            System.out.println("  ⚠ 警告: AtomicCounter耗时超过1秒，性能不达标！");
        }
    }
}
