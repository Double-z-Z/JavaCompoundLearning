package com.example.Server;

import com.example.Client.ChatClient;
import com.example.message.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 测试用的 ChatClient 包装类，提供同步接口便于测试断言。
 *
 * 底层使用项目中的 {@link ChatClient}，避免测试重复实现协议解析。
 * 
 * 注意：ChatClient 本身已处理 SHUTDOWN_NOTICE 并自动断开，此处无需重复处理
 */
public class TestChatClient {

    private final String name;
    private final ChatClient client;
    private final List<String> receivedMessages = new ArrayList<>();
    private final CountDownLatch connectedLatch = new CountDownLatch(1);
    private volatile boolean connected = false;
    private final AtomicLong messagesSent = new AtomicLong(0);

    public TestChatClient(String name, String host, int port) {
        this.name = name;
        this.client = new ChatClient(host, port);
        this.client.setMessageListener(new ChatClient.MessageListener() {
            @Override
            public void onMessageReceived(ChatMessage message) {
                // ChatClient 本身已处理 SHUTDOWN_NOTICE 并自动断开
                // 此处只需记录消息内容
                String text = message.getContent();
                synchronized (receivedMessages) {
                    receivedMessages.add(text);
                }
                System.out.println(name + " 收到: " + text);
            }

            @Override
            public void onConnected() {
                connected = true;
                connectedLatch.countDown();
            }

            @Override
            public void onDisconnected() {
                connected = false;
            }

            @Override
            public void onError(Exception e) {
                System.err.println(name + " 错误: " + e.getMessage());
            }
        });
    }

    /**
     * 连接到服务端，并等待连接成功回调。
     */
    public void connect() throws Exception {
        client.connect();
        boolean success = connectedLatch.await(2, TimeUnit.SECONDS);
        if (!success) {
            throw new RuntimeException(name + " 连接超时");
        }
    }

    /**
     * 发送消息（使用项目中的长度前缀协议）。
     */
    public void sendMessage(String message) {
        client.sendMessage(message);
        messagesSent.incrementAndGet();
    }

    /**
     * 获取已发送消息数量。
     */
    public long getMessagesSent() {
        return messagesSent.get();
    }

    /**
     * 在指定超时时间内获取最后一条收到的消息。
     */
    public String getLastMessage(long timeout, TimeUnit unit) {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() < deadline) {
            synchronized (receivedMessages) {
                if (!receivedMessages.isEmpty()) {
                    return receivedMessages.get(receivedMessages.size() - 1);
                }
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    /**
     * 获取当前已收到的消息数量。
     */
    public int getMessageCount() {
        synchronized (receivedMessages) {
            return receivedMessages.size();
        }
    }

    /**
     * 获取所有收到的消息列表（拷贝）。
     */
    public List<String> getAllMessages() {
        synchronized (receivedMessages) {
            return new ArrayList<>(receivedMessages);
        }
    }

    /**
     * 优雅关闭客户端。
     */
    public void close() {
        connected = false;
        client.shutdown();
    }

    public boolean isConnected() {
        return connected;
    }
}
