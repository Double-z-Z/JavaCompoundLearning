package com.example.Server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.*;

/**
 * 聊天服务器主类
 * 
 * 职责：封装服务器生命周期管理，提供清晰的启动/停止接口
 * 状态管理：STARTING -> RUNNING -> STOPPING -> STOPPED
 */
public class ChatServer {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatServer.class);
    
    public enum State {
        NEW,        // 新建状态
        STARTING,   // 启动中
        RUNNING,    // 运行中（已准备好接受连接）
        STOPPING,   // 停止中
        STOPPED     // 已停止
    }
    
    private final int port;
    private final int workerCount;
    private final long startupTimeoutMs;
    
    private volatile State state = State.NEW;
    private ClientManager clientManager;
    private ExecutorService serverExecutor;
    
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
        if (state != State.NEW && state != State.STOPPED) {
            throw new IllegalStateException("服务器已经启动或正在运行，当前状态: " + state);
        }
        
        state = State.STARTING;
        logger.info("服务器启动中... 端口={}, Worker数={}", port, workerCount);
        
        clientManager = new ClientManager(port, workerCount);
        serverExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ChatServer-Main");
            t.setDaemon(false);
            return t;
        });
        
        // 提交服务器任务
        serverExecutor.execute(() -> {
            try {
                // 在独立线程中启动服务器（不传递回调，使用Future等待）
                clientManager.run();
            } catch (Exception e) {
                logger.error("服务器运行异常", e);
            }
        });
        
        // 等待服务器准备好（通过ClientManager的Future）
        try {
            clientManager.getReadyFuture().get(startupTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            shutdown();
            throw new RuntimeException("服务器启动被中断", e);
        } catch (ExecutionException e) {
            shutdown();
            throw new RuntimeException("服务器启动失败", e.getCause());
        } catch (TimeoutException e) {
            shutdown();
            throw new TimeoutException("服务器启动超时（" + startupTimeoutMs + "ms）");
        }
        
        state = State.RUNNING;
        logger.info("服务器启动完成，已准备好接受连接");
    }
    
    /**
     * 停止服务器
     */
    public synchronized void stop() {
        if (state != State.RUNNING) {
            logger.warn("服务器不在运行状态，当前状态: {}", state);
            return;
        }
        
        state = State.STOPPING;
        logger.info("服务器停止中...");
        
        shutdown();
        
        state = State.STOPPED;
        logger.info("服务器已停止");
    }
    
    /**
     * 关闭资源
     */
    private void shutdown() {
        if (clientManager != null) {
            clientManager.shutdown();
        }
        
        if (serverExecutor != null) {
            serverExecutor.shutdown();
            try {
                if (!serverExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    serverExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                serverExecutor.shutdownNow();
            }
        }
    }
    
    /**
     * 获取当前状态
     */
    public State getState() {
        return state;
    }
    
    /**
     * 检查服务器是否正在运行
     */
    public boolean isRunning() {
        return state == State.RUNNING;
    }
    
    /**
     * 获取ClientManager（用于测试统计）
     */
    public ClientManager getClientManager() {
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
