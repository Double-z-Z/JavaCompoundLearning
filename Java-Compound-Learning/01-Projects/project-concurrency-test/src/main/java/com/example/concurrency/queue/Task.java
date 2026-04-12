package com.example.concurrency.queue;

/**
 * 任务接口
 */
public interface Task {
    
    /**
     * 执行任务
     */
    void execute();
    
    /**
     * 获取任务ID
     */
    String getTaskId();
    
    /**
     * 获取任务优先级（数值越小优先级越高）
     */
    default int getPriority() {
        return 5; // 默认优先级
    }
}
