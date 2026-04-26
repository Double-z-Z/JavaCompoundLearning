package com.example.server;

import com.example.server.handler.*;
import com.example.server.service.MessageService;
import com.example.server.service.SessionManager;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

/**
 * WebSocket Channel 初始化器
 *
 * 组装 WebSocket 协议的 Pipeline
 * 支持浏览器客户端通过 ws:// 连接
 */
public class WebSocketChatServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final String WEBSOCKET_PATH = "/ws";

    private final SessionManager sessionManager;
    private final MessageService messageService;
    private final SessionHandler sessionHandler;
    private final ChatHandler chatHandler;

    public WebSocketChatServerInitializer(SessionManager sessionManager, MessageService messageService) {
        this.sessionManager = sessionManager;
        this.messageService = messageService;
        // Handler 是可复用的，创建一次
        this.sessionHandler = new SessionHandler(sessionManager, messageService);
        this.chatHandler = new ChatHandler(messageService);
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        // ========== HTTP 协议栈（用于 WebSocket 握手）==========
        pipeline.addLast(new HttpServerCodec());                    // HTTP 编解码
        pipeline.addLast(new HttpObjectAggregator(65536));          // HTTP 消息聚合

        // ========== WebSocket 协议升级 ==========
        pipeline.addLast(new WebSocketServerProtocolHandler(
            WEBSOCKET_PATH,     // WebSocket 路径
            null,               // 子协议（null 表示接受所有）
            true,               // 允许扩展
            65536               // 最大帧大小
        ));

        // ========== WebSocket 帧处理 ==========
        // 将 TextWebSocketFrame 解析为 Message
        pipeline.addLast(new WebSocketFrameHandler());

        // ========== 业务 Handler（复用现有逻辑）==========
        pipeline.addLast(sessionHandler);
        pipeline.addLast(chatHandler);

        // ========== 出站编码器 ==========
        // 将 Message 编码为 TextWebSocketFrame
        pipeline.addLast(new WebSocketProtocolEncoder());
    }
}
