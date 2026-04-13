package com.example.review.queue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BlockQueueScheduler extends TaskScheduler {
    
    private final BlockingQueue<Task> queueTasks;

    public BlockQueueScheduler(int producerCount, int consumerCount) {
        super(producerCount, consumerCount);
        this.queueTasks = new LinkedBlockingQueue<>();
    }

    @Override
    protected Task acciqureTask() throws InterruptedException {
        return queueTasks.take();
    }

    @Override
    protected void releaseTask(Task task) throws InterruptedException {
        queueTasks.put(task);
    }

}