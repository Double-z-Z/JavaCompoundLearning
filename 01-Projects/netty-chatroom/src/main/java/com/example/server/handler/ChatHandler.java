package com.example.server.handler;

import com.example.server.protocol.*;
import com.example.server.service.MessageService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 聊天业务处理器
 * 处理聊天消息和用户列表请求
 */
@ChannelHandler.Sharable
public class ChatHandler extends SimpleChannelInboundHandler<Message> {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatHandler.class);
    
    private final MessageService messageService;
    
    public ChatHandler(MessageService messageService) {
        this.messageService = messageService;
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        Channel channel = ctx.channel();
        
        switch (msg.getType()) {
            case CHAT:
                handleChatMessage(channel, (ChatMessage) msg);
                break;
            case USER_LIST:
                handleUserListRequest(channel);
                break;
            default:
                // 未知消息类型，传递给下一个 Handler
                ctx.fireChannelRead(msg);
                break;
        }
    }
    
    /**
     * 处理聊天消息
     */
    private void handleChatMessage(Channel channel, ChatMessage msg) {
        logger.info("Received chat message from: {}, content: {}, to: {}",
            msg.getFrom(), msg.getContent(), msg.getTo());
        messageService.handleChatMessage(channel, msg);
    }
    
    /**
     * 处理用户列表请求
     */
    private void handleUserListRequest(Channel channel) {
        logger.debug("Received user list request");
        messageService.handleUserListRequest(channel);
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception in ChatHandler", cause);
        ctx.close();
    }
}
