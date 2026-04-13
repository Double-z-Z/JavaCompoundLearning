package com.example.review.queue;

public class Task {
    private final String id;

    public Task(String id) {
        this.id = id;
    }

    void execute() {
        // 任务执行逻辑
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    String getId() {
        return id;
    }
}
