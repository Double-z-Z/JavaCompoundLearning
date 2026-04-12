package com.example.review.queue;

import java.util.LinkedList;
import java.util.concurrent.Semaphore;

class SemaphoreScheduler extends TaskScheduler {

    private final Semaphore semaphore;

    private final LinkedList<Task> queueTasks;

    public SemaphoreScheduler(int producerCount, int consumerCount, int capacity) {
        super(producerCount, consumerCount);
        this.semaphore = new Semaphore(capacity);
        this.queueTasks = new LinkedList<>();
    }

    @Override
    protected Task acciqureTask() throws InterruptedException {
        semaphore.acquire();
        synchronized (queueTasks) {
            return queueTasks.pollFirst();
        }
    }

    @Override
    protected void releaseTask(Task task) throws InterruptedException {
        semaphore.release();
        synchronized (queueTasks) {
            queueTasks.offerLast(task);
        }
    }
}
