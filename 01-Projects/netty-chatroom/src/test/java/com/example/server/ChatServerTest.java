package com.example.server;

import com.example.Client.ChatClient;
import com.example.Client.ClientListener;
import com.example.server.protocol.ChatMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatServer 基础功能测试
 */
public class ChatServerTest {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatServerTest.class);
    
    private static final int TEST_PORT = 9999;
    private static final String TEST_HOST = "localhost";
    
    private ChatServer server;
    
    @BeforeEach
    void setUp() throws Exception {
        // 启动服务器
        server = new ChatServer(TEST_PORT);
        server.startAsync();
        server.awaitRunning(5, TimeUnit.SECONDS);
        logger.info("Test server started on port {}", TEST_PORT);
        
        // 等待服务器完全启动
        Thread.sleep(500);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        // 停止服务器
        if (server != null && server.isRunning()) {
            server.stopAsync();
            server.awaitTerminated(5, TimeUnit.SECONDS);
            logger.info("Test server stopped");
        }
    }
    
    /**
     * 测试：客户端连接和认证
     */
    @Test
    void testClientConnectAndIdentify() throws Exception {
        String userId = "testUser1";
        ChatClient client = new ChatClient(TEST_HOST, TEST_PORT, userId);
        
        // 连接并认证
        boolean success = client.start(5, TimeUnit.SECONDS);
        
        assertTrue(success, "Client should connect and identify successfully");
        assertTrue(client.isConnected(), "Client should be connected");
        assertTrue(client.isIdentified(), "Client should be identified");
        assertEquals(userId, client.getUserId(), "User ID should match");
        
        // 清理
        client.stop();
    }
    
    /**
     * 测试：多个客户端连接
     */
    @Test
    void testMultipleClientsConnect() throws Exception {
        int clientCount = 3;
        ChatClient[] clients = new ChatClient[clientCount];
        
        try {
            for (int i = 0; i < clientCount; i++) {
                clients[i] = new ChatClient(TEST_HOST, TEST_PORT, "user" + i);
                boolean success = clients[i].start(5, TimeUnit.SECONDS);
                assertTrue(success, "Client " + i + " should connect successfully");
            }
            
            // 验证所有客户端都连接成功
            for (int i = 0; i < clientCount; i++) {
                assertTrue(clients[i].isReady(), "Client " + i + " should be ready");
            }
        } finally {
            // 清理
            for (ChatClient client : clients) {
                if (client != null) {
                    client.stop();
                }
            }
        }
    }
    
    /**
     * 测试：广播消息
     */
    @Test
    void testBroadcastMessage() throws Exception {
        String senderId = "sender";
        String receiverId = "receiver";
        String messageContent = "Hello, everyone!";

        ChatClient sender = new ChatClient(TEST_HOST, TEST_PORT, senderId);
        ChatClient receiver = new ChatClient(TEST_HOST, TEST_PORT, receiverId);

        CountDownLatch messageLatch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();

        try {
            // ✅ 先注册监听器（在连接前），避免错过消息
            receiver.addClientListener(new ClientListener() {
                @Override
                public void onConnect() {}

                @Override
                public void onDisconnect() {}

                @Override
                public void onMessage(String message) {
                    logger.info("Receiver got message: {}", message);
                    if (message.contains(messageContent)) {
                        receivedMessage.set(message);
                        messageLatch.countDown();
                    }
                }
            });

            // 启动两个客户端
            assertTrue(receiver.start(5, TimeUnit.SECONDS), "Receiver should connect first");
            assertTrue(sender.start(5, TimeUnit.SECONDS), "Sender should connect");

            // 等待连接完全建立
            Thread.sleep(200);

            // 发送者发送广播消息
            sender.sendPublicMessage(messageContent);

            // 等待接收者收到消息
            boolean received = messageLatch.await(5, TimeUnit.SECONDS);
            assertTrue(received, "Receiver should receive broadcast message");
            assertNotNull(receivedMessage.get(), "Received message should not be null");
            assertTrue(receivedMessage.get().contains(messageContent),
                "Received message should contain sent content");

        } finally {
            sender.stop();
            receiver.stop();
        }
    }
    
    /**
     * 测试：私聊消息
     */
    @Test
    void testPrivateMessage() throws Exception {
        String senderId = "privateSender";
        String receiverId = "privateReceiver";
        String messageContent = "Secret message!";

        ChatClient sender = new ChatClient(TEST_HOST, TEST_PORT, senderId);
        ChatClient receiver = new ChatClient(TEST_HOST, TEST_PORT, receiverId);

        CountDownLatch messageLatch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();

        try {
            // ✅ 先注册监听器（在连接前），避免错过消息
            receiver.addClientListener(new ClientListener() {
                @Override
                public void onConnect() {}

                @Override
                public void onDisconnect() {}

                @Override
                public void onMessage(String message) {
                    logger.info("Receiver got message: {}", message);
                    if (message.contains(messageContent)) {
                        receivedMessage.set(message);
                        messageLatch.countDown();
                    }
                }
            });

            // 启动两个客户端
            assertTrue(receiver.start(5, TimeUnit.SECONDS), "Receiver should connect first");
            assertTrue(sender.start(5, TimeUnit.SECONDS), "Sender should connect");

            // 等待连接完全建立
            Thread.sleep(200);

            // 发送者发送私聊消息
            sender.sendPrivateMessage(messageContent, receiverId);

            // 等待接收者收到消息
            boolean received = messageLatch.await(5, TimeUnit.SECONDS);
            assertTrue(received, "Receiver should receive private message");
            assertNotNull(receivedMessage.get(), "Received message should not be null");
            assertTrue(receivedMessage.get().contains(messageContent),
                "Received message should contain sent content");

        } finally {
            sender.stop();
            receiver.stop();
        }
    }
    
    /**
     * 测试：用户列表请求
     */
    @Test
    void testUserListRequest() throws Exception {
        String userId = "listUser";
        ChatClient client = new ChatClient(TEST_HOST, TEST_PORT, userId);
        
        CountDownLatch messageLatch = new CountDownLatch(1);
        AtomicBoolean receivedUserList = new AtomicBoolean(false);
        
        try {
            assertTrue(client.start(5, TimeUnit.SECONDS), "Client should connect");
            
            // 添加监听器
            client.addClientListener(new ClientListener() {
                @Override
                public void onConnect() {}
                
                @Override
                public void onDisconnect() {}
                
                @Override
                public void onMessage(String message) {
                    logger.info("Client got message: {}", message);
                    if (message.contains("USER_LIST")) {
                        receivedUserList.set(true);
                        messageLatch.countDown();
                    }
                }
            });
            
            // 请求用户列表
            client.requestUserList();
            
            // 等待响应
            boolean received = messageLatch.await(5, TimeUnit.SECONDS);
            assertTrue(received, "Should receive user list response");
            assertTrue(receivedUserList.get(), "Should receive USER_LIST message");
            
        } finally {
            client.stop();
        }
    }
    
    /**
     * 测试：连接断开
     */
    @Test
    void testDisconnect() throws Exception {
        String userId = "disconnectUser";
        ChatClient client = new ChatClient(TEST_HOST, TEST_PORT, userId);
        
        CountDownLatch disconnectLatch = new CountDownLatch(1);
        
        assertTrue(client.start(5, TimeUnit.SECONDS), "Client should connect");
        
        // 添加断开监听器
        client.addClientListener(new ClientListener() {
            @Override
            public void onConnect() {}
            
            @Override
            public void onDisconnect() {
                logger.info("Client disconnected");
                disconnectLatch.countDown();
            }
            
            @Override
            public void onMessage(String message) {}
        });
        
        // 断开连接
        client.stop();
        
        // 等待断开通知
        boolean disconnected = disconnectLatch.await(5, TimeUnit.SECONDS);
        assertTrue(disconnected, "Should receive disconnect notification");
        assertFalse(client.isConnected(), "Client should not be connected");
    }
}
