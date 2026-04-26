package com.example.server.handler;

import com.example.server.message.base.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocket 帧处理器
 *
 * 职责：
 * 1. 将 TextWebSocketFrame 解析为 Message 对象
 * 2. 将 Message 对象编码为 TextWebSocketFrame
 * 3. 作为 WebSocket 和现有业务逻辑的桥梁
 */
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketFrameHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof TextWebSocketFrame) {
            String text = ((TextWebSocketFrame) frame).text();
            logger.debug("Received WebSocket text: {}", text);

            try {
                // JSON 解析为 Message 对象
                Message message = objectMapper.readValue(text, Message.class);

                // 传递给下一个 Handler（IdentifyHandler、ChatHandler）
                ctx.fireChannelRead(message);
            } catch (Exception e) {
                logger.error("Failed to parse WebSocket message: {}", text, e);
                // 发送错误响应
                ctx.writeAndFlush(new TextWebSocketFrame("{\"error\":\"Invalid message format\"}"));
            }
        } else {
            // 不处理二进制帧或其他类型
            logger.warn("Unsupported WebSocket frame type: {}", frame.getClass().getName());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception in WebSocketFrameHandler", cause);
        ctx.close();
    }
}
