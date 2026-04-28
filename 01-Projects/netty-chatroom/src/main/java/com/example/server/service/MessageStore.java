package com.example.server.service;

import com.example.server.message.base.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 消息存储服务
 *
 * 设计要点：
 * 1. 内存存储：使用 ConcurrentLinkedQueue 实现线程安全的循环缓冲区
 * 2. 容量限制：最多保存 MAX_SIZE 条消息，超出时移除最旧的
 * 3. 线程安全：所有操作都是线程安全的，支持多线程并发访问
 */
public class MessageStore {

    private static final int MAX_SIZE = 100;

    // 使用 ConcurrentLinkedQueue 保证线程安全
    // 采用 FIFO 策略，新消息添加到尾部，超出容量时从头部移除
    private final ConcurrentLinkedQueue<Message> messages = new ConcurrentLinkedQueue<>();

    /**
     * 保存消息
     * 如果超出容量限制，自动移除最旧的消息
     *
     * @param message 要保存的消息
     */
    public void saveMessage(Message message) {
        // 添加新消息
        messages.offer(message);

        // 如果超出容量，移除最旧的消息
        while (messages.size() > MAX_SIZE) {
            messages.poll();
        }
    }

    /**
     * 获取最近的消息
     *
     * @param count 要获取的消息数量
     * @return 最近的消息列表（按时间从新到旧排序）
     */
    public List<Message> getRecentMessages(int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }

        int actualCount = Math.min(count, messages.size());
        List<Message> result = new ArrayList<>(actualCount);

        // 跳过前面的元素，只遍历需要的部分
        int skip = messages.size() - actualCount;
        int index = 0;
        for (Message msg : messages) {
            if (index >= skip) {
                result.add(msg);
            }
            index++;
        }

        // 反转列表，使最新的消息排在最前面
        Collections.reverse(result);
        return result;
    }

    /**
     * 获取当前存储的消息数量
     */
    public int size() {
        return messages.size();
    }

    /**
     * 清空所有消息
     */
    public void clear() {
        messages.clear();
    }
}
