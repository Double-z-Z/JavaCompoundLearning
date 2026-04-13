package com.example.review.counter;

import java.util.concurrent.atomic.AtomicLong;

public class LongAdderCounter implements Counter {

    private AtomicLong[] counters;

    private long segmentCount;

    public LongAdderCounter(int segmentCount) { 
        this.segmentCount = segmentCount;
        this.counters = new AtomicLong[segmentCount]; 
        for (int i = 0; i < segmentCount; i++) {
            counters[i] = new AtomicLong(0);
        }
    }

    @Override
    public void increment() {
        Thread thread = Thread.currentThread();
        long segmentIndex = thread.getId() % segmentCount;
        counters[(int) segmentIndex].incrementAndGet();
    }

    @Override
    public long get() {
        long result = 0;
        for (AtomicLong counter : counters) {
            result += counter.get();
        }
        return result;
    }
    
}
