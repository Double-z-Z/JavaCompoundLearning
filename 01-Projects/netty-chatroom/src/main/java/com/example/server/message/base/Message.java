package com.example.server.message.base;

import com.example.server.message.request.ChatMessage;
import com.example.server.message.request.HeartbeatMessage;
import com.example.server.message.request.HistoryRequestMessage;
import com.example.server.message.request.IdentifyMessage;
import com.example.server.message.response.HistoryResponseMessage;
import com.example.server.message.response.SystemMessage;
import com.example.server.message.response.UserListMessage;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ChatMessage.class, name = "CHAT"),
    @JsonSubTypes.Type(value = IdentifyMessage.class, name = "IDENTIFY"),
    @JsonSubTypes.Type(value = HeartbeatMessage.class, name = "HEARTBEAT"),
    @JsonSubTypes.Type(value = SystemMessage.class, name = "SYSTEM"),
    @JsonSubTypes.Type(value = UserListMessage.class, name = "USER_LIST"),
    @JsonSubTypes.Type(value = HistoryRequestMessage.class, name = "HISTORY_REQUEST"),
    @JsonSubTypes.Type(value = HistoryResponseMessage.class, name = "HISTORY_RESPONSE")
})
public abstract class Message {
    
    public enum MessageType {
        CHAT,               // 聊天消息
        IDENTIFY,           // 认证消息
        HEARTBEAT,          // 心跳消息
        SYSTEM,             // 系统消息
        USER_LIST,          // 用户列表
        HISTORY_REQUEST,    // 历史消息请求
        HISTORY_RESPONSE    // 历史消息响应
    }
    
    protected MessageType type;
    protected long timestamp;
    
    public Message() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public MessageType getType() {
        return type;
    }
    
    public void setType(MessageType type) {
        this.type = type;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
