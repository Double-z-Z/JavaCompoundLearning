package com.example.review.counter;

import java.util.concurrent.atomic.AtomicLong;

public class AtomicCounter implements Counter {
    private AtomicLong counter;
    
    public AtomicCounter() {
        this.counter = new AtomicLong(0);
    }

    public void increment() {
        counter.incrementAndGet();
    }

    public long get() {
        return counter.get();
    }
}