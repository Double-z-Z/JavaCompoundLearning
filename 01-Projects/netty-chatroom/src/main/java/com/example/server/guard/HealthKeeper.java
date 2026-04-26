package com.example.server.guard;

import com.example.server.protocol.HeartbeatMessage;
import com.example.server.service.SessionManager;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 健康检查守护服务
 * 定时检测客户端心跳，关闭超时连接
 */
public class HealthKeeper {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthKeeper.class);
    
    // 默认检查间隔 30 秒
    private static final long CHECK_INTERVAL_SECONDS = 30;
    // 默认超时时间 60 秒
    private static final long TIMEOUT_MILLISECONDS = 60000;
    // 心跳请求后的等待时间 5 秒
    private static final long HEARTBEAT_WAIT_SECONDS = 5;
    
    private final SessionManager sessionManager;
    private final ScheduledExecutorService scheduler;
    
    public HealthKeeper(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HealthKeeper");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * 启动健康检查
     */
    public void start() {
        logger.info("Starting HealthKeeper, check interval: {}s, timeout: {}ms", 
            CHECK_INTERVAL_SECONDS, TIMEOUT_MILLISECONDS);
        
        scheduler.scheduleAtFixedRate(
            this::checkHealth,
            CHECK_INTERVAL_SECONDS,
            CHECK_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
    }
    
    /**
     * 停止健康检查
     */
    public void stop() {
        logger.info("Stopping HealthKeeper");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 执行健康检查
     */
    private void checkHealth() {
        try {
            // 获取不活跃的通道
            List<Channel> inactiveChannels = sessionManager.getInactiveChannels(TIMEOUT_MILLISECONDS);
            
            if (inactiveChannels.isEmpty()) {
                logger.debug("Health check passed, no inactive channels");
                return;
            }
            
            logger.info("Found {} inactive channels, sending heartbeat check", inactiveChannels.size());
            
            // 发送心跳检测
            for (Channel channel : inactiveChannels) {
                if (channel.isActive()) {
                    sendHeartbeatCheck(channel);
                }
            }
        } catch (Exception e) {
            logger.error("Error during health check", e);
        }
    }
    
    /**
     * 发送心跳检测并设置超时关闭
     */
    private void sendHeartbeatCheck(Channel channel) {
        String userId = sessionManager.getUserId(channel);
        
        logger.debug("Sending heartbeat check to user: {}", userId);
        
        // 发送心跳请求
        HeartbeatMessage heartbeat = new HeartbeatMessage(true);
        channel.writeAndFlush(heartbeat);
        
        // 延迟检查，如果仍然不活跃则关闭
        channel.eventLoop().schedule(() -> {
            // 再次检查活跃时间
            long lastActive = sessionManager.getLastActiveTime(channel);
            long now = System.currentTimeMillis();
            
            if (now - lastActive > TIMEOUT_MILLISECONDS + HEARTBEAT_WAIT_SECONDS * 1000) {
                // 仍然超时，关闭连接
                logger.warn("User {} heartbeat timeout, closing connection", userId);
                channel.close();
            } else {
                logger.debug("User {} responded to heartbeat", userId);
            }
        }, HEARTBEAT_WAIT_SECONDS, TimeUnit.SECONDS);
    }
}
