package com.example.Server;

import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * 客户端上下文，存储每个客户端的状态信息
 */
public class ClientContext {

    public static final int MAX_BUFFER_SIZE = 1024 * 4;

    /**
     * 消息缓冲区（用于读取）
     */
    public ByteBuffer messageBuffer;

    /**
     * 待发送消息队列
     * 存储已编码的消息字节数组，避免重复编码和复制
     * 使用LinkedList支持队首插入（部分发送时放回队列头部）
     */
    public LinkedList<byte[]> pendingMessages = new LinkedList<>();

    /**
     * 当前正在发送的写缓冲区（用于跟踪部分发送的状态）
     */
    private ByteBuffer writeBuffer;

    public ByteBuffer extendBuffer() {
        if (extendable()) {
            messageBuffer = ByteBuffer.allocate(messageBuffer.capacity() * 2);
        }
        return messageBuffer;
    }

    public boolean extendable() {
        ByteBuffer buffer = getBuffer();
        if (buffer.remaining() == buffer.capacity() && buffer.capacity() < MAX_BUFFER_SIZE) {
            return true;
        }
        return false;
    }

    public ByteBuffer getBuffer() {
        if (messageBuffer == null) {
            messageBuffer = ByteBuffer.allocate(256);
        }
        return messageBuffer;
    }

    /**
     * 添加待发送消息到队列
     * @param messageBytes 已编码的消息字节数组
     */
    public synchronized void pushPendingMessage(byte[] messageBytes) {
        pendingMessages.offer(messageBytes);
    }

    /**
     * 获取下一个待发送的消息
     * @return 消息字节数组，如果没有则返回null
     */
    public synchronized byte[] pollPendingMessage() {
        return pendingMessages.poll();
    }

    /**
     * 检查是否有待发送的消息
     * @return true如果有待发送的消息
     */
    public synchronized boolean hasPendingMessages() {
        return !pendingMessages.isEmpty();
    }

    /**
     * 获取当前正在发送的写缓冲区
     * @return 写缓冲区，如果没有则返回null
     */
    public synchronized ByteBuffer getWriteBuffer() {
        return writeBuffer;
    }

    /**
     * 设置当前正在发送的写缓冲区
     * @param buffer 写缓冲区
     */
    public synchronized void setWriteBuffer(ByteBuffer buffer) {
        this.writeBuffer = buffer;
    }
}
