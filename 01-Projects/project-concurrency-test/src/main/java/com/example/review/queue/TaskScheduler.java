package com.example.review.queue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public abstract class TaskScheduler {
    private final ExecutorService producerPool;
    private final ExecutorService consumerPool;

    /**
     * 监控指标
     */
    private final AtomicLong consumedCount = new AtomicLong(0);

    /**
     * 调度器参数配置
     */
    private final int producerCount;
    private final int consumerCount;

    private volatile boolean isRunning;

    TaskScheduler(int producerCount, int consumerCount) {
        this.producerPool = Executors.newFixedThreadPool(producerCount);
        this.consumerPool = Executors.newFixedThreadPool(consumerCount);
        this.producerCount = producerCount;
        this.consumerCount = consumerCount;
    }

    public void start() {
        isRunning = true;
        for (int i = 0; i < producerCount; i++) {
            producerPool.execute(new Producer(i, 100));
        }

        for (int i = 0; i < consumerCount; i++) {
            consumerPool.execute(new Consumer(i, consumedCount));
        }
    }

    public void shutdown() {
        isRunning = false;
        producerPool.shutdownNow();
        try {
            producerPool.awaitTermination(10, TimeUnit.SECONDS);
            consumerPool.shutdownNow();
            consumerPool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println(e.getMessage());
        }

        System.out.println("BlockQueueScheduler 已关闭，已消费任务数：" + consumedCount.get());
    }

    abstract protected Task acciqureTask() throws InterruptedException;

    abstract protected void releaseTask(Task task) throws InterruptedException;

    class Producer implements Runnable {
        int id;
        int taskPerSecond;

        Producer(int id, int taskPerSecond) {
            this.id = id;
            this.taskPerSecond = taskPerSecond;
        }

        @Override
        public void run() {
            try {
                while (isRunning) {
                    long startTime = System.currentTimeMillis();

                    for (int i = 0; i < taskPerSecond; i++) {
                        releaseTask(new Task("task-" + id + "-" + i));
                    }

                    if (!isRunning) {
                        break;
                    }

                    long endTime = System.currentTimeMillis();

                    Thread.sleep(Math.max(0, 1000 - (endTime - startTime)));
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    class Consumer implements Runnable {
        int id;
        AtomicLong consumedCount;

        Consumer(int id, AtomicLong consumedCount) {
            this.id = id;
            this.consumedCount = consumedCount;
        }

        @Override
        public void run() {
            long currentCount = 0;
            try {
                while (isRunning) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    Task task = acciqureTask();

                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    if (task != null) {
                        task.execute();
                        currentCount++;
                    }

                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                consumedCount.addAndGet(currentCount);
            }
        }
    }
}
