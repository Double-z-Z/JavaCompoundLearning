package com.example.server;

import com.example.server.guard.HealthKeeper;
import com.example.server.service.MessageService;
import com.example.server.service.SessionManager;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Netty 聊天室服务端入口
 * 
 * 基于 Guava AbstractExecutionThreadService 实现服务生命周期管理：
 * 1. 继承成熟的状态机（NEW -> STARTING -> RUNNING -> STOPPING -> TERMINATED）
 * 2. 自动处理线程管理和状态转换
 * 3. 支持 Listener 机制监听状态变化
 * 4. 提供 awaitRunning() / awaitTerminated() 等待方法
 */
public class ChatServer extends AbstractExecutionThreadService {

    private static final Logger logger = LoggerFactory.getLogger(ChatServer.class);

    private final int tcpPort;
    private final int websocketPort;
    private final SessionManager sessionManager;
    private final MessageService messageService;
    private final HealthKeeper healthKeeper;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private volatile Channel tcpChannel;
    private volatile Channel websocketChannel;
    private Thread serverThread;

    public ChatServer(int tcpPort) {
        this(tcpPort, tcpPort + 1);  // WebSocket 默认用 TCP 端口 + 1
    }

    public ChatServer(int tcpPort, int websocketPort) {
        this.tcpPort = tcpPort;
        this.websocketPort = websocketPort;
        this.sessionManager = new SessionManager();
        this.messageService = new MessageService(sessionManager);
        this.healthKeeper = new HealthKeeper(sessionManager);
    }
    
    /**
     * 服务启动前的初始化（在状态变为 STARTING 后调用）
     * 这里执行资源初始化，但不阻塞
     */
    @Override
    protected void startUp() throws Exception {
        logger.info("Starting ChatServer on TCP port {}, WebSocket port {}", tcpPort, websocketPort);

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        // ========== TCP 端口 ==========
        ServerBootstrap tcpBootstrap = new ServerBootstrap();
        tcpBootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChatServerInitializer(sessionManager, messageService))
            .option(ChannelOption.SO_BACKLOG, 128)
            .childOption(ChannelOption.SO_KEEPALIVE, true);

        tcpChannel = tcpBootstrap.bind(tcpPort).sync().channel();
        logger.info("TCP server bound to port {}", tcpPort);

        // ========== WebSocket 端口 ==========
        ServerBootstrap wsBootstrap = new ServerBootstrap();
        wsBootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new WebSocketChatServerInitializer(sessionManager, messageService))
            .option(ChannelOption.SO_BACKLOG, 128)
            .childOption(ChannelOption.SO_KEEPALIVE, true);

        websocketChannel = wsBootstrap.bind(websocketPort).sync().channel();
        logger.info("WebSocket server bound to port {}", websocketPort);

        // 启动健康检查
        healthKeeper.start();

        logger.info("ChatServer started successfully");
    }
    
    /**
     * 服务运行逻辑（在状态变为 RUNNING 后调用）
     * 这是服务的主线程，阻塞直到服务停止
     */
    @Override
    protected void run() throws Exception {
        // 记录服务线程，用于后续管理
        serverThread = Thread.currentThread();

        logger.info("ChatServer is running, waiting for shutdown signal...");

        // 阻塞等待任意一个 Channel 关闭（通常它们会同时关闭）
        tcpChannel.closeFuture().sync();

        logger.info("Server channel closed, exiting run loop");
    }
    
    /**
     * 服务停止后的清理（在状态变为 TERMINATED 前调用）
     * 无论 run() 是正常结束还是异常退出，都会执行
     */
    @Override
    protected void shutDown() throws Exception {
        logger.info("Shutting down ChatServer");
        
        // 停止健康检查
        if (healthKeeper != null) {
            healthKeeper.stop();
        }
        
        // 关闭 Channel
        if (tcpChannel != null) {
            tcpChannel.close();
        }
        if (websocketChannel != null) {
            websocketChannel.close();
        }
        
        // 关闭 EventLoopGroup
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        
        logger.info("ChatServer shutdown complete");
    }
    
    /**
     * 触发关闭（覆盖父类方法，添加日志）
     */
    @Override
    protected void triggerShutdown() {
        logger.info("Triggering ChatServer shutdown");
        // 关闭 Channel 会触发 closeFuture，从而退出 run() 循环
        if (tcpChannel != null) {
            tcpChannel.close();
        }
        if (websocketChannel != null) {
            websocketChannel.close();
        }
    }
    
    /**
     * 获取服务名称（用于日志和线程命名）
     */
    @Override
    protected String serviceName() {
        return "ChatServer-" + tcpPort;
    }
    
    /**
     * 获取会话管理器（用于外部访问）
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }
    
    /**
     * 获取消息服务（用于外部访问）
     */
    public MessageService getMessageService() {
        return messageService;
    }
    
    /**
     * 获取 TCP 端口号
     */
    public int getTcpPort() {
        return tcpPort;
    }

    /**
     * 获取 WebSocket 端口号
     */
    public int getWebsocketPort() {
        return websocketPort;
    }
    
    public static void main(String[] args) {
        int port = 8080;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        
        ChatServer server = new ChatServer(port);
        
        // 添加 JVM 关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("JVM shutdown detected, stopping ChatServer...");
            server.stopAsync();
            try {
                server.awaitTerminated(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.error("Error waiting for server termination", e);
            }
        }, "ChatServer-ShutdownHook"));
        
        try {
            // 启动服务（异步）
            server.startAsync();
            
            // 等待服务进入 RUNNING 状态
            server.awaitRunning();
            logger.info("Server is now running and accepting connections");
            
            // 阻塞等待服务终止
            server.awaitTerminated();
            logger.info("Server terminated");
            
        } catch (Exception e) {
            logger.error("Server error", e);
            System.exit(1);
        }
    }
}
