package com.example.server;

import com.example.Client.ChatClient;
import com.example.Client.ClientListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatServer 基础功能测试（隔离版）
 * 
 * 测试隔离措施：
 * 1. 每个测试类使用随机端口（通过 TestPortUtil）
 * 2. Maven Surefire 配置每个测试类独立 JVM
 * 3. @BeforeEach / @AfterEach 确保资源清理
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ChatServerBasicTest {

    private static final Logger logger = LoggerFactory.getLogger(ChatServerBasicTest.class);

    private static final String TEST_HOST = "localhost";

    private int testPort;
    private ChatServer server;

    @BeforeEach
    void setUp() throws Exception {
        // 获取随机可用端口，避免与其他测试冲突
        testPort = TestPortUtil.findAvailablePort();
        logger.info("Using random port: {}", testPort);

        // 启动服务器
        server = new ChatServer(testPort);
        server.startAsync();
        server.awaitRunning(10, TimeUnit.SECONDS);
        logger.info("Test server started on port {}", testPort);

        // 等待服务器完全启动
        Thread.sleep(500);
    }

    @AfterEach
    void tearDown() throws Exception {
        // 停止服务器
        if (server != null && server.isRunning()) {
            server.stopAsync();
            try {
                server.awaitTerminated(10, TimeUnit.SECONDS);
                logger.info("Test server stopped gracefully");
            } catch (Exception e) {
                logger.warn("Server did not terminate in time: {}", e.getMessage());
            }
        }

        // 额外等待确保端口释放
        Thread.sleep(200);
    }

    /**
     * 测试：单个客户端连接和认证
     */
    @Test
    void testSingleClientConnect() throws Exception {
        String userId = "user1";
        ChatClient client = new ChatClient(TEST_HOST, testPort, userId);

        try {
            boolean success = client.start(10, TimeUnit.SECONDS);
            assertTrue(success, "Client should connect and identify");
            assertTrue(client.isReady(), "Client should be ready");
            logger.info("✅ Single client connect test passed");
        } finally {
            client.stop();
        }
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
                clients[i] = new ChatClient(TEST_HOST, testPort, "user" + i);
                boolean success = clients[i].start(10, TimeUnit.SECONDS);
                assertTrue(success, "Client " + i + " should connect");
            }

            // 验证所有客户端都就绪
            for (int i = 0; i < clientCount; i++) {
                assertTrue(clients[i].isReady(), "Client " + i + " should be ready");
            }

            logger.info("✅ Multiple clients connect test passed");
        } finally {
            for (ChatClient client : clients) {
                if (client != null) {
                    client.stop();
                }
            }
        }
    }

    /**
     * 测试：客户端断开连接
     */
    @Test
    void testClientDisconnect() throws Exception {
        String userId = "disconnectUser";
        ChatClient client = new ChatClient(TEST_HOST, testPort, userId);

        CountDownLatch disconnectLatch = new CountDownLatch(1);

        assertTrue(client.start(10, TimeUnit.SECONDS), "Client should connect");

        client.addClientListener(new ClientListener() {
            @Override
            public void onConnect() {
            }

            @Override
            public void onDisconnect() {
                logger.info("Disconnect callback triggered");
                disconnectLatch.countDown();
            }

            @Override
            public void onMessage(String message) {
            }
        });

        client.stop();

        boolean disconnected = disconnectLatch.await(5, TimeUnit.SECONDS);
        assertTrue(disconnected, "Should receive disconnect notification");
        assertFalse(client.isConnected(), "Client should not be connected");

        logger.info("✅ Client disconnect test passed");
    }

    /**
     * 测试：用户列表请求
     */
    @Test
    void testUserListRequest() throws Exception {
        String userId = "listUser";
        ChatClient client = new ChatClient(TEST_HOST, testPort, userId);

        CountDownLatch messageLatch = new CountDownLatch(1);
        AtomicBoolean receivedUserList = new AtomicBoolean(false);

        try {
            assertTrue(client.start(10, TimeUnit.SECONDS), "Client should connect");

            client.addClientListener(new ClientListener() {
                @Override
                public void onConnect() {
                }

                @Override
                public void onDisconnect() {
                }

                @Override
                public void onMessage(String message) {
                    logger.info("Received: {}", message);
                    if (message.contains("USER_LIST")) {
                        receivedUserList.set(true);
                        messageLatch.countDown();
                    }
                }
            });

            client.requestUserList();

            boolean received = messageLatch.await(5, TimeUnit.SECONDS);
            assertTrue(received, "Should receive user list");
            assertTrue(receivedUserList.get(), "Should receive USER_LIST message");

            logger.info("✅ User list request test passed");
        } finally {
            client.stop();
        }
    }

    /**
     * 测试：系统消息通知（用户加入）
     */
    @Test
    void testSystemNotification() throws Exception {
        String user1 = "firstUser";
        String user2 = "secondUser";

        ChatClient client1 = new ChatClient(TEST_HOST, testPort, user1);
        ChatClient client2 = new ChatClient(TEST_HOST, testPort, user2);

        CountDownLatch notificationLatch = new CountDownLatch(1);
        AtomicReference<String> notification = new AtomicReference<>();

        try {
            // 先启动 client1
            assertTrue(client1.start(10, TimeUnit.SECONDS), "Client1 should connect");

            // client1 监听系统消息
            client1.addClientListener(new ClientListener() {
                @Override
                public void onConnect() {
                }

                @Override
                public void onDisconnect() {
                }

                @Override
                public void onMessage(String message) {
                    logger.info("Client1 received: {}", message);
                    // 检查是否收到用户加入通知
                    if (message.contains("加入") || message.contains("joined")) {
                        notification.set(message);
                        notificationLatch.countDown();
                    }
                }
            });

            // 再启动 client2，应该触发 client1 的系统通知
            assertTrue(client2.start(10, TimeUnit.SECONDS), "Client2 should connect");

            boolean received = notificationLatch.await(5, TimeUnit.SECONDS);
            assertTrue(received, "Client1 should receive join notification");
            assertNotNull(notification.get(), "Notification should not be null");

            logger.info("✅ System notification test passed");
        } finally {
            client1.stop();
            client2.stop();
        }
    }
}
