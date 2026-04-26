package com.example.server.protocol;

public class HeartbeatMessage extends Message {
    
    private boolean request;    // true = 心跳请求, false = 心跳响应
    
    public HeartbeatMessage() {
        super();
        this.type = MessageType.HEARTBEAT;
    }
    
    public HeartbeatMessage(boolean request) {
        this();
        this.request = request;
    }
    
    public boolean isRequest() {
        return request;
    }
    
    public void setRequest(boolean request) {
        this.request = request;
    }
    
    @Override
    public String toString() {
        return "HeartbeatMessage{" +
                "request=" + request +
                ", timestamp=" + timestamp +
                '}';
    }
}
