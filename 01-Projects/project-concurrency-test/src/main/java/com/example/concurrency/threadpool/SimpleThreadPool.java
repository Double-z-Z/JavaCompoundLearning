package com.example.concurrency.threadpool;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 简易线程池实现
 *
 * 知识点：
 * 1. 线程池核心参数（corePoolSize、maxPoolSize、keepAliveTime）
 * 2. 任务提交与执行流程
 * 3. Worker线程的生命周期管理
 * 4. 线程安全的状态管理
 *
 * 需求：
 * - 支持核心线程数和最大线程数配置
 * - 有界任务队列
 * - 拒绝策略（抛出异常/丢弃/调用者运行）
 * - 线程空闲回收机制
 */
public class SimpleThreadPool {

    // 线程池状态
    private static final int RUNNING = 0;
    private static final int SHUTDOWN = 1;
    private static final int STOP = 2;
    private static final int TERMINATED = 3;

    private volatile int state = RUNNING;
    private final ReentrantLock mainLock = new ReentrantLock();

    // 核心参数
    private final int corePoolSize;
    private final int maximumPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final BlockingQueue<Runnable> workQueue;
    private final RejectPolicy rejectPolicy;

    // 工作线程集合
    private final Set<Worker> workers = new HashSet<>();

    // 监控指标
    private final AtomicInteger workerCount = new AtomicInteger(0);
    private final AtomicInteger completedTaskCount = new AtomicInteger(0);

    /**
     * 拒绝策略接口
     */
    public interface RejectPolicy {
        void reject(Runnable task, SimpleThreadPool pool);
    }

    // 预定义拒绝策略
    public static final RejectPolicy ABORT_POLICY = (task, pool) -> {
        throw new RejectedExecutionException("Task " + task + " rejected from " + pool);
    };

    public static final RejectPolicy CALLER_RUNS_POLICY = (task, pool) -> {
        if (!pool.isShutdown()) {
            task.run();
        }
    };

    public static final RejectPolicy DISCARD_POLICY = (task, pool) -> {
        // 静默丢弃
    };

    public SimpleThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit timeUnit,
                            int queueCapacity, RejectPolicy rejectPolicy) {
        if (corePoolSize < 0 || maximumPoolSize <= 0 || maximumPoolSize < corePoolSize || keepAliveTime < 0) {
            throw new IllegalArgumentException("Invalid pool parameters");
        }

        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.workQueue = new LinkedBlockingQueue<>(queueCapacity);
        this.rejectPolicy = rejectPolicy != null ? rejectPolicy : ABORT_POLICY;
    }

    /**
     * 提交任务
     */
    public void execute(Runnable task) {
        if (task == null) {
            throw new NullPointerException();
        }

        int c = workerCount.get();

        // 1. 当前线程数 < corePoolSize，创建核心线程
        if (c < corePoolSize) {
            if (addWorker(task, true)) {
                return;
            }
            // 添加失败，重新获取状态
            c = workerCount.get();
        }

        // 2. 尝试将任务加入队列
        if (isRunning(c) && workQueue.offer(task)) {
            // 双重检查
            int recheck = workerCount.get();
            if (!isRunning(recheck) && remove(task)) {
                reject(task);
            } else if (recheck == 0) {
                addWorker(null, false);
            }
        }
        // 3. 队列满了，尝试创建非核心线程
        else if (!addWorker(task, false)) {
            // 4. 创建失败，执行拒绝策略
            reject(task);
        }
    }

    /**
     * 添加工作线程
     */
    private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        for (;;) {
            int c = workerCount.get();
            int rs = state;

            // 检查状态
            if (rs >= SHUTDOWN && !(rs == SHUTDOWN && firstTask == null && !workQueue.isEmpty())) {
                return false;
            }

            for (;;) {
                int wc = workerCount.get();
                if (wc >= (core ? corePoolSize : maximumPoolSize)) {
                    return false;
                }
                if (workerCount.compareAndSet(wc, wc + 1)) {
                    break retry;
                }
                // CAS失败，重新检查
                c = workerCount.get();
                if (c != wc) {
                    continue retry;
                }
            }
        }

        Worker w = new Worker(firstTask);
        final Thread t = w.thread;

        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int rs = state;
            if (rs < SHUTDOWN || (rs == SHUTDOWN && firstTask == null)) {
                if (t.isAlive()) {
                    throw new IllegalThreadStateException();
                }
                workers.add(w);
            }
        } finally {
            mainLock.unlock();
        }

        t.start();
        return true;
    }

    /**
     * 关闭线程池（优雅关闭）
     */
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            state = SHUTDOWN;
        } finally {
            mainLock.unlock();
        }

        // 中断空闲线程
        interruptIdleWorkers();
    }

    /**
     * 立即关闭
     */
    public void shutdownNow() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            state = STOP;
        } finally {
            mainLock.unlock();
        }

        // 中断所有线程
        interruptWorkers();
    }

    /**
     * 等待线程池终止
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            while (state != TERMINATED) {
                if (nanos <= 0) {
                    return false;
                }
                nanos = 0; // 简化实现
            }
            return true;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 中断空闲工作线程
     */
    private void interruptIdleWorkers() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers) {
                Thread t = w.thread;
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        w.unlock();
                    }
                }
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 中断所有工作线程
     */
    private void interruptWorkers() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers) {
                w.thread.interrupt();
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 从队列中移除任务
     */
    private boolean remove(Runnable task) {
        return workQueue.remove(task);
    }

    /**
     * 执行拒绝策略
     */
    private void reject(Runnable task) {
        rejectPolicy.reject(task, this);
    }

    /**
     * 判断线程池是否运行中
     */
    private boolean isRunning(int c) {
        return state == RUNNING;
    }

    /**
     * 判断线程池是否已关闭
     */
    public boolean isShutdown() {
        return state >= SHUTDOWN;
    }

    /**
     * 获取活跃线程数
     */
    public int getActiveCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int n = 0;
            for (Worker w : workers) {
                if (w.isLocked()) {
                    ++n;
                }
            }
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        return workQueue.size();
    }

    /**
     * 获取已完成任务数
     */
    public long getCompletedTaskCount() {
        return completedTaskCount.get();
    }

    /**
     * 打印线程池状态
     */
    public void printStatus() {
        System.out.println("=== 线程池状态 ===");
        System.out.println("状态: " + (state == RUNNING ? "RUNNING" : state == SHUTDOWN ? "SHUTDOWN" : state == STOP ? "STOP" : "TERMINATED"));
        System.out.println("工作线程数: " + workerCount.get());
        System.out.println("活跃线程数: " + getActiveCount());
        System.out.println("队列大小: " + getQueueSize());
        System.out.println("已完成任务数: " + getCompletedTaskCount());
    }

    /**
     * 工作线程类
     */
    private final class Worker extends ReentrantLock implements Runnable {
        final Thread thread;
        Runnable firstTask;
        @SuppressWarnings("unused")
        volatile long completedTasks;

        Worker(Runnable firstTask) {
            this.firstTask = firstTask;
            this.thread = new Thread(this, "Pool-Worker-" + workerCount.get());
        }

        @Override
        public void run() {
            runWorker(this);
        }
    }

    /**
     * 运行工作线程的主循环
     */
    private void runWorker(Worker w) {
        Runnable task = w.firstTask;
        w.firstTask = null;
        boolean completedAbruptly = true;

        try {
            while (task != null || (task = getTask()) != null) {
                w.lock();
                try {
                    // 检查中断状态
                    if ((state >= STOP || (Thread.interrupted() && state >= STOP)) && !w.thread.isInterrupted()) {
                        w.thread.interrupt();
                    }

                    // 执行任务
                    task.run();
                    completedTaskCount.incrementAndGet();
                    w.completedTasks++;
                } catch (Exception ex) {
                    throw ex;
                } finally {
                    w.unlock();
                    task = null;
                }
            }
            completedAbruptly = false;
        } finally {
            processWorkerExit(w, completedAbruptly);
        }
    }

    /**
     * 从队列获取任务
     */
    private Runnable getTask() {
        boolean timedOut = false;

        for (;;) {
            int c = workerCount.get();
            int rs = state;

            // 检查是否需要减少工作线程
            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
                if (workerCount.compareAndSet(c, c - 1)) {
                    return null;
                }
                continue;
            }

            boolean timed = c > corePoolSize;

            if ((c > maximumPoolSize || (timed && timedOut)) && c > 1) {
                if (workerCount.compareAndSet(c, c - 1)) {
                    return null;
                }
                continue;
            }

            try {
                Runnable r = timed ?
                    workQueue.poll(keepAliveTime, timeUnit) :
                    workQueue.take();
                if (r != null) {
                    return r;
                }
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }

    /**
     * 处理工作线程退出
     */
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        if (completedAbruptly) {
            workerCount.decrementAndGet();
        }

        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            workers.remove(w);
        } finally {
            mainLock.unlock();
        }

        // 尝试终止线程池
        tryTerminate();

        // 如果需要，添加新的工作线程
        int c = workerCount.get();
        if (!completedAbruptly && c < minimumNeededWorkers()) {
            addWorker(null, false);
        }
    }

    /**
     * 尝试终止线程池
     */
    private void tryTerminate() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (state == SHUTDOWN && workQueue.isEmpty()) {
                state = TERMINATED;
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 计算需要的最小工作线程数
     */
    private int minimumNeededWorkers() {
        return state == RUNNING ? 1 : 0;
    }
}
