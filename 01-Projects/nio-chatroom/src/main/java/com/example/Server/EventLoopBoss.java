package com.example.Server;

import com.example.ioutils.IoUtils;
import com.example.message.BroadcastMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class EventLoopBoss {

    private static final Logger logger = LoggerFactory.getLogger(EventLoopBoss.class);
    private static final Logger perfLogger = LoggerFactory.getLogger("PERFORMANCE");

    /**
     * ClientManager 状态枚举
     * 参考 ServerHandler.State 和 ChatServer.State 设计
     */
    public enum State {
        NEW, // 新建状态
        RUNNING, // 运行中（接受连接）
        SHUTTING_DOWN, // 正在关闭（不再接受新连接，等待Worker完成）
        STOPPED // 已停止
    }

    private int port;

    private int workerCount;

    private Selector selector;

    private ServerSocketChannel channel;

    private EventLoopWorker[] workers;

    private ExecutorService workerExecutor;

    /**
     * 全局注册统计
     */
    private final LongAdder totalAcceptTime = new LongAdder();
    private final AtomicInteger acceptCount = new AtomicInteger(0);

    private volatile State state = State.NEW;

    // 启动完成通知（非 final，支持重启时重置）
    private CompletableFuture<Void> readyFuture = new CompletableFuture<>();

    // 关闭完成通知（非 final，支持重启时重置）
    private CompletableFuture<Void> shutdownFuture = new CompletableFuture<>();

    public EventLoopBoss(int port, int workerCount) {
        this.port = port;
        this.workerCount = workerCount;
        this.workers = new EventLoopWorker[workerCount];
    }

    /**
     * 运行服务器（阻塞方法）
     * 支持从 STOPPED 状态重启
     */
    public void run() throws IOException, InterruptedException {
        // 支持从 NEW 或 STOPPED 状态启动
        if (state != State.NEW && state != State.STOPPED) {
            logger.warn("ClientManager 不在可启动状态，当前 state={}", state);
            return;
        }

        // 如果是重启，重置状态和资源
        if (state == State.STOPPED) {
            resetState();
        }

        // 使用try-finally确保资源被关闭
        try {
            // 初始化服务器
            initServer();
            
            // 阶段1：RUNNING - 接受连接并分配给Worker
            runRunningPhase();
            
            // 阶段2：SHUTTING_DOWN - 等待所有Worker结束
            runShuttingDownPhase();
            
        } finally {
            // 统一关闭资源
            shutdownNow();
        }
    }

    /**
     * 初始化服务器：创建selector、channel、启动worker
     */
    private void initServer() throws IOException {
        selector = Selector.open();
        channel = ServerSocketChannel.open();
        channel.socket().bind(new InetSocketAddress(port));
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_ACCEPT);

        this.workerExecutor = Executors.newFixedThreadPool(workerCount);
        for (int i = 0; i < workerCount; i++) {
            EventLoopWorker newHandler = new EventLoopWorker(this).openSelector();
            workers[i] = newHandler;
            workerExecutor.execute(() -> {
                try {
                    newHandler.run();
                } catch (IOException e) {
                    logger.error("Worker 运行异常", e);
                } catch (Exception e) {
                    logger.error("Worker 运行发生未预期异常", e);
                }
            });
        }

        state = State.RUNNING;
        readyFuture.complete(null);
    }

    /**
     * RUNNING阶段：接受连接并分配给Worker
     */
    private void runRunningPhase() throws IOException {
        logger.info("BOSS进入RUNNING阶段，开始接受连接");
        int clinetId = 0;
        int selectCount = 0;
        
        while (state == State.RUNNING) {
            int readyChannels = selector.select(100);
            selectCount++;

            if (selectCount % 100 == 0) {
                logger.debug("select循环运行中... selectCount={}, acceptCount={}", selectCount, acceptCount.get());
            }

            if (readyChannels <= 0) {
                continue;
            }

            logger.debug("select返回 {} 个就绪通道", readyChannels);
            processAcceptableKeys(clinetId);
        }
    }

    /**
     * 处理可接受的连接key
     */
    private void processAcceptableKeys(int clinetId) throws IOException {
        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            if (key.isAcceptable()) {
                acceptConnections(key, clinetId);
            }
            iterator.remove();
        }
    }

    /**
     * 接受所有可用连接并分配给Worker
     */
    private void acceptConnections(SelectionKey key, int clinetId) throws IOException {
        SocketChannel clientChannel;
        while ((clientChannel = ((ServerSocketChannel) key.channel()).accept()) != null) {
            acceptAndRegister(clientChannel, clinetId);
        }
    }

    /**
     * 接受并注册单个连接到Worker
     */
    private void acceptAndRegister(SocketChannel clientChannel, int clinetId) {
        long acceptStart = System.nanoTime();
        boolean registerSuccess = false;
        int workerIndex = -1;

        try {
            clientChannel.configureBlocking(false);
            workerIndex = clinetId % workerCount;
            EventLoopWorker handler = workers[workerIndex];
            handler.register(clientChannel);
            registerSuccess = true;
        } catch (IOException e) {
            logger.error("注册客户端失败: {}", clientChannel, e);
            IoUtils.closeQuietly(clientChannel);
        }

        recordStats(acceptStart, workerIndex, registerSuccess);
    }

    /**
     * 记录连接统计信息
     */
    private void recordStats(long acceptStart, int workerIndex, boolean registerSuccess) {
        long acceptTime = System.nanoTime() - acceptStart;
        totalAcceptTime.add(acceptTime);
        int count = acceptCount.incrementAndGet();

        logger.debug("连接 #{} (acceptCount={}), worker={}, 成功={}, 耗时={}ms",
                count, acceptCount.get(), workerIndex, registerSuccess, acceptTime / 1_000_000);

        if (count % 10 == 0) {
            long avgTime = totalAcceptTime.sum() / count;
            perfLogger.debug("全局注册统计: 总数={}, 成功={}, 平均耗时={}ms",
                    count, registerSuccess, avgTime / 1_000_000);
        }
    }

    /**
     * SHUTTING_DOWN阶段：等待所有Worker结束
     */
    private void runShuttingDownPhase() throws IOException {
        logger.info("BOSS进入SHUTTING_DOWN阶段，等待所有Worker结束");
        
        closeServerSocket();
        shutdownAllWorkers();
        waitForWorkersToStop();
    }

    /**
     * 关闭ServerSocketChannel
     */
    private void closeServerSocket() {
        if (channel != null) {
            try {
                channel.close();
                logger.info("ServerSocketChannel 已关闭");
            } catch (IOException e) {
                logger.error("关闭 ServerSocketChannel 失败", e);
            }
        }
    }

    /**
     * 触发所有Worker关闭
     */
    private void shutdownAllWorkers() {
        logger.info("触发 {} 个 Worker 关闭", workers.length);
        for (int i = 0; i < workers.length; i++) {
            if (workers[i] != null) {
                workers[i].shutdown();
            }
        }
    }

    /**
     * 等待所有Worker停止
     */
    private void waitForWorkersToStop() throws IOException {
        while (state == State.SHUTTING_DOWN) {
            if (areAllWorkersStopped()) {
                logger.info("所有 Worker 已停止，BOSS退出SHUTTING_DOWN阶段");
                break;
            }
            selector.select(100);
        }
    }

    /**
     * 检查是否所有Worker都已停止
     */
    private boolean areAllWorkersStopped() {
        for (EventLoopWorker worker : workers) {
            if (worker.getState() != EventLoopWorker.State.STOPPED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 广播消息给所有worker
     * 
     * @param from    发送消息的worker
     * @param message 要广播的消息
     */
    public void broadcast(EventLoopWorker from, BroadcastMessage message) {
        for (EventLoopWorker worker : workers) {
            if (worker != from) {
                worker.broadcast(message);
            }
        }
    }

    /**
     * 重置状态，支持重启
     */
    private void resetState() {
        logger.info("重置 ClientManager 状态，准备重启");

        // 重置状态
        state = State.NEW;

        // 重置统计
        totalAcceptTime.reset();
        acceptCount.set(0);

        // 重置 Future
        readyFuture = new CompletableFuture<>();
        shutdownFuture = new CompletableFuture<>();

        // 重置 Worker 数组
        workers = new EventLoopWorker[workerCount];

        logger.info("ClientManager 状态重置完成");
    }

    /**
     * 获取当前状态
     */
    public State getState() {
        return state;
    }

    /**
     * 检查是否正在运行
     */
    public boolean isRunning() {
        return state == State.RUNNING;
    }

    /**
     * 等待服务器启动完成（阻塞方法）
     * 
     * @param timeoutMs 超时时间（毫秒）
     * @return true=启动成功, false=超时
     */
    public boolean awaitReady(long timeoutMs) {
        try {
            readyFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 触发关闭流程（非阻塞）
     * 与 ExecutorService.shutdown() 语义一致
     * 
     * 注意：此方法只发送信号，所有实际操作由BOSS线程在run()中执行
     */
    public void shutdown() {
        logger.info("shutdown() 被调用，当前 state={}", state);

        // 只有 RUNNING 状态才能进入 SHUTTING_DOWN
        if (state != State.RUNNING) {
            logger.warn("ClientManager 不在 RUNNING 状态，当前 state={}", state);
            return;
        }

        // 只发送信号：进入 SHUTTING_DOWN 状态
        // 所有实际操作（关闭ServerSocket、触发Worker关闭）由BOSS线程自己执行
        state = State.SHUTTING_DOWN;

        // 唤醒 selector，让BOSS线程立即检查状态并执行关闭流程
        if (selector != null) {
            logger.info("唤醒 BOSS Selector，发送关闭信号");
            selector.wakeup();
        }
    }

    /**
     * 立即关闭并清理资源
     * 与 ExecutorService.shutdownNow() 语义一致
     */
    public void shutdownNow() {
        logger.info("shutdownNow() 被调用，当前 state={}", state);

        // 1. 设置状态为 STOPPED
        state = State.STOPPED;

        // 2. 关闭 ServerSocketChannel
        IoUtils.closeQuietly(channel);

        // 3. 关闭 Selector
        IoUtils.closeQuietly(selector);

        // 4. 关闭所有 Worker
        for (EventLoopWorker worker : workers) {
            if (worker != null) {
                worker.shutdownNow();
            }
        }

        // 5. 关闭 Worker 线程池，必须在发起shutdown信号后调用，否则可能会打断worker的关闭。
        if (workerExecutor != null) {
            workerExecutor.shutdownNow();
        }


        // 6. 通知关闭完成
        shutdownFuture.complete(null);

        logger.info("资源清理完成");
    }

    /**
     * 等待服务器完全停止（阻塞方法）
     * 与 ExecutorService.awaitTermination() 语义一致
     * 
     * 注意：此方法只等待BOSS线程完成，不直接操作Worker
     * Worker的关闭由BOSS线程自己在run()中协调
     * 
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return true=关闭成功, false=超时
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        try {
            // 只等待shutdownFuture（BOSS线程完成）
            // Worker的关闭由BOSS线程自己在run()中协调和等待
            shutdownFuture.get(timeout, unit);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            logger.error("等待关闭完成时出错", e);
            return false;
        }
    }

    /**
     * 等待服务器完全停止（阻塞方法）- 便捷方法，默认毫秒
     * 
     * @param timeoutMs 超时时间（毫秒）
     * @return true=关闭成功, false=超时
     */
    public boolean awaitTermination(long timeoutMs) {
        return awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 获取全局注册统计信息
     */
    public String getGlobalRegisterStats() {
        int count = acceptCount.get();
        long totalTime = totalAcceptTime.sum();
        long avgTime = count > 0 ? totalTime / count : 0;

        logger.debug("getGlobalRegisterStats: acceptCount={}, totalAcceptTime={}", count, totalTime);

        StringBuilder sb = new StringBuilder();
        sb.append("全局注册统计: 总数=").append(count)
                .append(", 总耗时=").append(totalTime / 1_000_000).append("ms")
                .append(", 平均耗时=").append(avgTime / 1_000_000.0).append("ms\n");

        for (int i = 0; i < workers.length; i++) {
            sb.append("  Worker-").append(i).append(": ")
                    .append(workers[i].getRegisterStats()).append("\n");
        }

        return sb.toString();
    }
}
