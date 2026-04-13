package com.example.concurrency;

import com.example.concurrency.queue.TaskScheduler;

/**
 * 任务调度器测试
 */
public class TaskSchedulerTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== 任务调度器测试 ===\n");

        // 配置：3个生产者，5个消费者，队列容量100，每个生产者每秒生产10个任务
        TaskScheduler scheduler = new TaskScheduler(3, 5, 100, 10);

        // 启动调度器
        scheduler.start();

        // 运行10秒后关闭
        Thread.sleep(10000);

        // 优雅关闭
        scheduler.shutdown();

        System.out.println("\n=== 测试完成 ===");
    }
}
