package com.example.server.protocol;

public class IdentifyMessage extends Message {
    
    private String userId;
    private String token;       // 可选，用于认证
    
    public IdentifyMessage() {
        super();
        this.type = MessageType.IDENTIFY;
    }
    
    public IdentifyMessage(String userId) {
        this();
        this.userId = userId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    @Override
    public String toString() {
        return "IdentifyMessage{" +
                "userId='" + userId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
