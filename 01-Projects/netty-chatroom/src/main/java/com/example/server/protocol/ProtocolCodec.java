package com.example.server.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import java.io.IOException;

public class ProtocolCodec {
    
    private static final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * 将 Message 对象编码为 JSON 字节数组
     */
    public static byte[] encode(Message message) throws IOException {
        return mapper.writeValueAsBytes(message);
    }
    
    /**
     * 将 JSON 字节数组解码为 Message 对象
     */
    public static Message decode(byte[] data) throws IOException {
        return mapper.readValue(data, Message.class);
    }
    
    /**
     * 从 ByteBuf 读取并解码
     */
    public static Message decode(ByteBuf buf) throws IOException {
        // 读取字节数组后解码
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return decode(data);
    }
    
    /**
     * 编码并写入 ByteBuf
     */
    public static void encode(Message message, ByteBuf buf) throws IOException {
        byte[] data = encode(message);
        buf.writeBytes(data);
    }
    
    /**
     * 获取 JSON 字符串（用于调试）
     */
    public static String toJson(Message message) throws IOException {
        return mapper.writeValueAsString(message);
    }
}
