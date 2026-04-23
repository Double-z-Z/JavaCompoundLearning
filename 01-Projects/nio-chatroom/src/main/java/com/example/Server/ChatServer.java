package com.example.Server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.*;

/**
 * 聊天服务器主类
 * 
 * 职责：封装服务器生命周期管理，提供清晰的启动/停止接口
 * 状态管理：委托给 ClientManager，自身不维护状态
 */
public class ChatServer {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatServer.class);
    
    private final int port;
    private final int workerCount;
    private final long startupTimeoutMs;
    
    private EventLoopBoss clientManager;
    private ExecutorService serverExecutor;
    private Thread shutdownHook;
    
    /**
     * 创建聊天服务器
     * 
     * @param port 监听端口
     * @param workerCount Worker线程数
     */
    public ChatServer(int port, int workerCount) {
        this(port, workerCount, 10000); // 默认10秒启动超时
    }
    
    /**
     * 创建聊天服务器
     * 
     * @param port 监听端口
     * @param workerCount Worker线程数
     * @param startupTimeoutMs 启动超时时间（毫秒）
     */
    public ChatServer(int port, int workerCount, long startupTimeoutMs) {
        this.port = port;
        this.workerCount = workerCount;
        this.startupTimeoutMs = startupTimeoutMs;
    }
    
    /**
     * 启动服务器（阻塞方法，直到服务器准备好接受连接）
     * 
     * @throws IllegalStateException 如果服务器已经启动
     * @throws IOException 如果启动失败
     * @throws TimeoutException 如果启动超时
     */
    public synchronized void start() throws IOException, TimeoutException {
        if (clientManager != null && clientManager.getState() != EventLoopBoss.State.NEW 
                && clientManager.getState() != EventLoopBoss.State.STOPPED) {
            throw new IllegalStateException("服务器已经启动或正在运行，当前状态: " + clientManager.getState());
        }
        
        logger.info("服务器启动中... 端口={}, Worker数={}", port, workerCount);
        
        clientManager = new EventLoopBoss(port, workerCount);
        serverExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ChatServer-Main");
            t.setDaemon(false);
            return t;
        });
        
        // 提交服务器任务
        serverExecutor.execute(() -> {
            try {
                clientManager.run();
            } catch (Exception e) {
                logger.error("服务器运行异常", e);
            }
        });
        
        // 等待服务器准备好
        boolean ready = clientManager.awaitReady(startupTimeoutMs);
        if (!ready) {
            stop();
            throw new TimeoutException("服务器启动超时（" + startupTimeoutMs + "ms）");
        }
        
        // 注册 ShutdownHook
        registerShutdownHook();
    }
    
    /**
     * 注册 JVM 关闭钩子
     */
    private void registerShutdownHook() {
        shutdownHook = new Thread(() -> {
            logger.info("接收到JVM关闭信号，开始优雅关闭...");
            if (getState() == EventLoopBoss.State.RUNNING) {
                shutdown();
                awaitTermination(5000);
            }
        }, "ChatServer-ShutdownHook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }
    
    /**
     * 触发关闭流程（非阻塞）
     * 与 ExecutorService.shutdown() 语义一致
     */
    public synchronized void shutdown() {
        if (clientManager == null || clientManager.getState() != EventLoopBoss.State.RUNNING) {
            logger.warn("服务器不在运行状态，当前状态: {}", 
                    clientManager != null ? clientManager.getState() : "未初始化");
            return;
        }
        
        logger.info("服务器停止中...");
        
        // 触发 ClientManager 关闭
        clientManager.shutdown();
        
        // 触发 serverExecutor 关闭
        if (serverExecutor != null) {
            serverExecutor.shutdown();
        }
    }

    /**
     * 立即关闭服务器（非阻塞）
     * 与 ExecutorService.shutdownNow() 语义一致
     */
    public synchronized void shutdownNow() {
        clientManager.shutdownNow();
    }
    
    /**
     * 等待服务器完全停止（阻塞方法）
     * 与 ExecutorService.awaitTermination() 语义一致
     * 
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return true=关闭成功, false=超时
     */
    public synchronized boolean awaitTermination(long timeout, TimeUnit unit) {
        boolean terminated = true;
        
        // 等待 ClientManager
        if (clientManager != null) {
            terminated = clientManager.awaitTermination(timeout, unit) && terminated;
        }
        
        // 等待 serverExecutor
        if (serverExecutor != null) {
            try {
                terminated = serverExecutor.awaitTermination(timeout, unit) && terminated;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                terminated = false;
            }
        }
        
        if (terminated) {
            logger.info("服务器已停止");
        } else {
            logger.warn("服务器停止超时");
        }
        return terminated;
    }
    
    /**
     * 等待服务器完全停止（阻塞方法）- 便捷方法，默认毫秒
     * 
     * @param timeoutMs 超时时间（毫秒）
     * @return true=关闭成功, false=超时
     */
    public synchronized boolean awaitTermination(long timeoutMs) {
        return awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 停止服务器（触发关闭 + 等待完成）- 默认5秒超时
     */
    public synchronized void stop() {
        stop(5000);
    }
    
    /**
     * 停止服务器（触发关闭 + 等待完成）
     * 
     * @param timeoutMs 超时时间（毫秒）
     * @return true=优雅停止成功, false=超时
     */
    public synchronized boolean stop(long timeoutMs) {
        shutdown();
        return awaitTermination(timeoutMs);
    }
    
    /**
     * 获取当前状态（委托给ClientManager）
     */
    public EventLoopBoss.State getState() {
        return clientManager != null ? clientManager.getState() : EventLoopBoss.State.NEW;
    }

    /**
     * 获取ClientManager（用于测试和强制关闭）
     */
    public EventLoopBoss getClientManager() {
        return clientManager;
    }

    /**
     * 获取全局注册统计
     */
    public String getGlobalRegisterStats() {
        if (clientManager != null) {
            return clientManager.getGlobalRegisterStats();
        }
        return "服务器未启动";
    }
}
