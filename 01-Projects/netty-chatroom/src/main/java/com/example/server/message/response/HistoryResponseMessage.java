package com.example.server.message.response;

import com.example.server.message.base.Message;

import java.util.List;

/**
 * 历史消息响应
 * 服务端返回请求的历史消息列表
 */
public class HistoryResponseMessage extends Message {

    private List<Message> messages;  // 历史消息列表
    private int totalCount;          // 实际返回的消息数量

    public HistoryResponseMessage() {
        super();
        this.type = MessageType.HISTORY_RESPONSE;
    }

    public HistoryResponseMessage(List<Message> messages) {
        this();
        this.messages = messages;
        this.totalCount = messages != null ? messages.size() : 0;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
        this.totalCount = messages != null ? messages.size() : 0;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
