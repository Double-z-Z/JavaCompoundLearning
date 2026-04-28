package com.example.server.message.request;

import com.example.server.message.base.Message;

/**
 * 历史消息请求
 * 客户端发送此消息请求获取最近的历史消息
 */
public class HistoryRequestMessage extends Message {

    private int count;  // 请求的消息数量

    public HistoryRequestMessage() {
        super();
        this.type = MessageType.HISTORY_REQUEST;
    }

    public HistoryRequestMessage(int count) {
        this();
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
