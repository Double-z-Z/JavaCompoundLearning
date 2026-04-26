package com.example.server.protocol;

public class SystemMessage extends Message {
    
    public enum SystemEvent {
        USER_JOINED,    // 用户加入
        USER_LEFT,      // 用户离开
        SERVER_NOTICE   // 服务器公告
    }
    
    private SystemEvent event;
    private String content;
    private String userId;      // 关联用户（可选）
    
    public SystemMessage() {
        super();
        this.type = MessageType.SYSTEM;
    }
    
    public SystemMessage(SystemEvent event, String content) {
        this();
        this.event = event;
        this.content = content;
    }
    
    public SystemMessage(SystemEvent event, String content, String userId) {
        this(event, content);
        this.userId = userId;
    }
    
    public SystemEvent getEvent() {
        return event;
    }
    
    public void setEvent(SystemEvent event) {
        this.event = event;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    @Override
    public String toString() {
        return "SystemMessage{" +
                "event=" + event +
                ", content='" + content + '\'' +
                ", userId='" + userId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
