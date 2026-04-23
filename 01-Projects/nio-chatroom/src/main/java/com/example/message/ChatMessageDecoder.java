package com.example.message;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * 聊天消息解码器
 * 
 * 将网络接收的二进制数据解码为应用层 {@link ChatMessage} 对象
 * @see ChatMessageEncoder
 */
public class ChatMessageDecoder {

    /**
     * 解码消息内容
     * 
     * @param messageContent 消息内容字节数组（不含长度前缀），读取流时已经解析出长度前缀用作传输控制
     * @return {@link ChatMessage} 应用层消息对象
     */
    public static ChatMessage decode(byte[] messageContent) {
        String content = new String(messageContent, StandardCharsets.UTF_8);

        try {
            return ChatMessageEncoder.mapper.readValue(content, ChatMessage.class);
        } catch (JsonProcessingException e) {
            // 降级：使用简单格式
            return new ChatMessage(MessageType.MSG, content, new Date(), null);
        }
    }

}
