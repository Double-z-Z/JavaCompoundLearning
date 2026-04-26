package com.example.server.handler;

import com.example.server.message.base.Message;
import com.example.server.message.request.HeartbeatMessage;
import com.example.server.message.request.IdentifyMessage;
import com.example.server.message.response.SystemMessage;
import com.example.server.service.MessageService;
import com.example.server.service.SessionManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 会话管理处理器
 * 负责用户认证、心跳维护和连接断开的清理工作
 */
@ChannelHandler.Sharable
public class SessionHandler extends SimpleChannelInboundHandler<Message> {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionHandler.class);
    
    private final SessionManager sessionManager;
    private final MessageService messageService;
    
    public SessionHandler(SessionManager sessionManager, MessageService messageService) {
        this.sessionManager = sessionManager;
        this.messageService = messageService;
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        if (msg instanceof IdentifyMessage) {
            handleIdentify(ctx, (IdentifyMessage) msg);
        } else if (msg instanceof HeartbeatMessage) {
            handleHeartbeat(ctx, (HeartbeatMessage) msg);
        } else {
            // 其他消息传递给下一个 Handler
            ctx.fireChannelRead(msg);
        }
    }
    
    /**
     * 处理认证消息
     */
    private void handleIdentify(ChannelHandlerContext ctx, IdentifyMessage msg) {
        String userId = msg.getUserId();
        Channel channel = ctx.channel();
        
        logger.info("Identify request from user: {}", userId);
        
        // 注册会话
        boolean success = sessionManager.register(userId, channel);
        
        if (success) {
            // 发送认证成功响应
            SystemMessage response = new SystemMessage(
                SystemMessage.SystemEvent.SERVER_NOTICE,
                "认证成功，欢迎 " + userId + " 加入聊天室！",
                userId
            );
            channel.writeAndFlush(response);
            
            // 通知其他用户
            messageService.notifyUserJoined(userId);
            
            logger.info("User {} identified successfully", userId);
        } else {
            // 认证失败
            SystemMessage response = new SystemMessage(
                SystemMessage.SystemEvent.SERVER_NOTICE,
                "认证失败",
                null
            );
            channel.writeAndFlush(response);
            channel.close();
            
            logger.warn("User {} identify failed", userId);
        }
    }
    
    /**
     * 处理心跳消息
     */
    private void handleHeartbeat(ChannelHandlerContext ctx, HeartbeatMessage msg) {
        Channel channel = ctx.channel();
        
        // 更新活跃时间
        sessionManager.updateActiveTime(channel);
        
        // 如果是心跳请求，发送响应
        if (msg.isRequest()) {
            HeartbeatMessage response = new HeartbeatMessage(false);
            channel.writeAndFlush(response);
        }
        
        logger.debug("Heartbeat from channel: {}", channel.id());
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        String userId = sessionManager.unregister(channel);
        
        if (userId != null) {
            messageService.notifyUserLeft(userId);
            logger.info("User {} disconnected", userId);
        }
        
        super.channelInactive(ctx);
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception in SessionHandler", cause);
        ctx.close();
    }
}
