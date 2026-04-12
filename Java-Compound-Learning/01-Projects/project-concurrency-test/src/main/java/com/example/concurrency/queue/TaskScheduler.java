package com.example.concurrency.queue;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 任务调度器 - 生产者消费者模型实现
 *
 * 知识点：
 * 1. BlockingQueue的使用（put/take）
 * 2. 线程池的合理配置
 * 3. 优雅关闭线程池
 * 4. 监控指标统计
 *
 * 需求：
 * - 3个生产者线程，每秒生产10个任务
 * - 5个消费者线程，处理任务（模拟耗时100ms）
 * - 任务队列大小限制为100
 * - 支持优雅关闭
 */
public class TaskScheduler {

    private final BlockingQueue<Task> taskQueue;
    private final ExecutorService producerPool;
    private final ExecutorService consumerPool;
    private volatile boolean running = false;

    // 监控指标
    private final AtomicInteger producedCount = new AtomicInteger(0);
    private final AtomicInteger consumedCount = new AtomicInteger(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);

    // 配置参数
    private final int producerCount;
    private final int consumerCount;
    private final int queueCapacity;
    private final int produceRatePerSecond; // 每个生产者每秒生产任务数

    public TaskScheduler(int producerCount, int consumerCount, int queueCapacity, int produceRatePerSecond) {
        this.producerCount = producerCount;
        this.consumerCount = consumerCount;
        this.queueCapacity = queueCapacity;
        this.produceRatePerSecond = produceRatePerSecond;

        // 使用有界队列，防止OOM
        this.taskQueue = new ArrayBlockingQueue<>(queueCapacity);

        // 生产者线程池
        this.producerPool = Executors.newFixedThreadPool(producerCount, r -> {
            Thread t = new Thread(r);
            t.setName("Producer-" + t.getId());
            return t;
        });

        // 消费者线程池
        this.consumerPool = Executors.newFixedThreadPool(consumerCount, r -> {
            Thread t = new Thread(r);
            t.setName("Consumer-" + t.getId());
            return t;
        });
    }

    /**
     * 启动调度器
     */
    public void start() {
        if (running) {
            throw new IllegalStateException("调度器已在运行");
        }
        running = true;

        // 启动生产者
        for (int i = 0; i < producerCount; i++) {
            producerPool.submit(new Producer());
        }

        // 启动消费者
        for (int i = 0; i < consumerCount; i++) {
            consumerPool.submit(new Consumer());
        }

        System.out.println("任务调度器已启动");
        System.out.println("生产者数量: " + producerCount);
        System.out.println("消费者数量: " + consumerCount);
        System.out.println("队列容量: " + queueCapacity);
        System.out.println("生产速率: " + produceRatePerSecond + " 任务/秒/生产者");
    }

    /**
     * 优雅关闭调度器
     */
    public void shutdown() {
        if (!running) {
            return;
        }
        running = false;

        System.out.println("正在关闭调度器...");

        // 先停止生产者（不再提交新任务）
        producerPool.shutdown();
        try {
            if (!producerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                producerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            producerPool.shutdownNow();
        }

        // 等待队列中的任务被消费完
        while (!taskQueue.isEmpty()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 停止消费者
        consumerPool.shutdown();
        try {
            if (!consumerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                consumerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            consumerPool.shutdownNow();
        }

        System.out.println("调度器已关闭");
        printStatistics();
    }

    /**
     * 提交单个任务（外部提交）
     */
    public boolean submit(Task task) {
        if (!running) {
            throw new IllegalStateException("调度器未运行");
        }
        try {
            // 使用offer带超时，避免无限阻塞
            boolean offered = taskQueue.offer(task, 1, TimeUnit.SECONDS);
            if (offered) {
                producedCount.incrementAndGet();
            }
            return offered;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 获取当前队列大小
     */
    public int getQueueSize() {
        return taskQueue.size();
    }

    /**
     * 获取队列剩余容量
     */
    public int getRemainingCapacity() {
        return taskQueue.remainingCapacity();
    }

    /**
     * 打印统计信息
     */
    public void printStatistics() {
        System.out.println("\n=== 统计信息 ===");
        System.out.println("已生产任务数: " + producedCount.get());
        System.out.println("已消费任务数: " + consumedCount.get());
        System.out.println("当前队列大小: " + getQueueSize());

        int consumed = consumedCount.get();
        if (consumed > 0) {
            long avgProcessingTime = totalProcessingTime.get() / consumed;
            System.out.println("平均处理时间: " + avgProcessingTime + " ms");
        }
    }

    /**
     * 生产者线程
     */
    private class Producer implements Runnable {
        @Override
        public void run() {
            while (running) {
                try {
                    // 控制生产速率
                    for (int i = 0; i < produceRatePerSecond && running; i++) {
                        Task task = createTask();

                        // 使用put会阻塞，如果队列满了
                        // 使用offer可以立即返回，这里演示put的用法
                        taskQueue.put(task);
                        producedCount.incrementAndGet();

                        System.out.println(Thread.currentThread().getName() + " 生产任务: " + task.getTaskId() +
                                " [队列: " + getQueueSize() + "/" + queueCapacity + "]");
                    }

                    // 每秒生产一批
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.println(Thread.currentThread().getName() + " 生产者停止");
        }

        private Task createTask() {
            // 创建随机优先级的任务
            int priority = (int) (Math.random() * 5) + 1; // 1-5
            return new SimpleTask(priority, 100, () -> {
                System.out.println(Thread.currentThread().getName() + " 处理任务完成");
            });
        }
    }

    /**
     * 消费者线程
     */
    private class Consumer implements Runnable {
        @Override
        public void run() {
            while (running || !taskQueue.isEmpty()) {
                try {
                    // take会阻塞，直到有任务可用
                    Task task = taskQueue.poll(1, TimeUnit.SECONDS);

                    if (task != null) {
                        long startTime = System.currentTimeMillis();

                        System.out.println(Thread.currentThread().getName() + " 开始处理任务: " + task.getTaskId());
                        task.execute();

                        long processingTime = System.currentTimeMillis() - startTime;
                        totalProcessingTime.addAndGet(processingTime);
                        consumedCount.incrementAndGet();

                        System.out.println(Thread.currentThread().getName() + " 完成任务: " + task.getTaskId() +
                                " [耗时: " + processingTime + "ms] [队列剩余: " + getQueueSize() + "]");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.println(Thread.currentThread().getName() + " 消费者停止");
        }
    }
}
