package com.example.message;

import java.util.Date;



/**
 * 聊天消息应用层对象
 * 
 * 职责：存储应用层消息数据（内容、时间戳、发送者）
 * 不包含网络传输相关的编码逻辑
 * 
 * 编码规则：
 * 1. 消息从网络接收后，解码为 ChatMessage 对象
 * 2. 应用层可以修改消息（如添加服务器时间戳）
 * 3. 消息发送到网络前，才进行编码（添加长度前缀）
 * 4. 编码只发生一次，且延迟到最后时刻
 */
public class ChatMessage {

    private MessageType type;

    private String content;

    private Date timestamp;

    private String from;

    private String to;
    
    // 默认构造函数，用于 Jackson 反序列化
    public ChatMessage() {
    }

    /**
     * 创建应用层消息对象
     * 
     * @param content 消息内容
     * @param timestamp 时间戳（服务器时间）
     * @param from 发送者
     */
    public ChatMessage(MessageType type, String content, Date timestamp, String from) {
        this.type = type;
        this.content = content;
        this.timestamp = timestamp;
        this.from = from;
        this.to = null;
    }

    /**
     * 创建应用层消息对象
     * 
     * @param type 消息类型
     * @param content 消息内容
     * @param timestamp 时间戳（服务器时间）
     * @param from 发送者
     * @param to 接收者
     */
    public ChatMessage(MessageType type, String content, Date timestamp, String from, String to) {
        this.type = type;
        this.content = content;
        this.timestamp = timestamp;
        this.from = from;
        this.to = to;
    }

    public MessageType getType() {
        return type;
    }
    
    public String getContent() {
        return content;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getFrom() {
        return from;
    }
    
    public String getTo() {
        return to;
    }
    
    @Override
    public String toString() {
        return String.format("ChatMessage{from='%s', timestamp=%s, content='%s'}", 
            from, timestamp, content);
    }
}
