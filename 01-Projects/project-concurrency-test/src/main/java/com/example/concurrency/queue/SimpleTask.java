package com.example.concurrency.queue;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 简单任务实现
 */
public class SimpleTask implements Task, Comparable<SimpleTask> {
    
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);
    
    private final String taskId;
    private final int priority;
    private final long processingTimeMs;
    private final Runnable work;
    
    public SimpleTask(int priority, long processingTimeMs, Runnable work) {
        this.taskId = "Task-" + ID_GENERATOR.incrementAndGet();
        this.priority = priority;
        this.processingTimeMs = processingTimeMs;
        this.work = work;
    }
    
    @Override
    public void execute() {
        try {
            // 模拟任务处理时间
            if (processingTimeMs > 0) {
                Thread.sleep(processingTimeMs);
            }
            if (work != null) {
                work.run();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println(taskId + " 被中断");
        }
    }
    
    @Override
    public String getTaskId() {
        return taskId;
    }
    
    @Override
    public int getPriority() {
        return priority;
    }
    
    public long getProcessingTimeMs() {
        return processingTimeMs;
    }
    
    @Override
    public int compareTo(SimpleTask other) {
        // 优先级高的排在前面（数值小的优先级高）
        return Integer.compare(this.priority, other.priority);
    }
    
    @Override
    public String toString() {
        return "SimpleTask{" +
                "taskId='" + taskId + '\'' +
                ", priority=" + priority +
                ", processingTimeMs=" + processingTimeMs +
                '}';
    }
}
