package com.example.review;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.example.review.counter.AtomicCounter;
import com.example.review.counter.Counter;
import com.example.review.counter.LongAdderCounter;

import java.util.concurrent.ArrayBlockingQueue;

public class CounterTest {

    private ExecutorService executorService;

    CounterTest() {
        executorService = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(5));
    }

    public static void main(String[] args) {
        testAtomicCounter(args);
        testLongAdderCounter(args);
    }
    
    public static void testAtomicCounter(String[] args) {
        CounterTest counterTest = new CounterTest();
        long startTime = System.currentTimeMillis();
        long result = counterTest.startTest(new AtomicCounter(), 8, 1000_000);
        long endTime = System.currentTimeMillis();
        System.out.println("最终计数：" + result);
        System.out.println("AtomicLong计数器——Time cost: " + (endTime - startTime) + " ms");
    }

    public static void testLongAdderCounter(String[] args) {
        CounterTest counterTest = new CounterTest();
        long startTime = System.currentTimeMillis();
        long result = counterTest.startTest(new LongAdderCounter(8), 8, 1000_000);
        long endTime = System.currentTimeMillis();
        System.out.println("最终计数：" + result);
        System.out.println("LongAdder计数器——Time cost: " + (endTime - startTime) + " ms");
    }

    public long startTest(Counter counter, int threadCount, int addCountPerThread) {
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                for (int j = 0; j < addCountPerThread; j++) {
                    counter.increment();
                }
            });
        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return counter.get();
    }
}
