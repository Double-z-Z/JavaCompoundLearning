package com.example.Client;

import com.example.server.protocol.*;
import com.example.server.handler.ProtocolDecoder;
import com.example.server.handler.ProtocolEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 聊天客户端主类，提供对外接口
 * 基于 Netty 实现，支持连接、认证、消息收发
 */
public class ChatClient {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatClient.class);
    
    private final String host;
    private final int port;
    private final String userId;
    
    private EventLoopGroup group;
    private Channel channel;
    private final CopyOnWriteArrayList<ClientListener> listeners = new CopyOnWriteArrayList<>();
    
    // 连接状态
    private volatile boolean connected = false;
    private volatile boolean identified = false;
    
    public ChatClient(String host, int port, String userId) {
        this.host = host;
        this.port = port;
        this.userId = userId;
    }
    
    /**
     * 添加客户端监听器
     */
    public void addClientListener(ClientListener listener) {
        listeners.add(listener);
    }
    
    /**
     * 移除客户端监听器
     */
    public void removeClientListener(ClientListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * 启动聊天客户端（阻塞直到连接并认证完成）
     * @return true 如果成功连接并认证
     */
    public boolean start() {
        return start(10, TimeUnit.SECONDS);
    }
    
    /**
     * 启动聊天客户端（带超时）
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return true 如果成功连接并认证
     */
    public boolean start(long timeout, TimeUnit unit) {
        group = new NioEventLoopGroup();
        
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new ProtocolDecoder());
                        pipeline.addLast(new ProtocolEncoder());
                        pipeline.addLast(new ClientHandler());
                    }
                });
            
            // 连接服务器
            ChannelFuture future = bootstrap.connect(host, port).sync();
            channel = future.channel();
            connected = true;
            
            logger.info("Connected to server {}:{}", host, port);
            
            // 发送认证消息
            IdentifyMessage identifyMsg = new IdentifyMessage(userId);
            channel.writeAndFlush(identifyMsg);
            
            // 等待认证响应（使用 CountDownLatch）
            CountDownLatch identifyLatch = new CountDownLatch(1);
            AtomicReference<Boolean> identifyResult = new AtomicReference<>(false);
            
            // 添加临时监听器等待认证结果
            ClientListener tempListener = new ClientListener() {
                @Override
                public void onConnect() {}
                
                @Override
                public void onDisconnect() {}
                
                @Override
                public void onMessage(String message) {
                    if (message.contains("认证成功")) {
                        identifyResult.set(true);
                        identifyLatch.countDown();
                    } else if (message.contains("认证失败")) {
                        identifyResult.set(false);
                        identifyLatch.countDown();
                    }
                }
            };
            addClientListener(tempListener);
            
            boolean identified = identifyLatch.await(timeout, unit);
            removeClientListener(tempListener);
            
            if (!identified || !identifyResult.get()) {
                logger.error("Identification failed or timeout");
                stop();
                return false;
            }
            
            this.identified = true;
            logger.info("Client {} identified successfully", userId);
            
            // 通知监听器
            for (ClientListener listener : listeners) {
                try {
                    listener.onConnect();
                } catch (Exception e) {
                    logger.error("Error notifying listener", e);
                }
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to start client", e);
            stop();
            return false;
        }
    }
    
    /**
     * 关闭聊天客户端
     */
    public void stop() {
        connected = false;
        identified = false;
        
        if (channel != null) {
            channel.close();
        }
        
        if (group != null) {
            group.shutdownGracefully();
        }
        
        // 通知监听器
        for (ClientListener listener : listeners) {
            try {
                listener.onDisconnect();
            } catch (Exception e) {
                logger.error("Error notifying listener", e);
            }
        }
        
        logger.info("Client stopped");
    }
    
    /**
     * 发送公共消息给所有客户端
     */
    public void sendPublicMessage(String content) {
        if (!isReady()) {
            logger.warn("Client not ready, cannot send message");
            return;
        }
        
        ChatMessage message = new ChatMessage();
        message.setContent(content);
        message.setFrom(userId);
        // to 为 null 表示广播
        
        channel.writeAndFlush(message);
        logger.info("Sent public message: {}", content);
    }
    
    /**
     * 发送私聊消息给指定用户
     */
    public void sendPrivateMessage(String content, String toUser) {
        if (!isReady()) {
            logger.warn("Client not ready, cannot send message");
            return;
        }
        
        ChatMessage message = new ChatMessage();
        message.setContent(content);
        message.setFrom(userId);
        message.setTo(toUser);
        // to 不为 null 表示私聊
        
        channel.writeAndFlush(message);
        logger.info("Sent private message to {}: {}", toUser, content);
    }
    
    /**
     * 请求用户列表
     */
    public void requestUserList() {
        if (!isReady()) {
            logger.warn("Client not ready, cannot request user list");
            return;
        }
        
        UserListMessage message = new UserListMessage();
        channel.writeAndFlush(message);
        logger.debug("Requested user list");
    }
    
    /**
     * 是否已连接
     */
    public boolean isConnected() {
        return connected && channel != null && channel.isActive();
    }
    
    /**
     * 是否已认证（可以收发消息）
     */
    public boolean isIdentified() {
        return identified;
    }
    
    /**
     * 是否就绪（已连接且已认证）
     */
    public boolean isReady() {
        return isConnected() && isIdentified();
    }
    
    /**
     * 获取用户ID
     */
    public String getUserId() {
        return userId;
    }
    
    /**
     * 客户端 Handler
     */
    private class ClientHandler extends SimpleChannelInboundHandler<Message> {
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
            try {
                String messageStr = ProtocolCodec.toJson(msg);
                
                // 处理心跳响应
                if (msg instanceof HeartbeatMessage) {
                    logger.debug("Received heartbeat response");
                    return;
                }
                
                // 通知监听器
                for (ClientListener listener : listeners) {
                    try {
                        listener.onMessage(messageStr);
                    } catch (Exception e) {
                        logger.error("Error notifying listener", e);
                    }
                }
                
            } catch (Exception e) {
                logger.error("Error handling message", e);
            }
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            logger.info("Connection closed by server");
            connected = false;
            identified = false;
            
            // 通知监听器
            for (ClientListener listener : listeners) {
                try {
                    listener.onDisconnect();
                } catch (Exception e) {
                    logger.error("Error notifying listener", e);
                }
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("Exception in client handler", cause);
            ctx.close();
        }
    }
}
