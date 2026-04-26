package com.example.server.service;

import com.example.server.protocol.*;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 消息服务
 * 处理业务消息逻辑
 */
public class MessageService {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);
    
    private final SessionManager sessionManager;
    
    public MessageService(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
    
    /**
     * 处理聊天消息
     */
    public void handleChatMessage(Channel channel, ChatMessage message) {
        String userId = sessionManager.getUserId(channel);
        if (userId == null) {
            logger.warn("Unauthenticated user tried to send message");
            return;
        }
        
        // 设置发送者
        message.setFrom(userId);
        
        if (message.isPrivate()) {
            // 私聊
            handlePrivateMessage(message);
        } else {
            // 广播
            handleBroadcastMessage(message);
        }
    }
    
    /**
     * 处理私聊
     */
    private void handlePrivateMessage(ChatMessage message) {
        String toUser = message.getTo();
        String fromUser = message.getFrom();
        
        // 发送给目标用户
        boolean sent = sessionManager.sendToUser(toUser, message);
        
        if (!sent) {
            // 用户不在线，发送系统提示给发送者
            SystemMessage errorMsg = new SystemMessage(
                SystemMessage.SystemEvent.SERVER_NOTICE,
                "用户 " + toUser + " 不在线",
                fromUser
            );
            sessionManager.sendToUser(fromUser, errorMsg);
            logger.info("Private message from {} to {} failed, user offline", fromUser, toUser);
        } else {
            logger.info("Private message from {} to {}", fromUser, toUser);
        }
    }
    
    /**
     * 处理广播消息
     */
    private void handleBroadcastMessage(ChatMessage message) {
        String fromUser = message.getFrom();
        logger.info("Broadcast message from {}: {}, online users: {}",
            fromUser, message.getContent(), sessionManager.getOnlineCount());

        // 广播给所有用户（包括发送者自己）
        sessionManager.broadcast(message);
        logger.info("Broadcast message sent to all users");
    }
    
    /**
     * 处理用户列表请求
     */
    public void handleUserListRequest(Channel channel) {
        String userId = sessionManager.getUserId(channel);
        if (userId == null) {
            return;
        }
        
        List<String> users = sessionManager.getOnlineUsers();
        UserListMessage message = new UserListMessage(users);
        
        channel.writeAndFlush(message);
        logger.debug("User list sent to {}, count: {}", userId, users.size());
    }
    
    /**
     * 发送系统消息
     */
    public void sendSystemMessage(SystemMessage.SystemEvent event, String content, String userId) {
        SystemMessage message = new SystemMessage(event, content, userId);
        
        if (userId != null) {
            // 发送给特定用户
            sessionManager.sendToUser(userId, message);
        } else {
            // 广播
            sessionManager.broadcast(message);
        }
    }
    
    /**
     * 用户加入通知
     */
    public void notifyUserJoined(String userId) {
        SystemMessage message = new SystemMessage(
            SystemMessage.SystemEvent.USER_JOINED,
            "用户 " + userId + " 加入了聊天室",
            userId
        );
        sessionManager.broadcast(message);
        logger.info("User {} joined, notified all users", userId);
    }
    
    /**
     * 用户离开通知
     */
    public void notifyUserLeft(String userId) {
        SystemMessage message = new SystemMessage(
            SystemMessage.SystemEvent.USER_LEFT,
            "用户 " + userId + " 离开了聊天室",
            userId
        );
        sessionManager.broadcast(message);
        logger.info("User {} left, notified all users", userId);
    }
}
