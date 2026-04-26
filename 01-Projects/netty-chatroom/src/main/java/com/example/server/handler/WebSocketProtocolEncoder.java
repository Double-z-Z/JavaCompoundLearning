package com.example.server.handler;

import com.example.server.message.base.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * WebSocket 协议编码器
 *
 * 职责：将 Message 对象编码为 TextWebSocketFrame
 * 用于 WebSocket 连接的出站消息
 */
public class WebSocketProtocolEncoder extends MessageToMessageEncoder<Message> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketProtocolEncoder.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, List<Object> out) throws Exception {
        try {
            // 将 Message 转为 JSON 字符串
            String json = objectMapper.writeValueAsString(msg);

            // 包装为 TextWebSocketFrame
            TextWebSocketFrame frame = new TextWebSocketFrame(json);
            out.add(frame);

            logger.debug("Encoded message to WebSocket frame: {}", json);
        } catch (Exception e) {
            logger.error("Failed to encode message: {}", msg, e);
            // 不抛出异常，避免断开连接
        }
    }
}
