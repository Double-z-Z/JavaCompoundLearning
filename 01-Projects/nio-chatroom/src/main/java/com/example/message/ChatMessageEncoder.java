package com.example.message;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 聊天消息编码器
 * 
 * 将应用层 {@link ChatMessage} 对象编码为网络传输格式（带长度前缀）
 * @see ChatMessageDecoder
 */
public class ChatMessageEncoder {

    static final ObjectMapper mapper = new ObjectMapper();

    public static byte[] encode(ChatMessage message) {
        if (message == null)
            return new byte[0];
        try {
            String json = mapper.writeValueAsString(message);
            return ChatDataEncoder.encode(json.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            // 降级：使用简单格式
            String simple = String.format("[%s] %s: %s",
                    message.getTimestamp(), message.getFrom(), message.getContent());
            return ChatDataEncoder.encode(simple.getBytes(StandardCharsets.UTF_8));
        }
    }

}
