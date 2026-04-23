package com.example.Client;

import com.example.message.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端消息处理器默认实现
 *
 * 可作为 ChatClient.MessageListener 的适配器使用，
 * 子类可重写感兴趣的方法。
 */
public class ClientHandler implements ChatClient.MessageListener {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    @Override
    public void onMessageReceived(ChatMessage message) {
        logger.info("[收到消息] {}: {}", message.getFrom(), message.getContent());
    }

    @Override
    public void onConnected() {
        logger.info("[连接成功] 已连接到服务端");
    }

    @Override
    public void onDisconnected() {
        logger.info("[连接断开] 与服务端的连接已关闭");
    }

    @Override
    public void onError(Exception e) {
        logger.error("[客户端错误] {}", e.getMessage(), e);
    }
}
