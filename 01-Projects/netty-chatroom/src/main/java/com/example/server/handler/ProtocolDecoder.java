package com.example.server.handler;

import com.example.server.protocol.Message;
import com.example.server.protocol.ProtocolCodec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 协议解码器
 * 长度前缀 + JSON 内容
 */
public class ProtocolDecoder extends ByteToMessageDecoder {
    
    private static final Logger logger = LoggerFactory.getLogger(ProtocolDecoder.class);
    
    // 最大消息长度 1MB
    private static final int MAX_FRAME_LENGTH = 1024 * 1024;
    // 长度字段占 4 字节
    private static final int LENGTH_FIELD_LENGTH = 4;
    
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 检查是否可读长度字段
        if (in.readableBytes() < LENGTH_FIELD_LENGTH) {
            return;
        }
        
        // 标记当前读位置
        in.markReaderIndex();
        
        // 读取长度
        int length = in.readInt();
        
        // 检查长度合法性
        if (length < 0 || length > MAX_FRAME_LENGTH) {
            logger.error("Invalid frame length: {}", length);
            ctx.close();
            return;
        }
        
        // 检查是否收到完整消息
        if (in.readableBytes() < length) {
            // 数据不够，重置读位置，等待下次
            in.resetReaderIndex();
            return;
        }
        
        // 读取 JSON 数据
        byte[] data = new byte[length];
        in.readBytes(data);
        
        try {
            // 解码为 Message 对象
            Message message = ProtocolCodec.decode(data);
            out.add(message);
            
            logger.debug("Decoded message: {}", message.getType());
        } catch (Exception e) {
            logger.error("Failed to decode message", e);
            // 可以选择关闭连接或忽略错误
        }
    }
}
