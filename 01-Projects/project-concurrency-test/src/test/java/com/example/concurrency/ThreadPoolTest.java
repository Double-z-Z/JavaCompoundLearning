package com.example.concurrency;

import com.example.concurrency.threadpool.SimpleThreadPool;

import java.util.concurrent.TimeUnit;

/**
 * 线程池测试
 */
public class ThreadPoolTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== 简易线程池测试 ===\n");

        // 创建线程池：核心线程2，最大线程4，空闲线程存活10秒，队列容量10
        SimpleThreadPool pool = new SimpleThreadPool(
                2,                      // corePoolSize
                4,                      // maximumPoolSize
                10,                     // keepAliveTime
                TimeUnit.SECONDS,       // timeUnit
                10,                     // queueCapacity
                SimpleThreadPool.ABORT_POLICY  // rejectPolicy
        );

        // 提交20个任务
        System.out.println("提交20个任务...");
        for (int i = 0; i < 20; i++) {
            final int taskId = i;
            try {
                pool.execute(() -> {
                    System.out.println(Thread.currentThread().getName() + " 执行任务 " + taskId);
                    try {
                        Thread.sleep(500); // 模拟任务执行
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    System.out.println(Thread.currentThread().getName() + " 完成任务 " + taskId);
                });
            } catch (Exception e) {
                System.out.println("任务 " + taskId + " 被拒绝: " + e.getMessage());
            }
        }

        // 打印状态
        Thread.sleep(1000);
        pool.printStatus();

        // 等待所有任务完成
        Thread.sleep(5000);

        // 再次打印状态
        System.out.println();
        pool.printStatus();

        // 关闭线程池
        pool.shutdown();

        System.out.println("\n=== 测试完成 ===");
    }
}
