package com.example.server.service;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 会话管理器
 * 管理用户与 Channel 的映射关系
 */
public class SessionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    
    // userId -> Channel 映射
    private final Map<String, Channel> userChannelMap = new ConcurrentHashMap<>();
    
    // ChannelId -> userId 反向映射
    private final Map<ChannelId, String> channelUserMap = new ConcurrentHashMap<>();
    
    // Channel -> 最后活跃时间
    private final Map<ChannelId, Long> lastActiveTime = new ConcurrentHashMap<>();
    
    /**
     * 注册会话
     */
    public boolean register(String userId, Channel channel) {
        if (userId == null || userId.isEmpty()) {
            logger.warn("Attempt to register with empty userId");
            return false;
        }
        
        // 检查是否已存在
        Channel existingChannel = userChannelMap.get(userId);
        if (existingChannel != null && existingChannel.isActive()) {
            logger.warn("User {} already connected, closing old connection", userId);
            existingChannel.close();
        }
        
        userChannelMap.put(userId, channel);
        channelUserMap.put(channel.id(), userId);
        lastActiveTime.put(channel.id(), System.currentTimeMillis());
        
        logger.info("User {} registered, channel: {}", userId, channel.id());
        return true;
    }
    
    /**
     * 注销会话
     */
    public String unregister(Channel channel) {
        ChannelId channelId = channel.id();
        String userId = channelUserMap.remove(channelId);
        
        if (userId != null) {
            userChannelMap.remove(userId);
            lastActiveTime.remove(channelId);
            logger.info("User {} unregistered, channel: {}", userId, channelId);
        }
        
        return userId;
    }
    
    /**
     * 根据 userId 获取 Channel
     */
    public Channel getChannel(String userId) {
        return userChannelMap.get(userId);
    }
    
    /**
     * 根据 Channel 获取 userId
     */
    public String getUserId(Channel channel) {
        return channelUserMap.get(channel.id());
    }
    
    /**
     * 检查用户是否在线
     */
    public boolean isOnline(String userId) {
        Channel channel = userChannelMap.get(userId);
        return channel != null && channel.isActive();
    }
    
    /**
     * 获取在线用户列表
     */
    public List<String> getOnlineUsers() {
        return userChannelMap.entrySet().stream()
                .filter(entry -> entry.getValue().isActive())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取在线用户数量
     */
    public int getOnlineCount() {
        return (int) userChannelMap.values().stream()
                .filter(Channel::isActive)
                .count();
    }
    
    /**
     * 更新活跃时间
     */
    public void updateActiveTime(Channel channel) {
        if (channelUserMap.containsKey(channel.id())) {
            lastActiveTime.put(channel.id(), System.currentTimeMillis());
        }
    }
    
    /**
     * 获取最后活跃时间
     */
    public long getLastActiveTime(Channel channel) {
        return lastActiveTime.getOrDefault(channel.id(), 0L);
    }
    
    /**
     * 获取不活跃的通道
     */
    public List<Channel> getInactiveChannels(long timeoutMs) {
        long now = System.currentTimeMillis();
        return lastActiveTime.entrySet().stream()
                .filter(entry -> now - entry.getValue() > timeoutMs)
                .map(entry -> userChannelMap.get(channelUserMap.get(entry.getKey())))
                .filter(channel -> channel != null && channel.isActive())
                .collect(Collectors.toList());
    }
    
    /**
     * 广播消息给所有在线用户
     */
    public void broadcast(Object msg) {
        userChannelMap.values().forEach(channel -> {
            if (channel.isActive()) {
                channel.writeAndFlush(msg);
            }
        });
    }
    
    /**
     * 发送消息给指定用户
     */
    public boolean sendToUser(String userId, Object msg) {
        Channel channel = userChannelMap.get(userId);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(msg);
            return true;
        }
        return false;
    }
}
