package com.example.server.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ChatMessage extends Message {
    
    private String from;
    private String to;          // null 表示广播
    private String content;
    
    public ChatMessage() {
        super();
        this.type = MessageType.CHAT;
    }
    
    public ChatMessage(String from, String content) {
        this();
        this.from = from;
        this.content = content;
    }
    
    public ChatMessage(String from, String to, String content) {
        this();
        this.from = from;
        this.to = to;
        this.content = content;
    }
    
    public String getFrom() {
        return from;
    }
    
    public void setFrom(String from) {
        this.from = from;
    }
    
    public String getTo() {
        return to;
    }
    
    public void setTo(String to) {
        this.to = to;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    @JsonIgnore
    public boolean isPrivate() {
        return to != null && !to.isEmpty();
    }
    
    @Override
    public String toString() {
        return "ChatMessage{" +
                "from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", content='" + content + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
