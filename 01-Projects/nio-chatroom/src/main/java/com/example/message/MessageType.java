package com.example.message;

public enum MessageType {
    MSG,            // 普通消息
    JOIN,           // 加入房间
    LEAVE,          // 离开房间
    PRIV,           // 私聊消息
    SYSTEM,         // 系统消息
    SHUTDOWN_NOTICE // 服务器关闭通知
}
