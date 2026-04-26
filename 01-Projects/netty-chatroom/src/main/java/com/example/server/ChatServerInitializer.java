package com.example.server;

import com.example.server.handler.ChatHandler;
import com.example.server.handler.SessionHandler;
import com.example.server.handler.TcpFrameDecoder;
import com.example.server.handler.TcpFrameEncoder;
import com.example.server.service.MessageService;
import com.example.server.service.SessionManager;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

/**
 * Channel 初始化器
 * 组装 Pipeline
 */
public class ChatServerInitializer extends ChannelInitializer<SocketChannel> {
    
    private final SessionManager sessionManager;
    private final MessageService messageService;
    private final SessionHandler sessionHandler;
    private final ChatHandler chatHandler;
    
    public ChatServerInitializer(SessionManager sessionManager, MessageService messageService) {
        this.sessionManager = sessionManager;
        this.messageService = messageService;
        // Handler 是可复用的，创建一次
        this.sessionHandler = new SessionHandler(sessionManager, messageService);
        this.chatHandler = new ChatHandler(messageService);
    }
    
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        
        // 入站解码器
        pipeline.addLast(new TcpFrameDecoder());
        
        // 业务 Handler
        pipeline.addLast(sessionHandler);
        pipeline.addLast(chatHandler);

        // 出站编码器（后添加的先执行）
        pipeline.addLast(new TcpFrameEncoder());
    }
}
