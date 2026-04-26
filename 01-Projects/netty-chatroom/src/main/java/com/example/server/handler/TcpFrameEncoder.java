package com.example.server.handler;

import com.example.server.message.base.Message;
import com.example.server.message.codec.MessageCodec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 协议编码器
 * JSON 内容 + 长度前缀
 */
public class TcpFrameEncoder extends MessageToByteEncoder<Message> {
    
    private static final Logger logger = LoggerFactory.getLogger(TcpFrameEncoder.class);
    
    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) throws Exception {
        try {
            // 编码为 JSON 字节数组
            byte[] data = MessageCodec.encode(msg);
            
            // 写入长度前缀
            out.writeInt(data.length);
            // 写入 JSON 数据
            out.writeBytes(data);
            
            logger.debug("Encoded message: {}, size: {}", msg.getType(), data.length);
        } catch (Exception e) {
            logger.error("Failed to encode message", e);
            throw e;
        }
    }
}
