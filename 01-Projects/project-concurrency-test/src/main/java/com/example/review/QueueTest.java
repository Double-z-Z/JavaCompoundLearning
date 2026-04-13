package com.example.review;

import com.example.review.queue.BlockQueueScheduler;

public class QueueTest {
    public static void main(String[] args) {
        testBlockQueueScheduler(args);
    }

    public static void testBlockQueueScheduler(String[] args) {
        BlockQueueScheduler scheduler = new BlockQueueScheduler(2, 2);
        scheduler.start();
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        scheduler.shutdown();
    }
}
