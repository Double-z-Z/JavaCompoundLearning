package com.example.Server;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 客户端连接管理测试
 *
 * 测试目标：验证客户端连接的生命周期管理
 * - 客户端正常连接
 * - 客户端断开处理
 * - 断开后服务端状态恢复
 */
public class ClientConnectionTest {

    private static final int BASE_PORT = 19500;
    private static final int WORKER_COUNT = 2;

    private ChatServer server;
    private int testPort;

    @Before
    public void setUp() throws Exception {
        testPort = BASE_PORT + (int)(Math.random() * 1000);
        server = new ChatServer(testPort, WORKER_COUNT);
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        if (server != null && (server.getState() == EventLoopBoss.State.RUNNING)) {
            server.stop();
        }
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 测试：客户端能正常连接
     */
    @Test
    public void testClientCanConnect() throws Exception {
        TestChatClient client = new TestChatClient("TestClient", "localhost", testPort);
        try {
            client.connect();
            assertTrue("客户端应该连接成功", client.isConnected());
        } finally {
            client.close();
        }
    }

    /**
     * 测试：客户端断开连接后，服务端能正确处理
     *
     * 验证点：
     * 1. 客户端断开连接
     * 2. 服务端清理资源
     * 3. 服务端能接收新连接
     */
    @Test
    public void testClientDisconnectHandled() throws Exception {
        // 第一个客户端连接
        TestChatClient client1 = new TestChatClient("Client1", "localhost", testPort);
        client1.connect();
        assertTrue("客户端应该连接成功", client1.isConnected());

        // 发送消息后断开
        client1.sendMessage("Test message");
        Thread.sleep(100);

        // 断开连接
        client1.close();
        Thread.sleep(200);

        assertFalse("客户端应该已断开", client1.isConnected());

        // 验证服务端仍然正常运行（可以接收新连接）
        TestChatClient client2 = new TestChatClient("Client2", "localhost", testPort);
        try {
            client2.connect();
            assertTrue("新客户端应该能连接", client2.isConnected());
            client2.sendMessage("After disconnect");
            Thread.sleep(100);
        } finally {
            client2.close();
        }
    }

    /**
     * 测试：多个客户端依次连接和断开
     */
    @Test
    public void testMultipleClientsConnectAndDisconnect() throws Exception {
        int clientCount = 3;

        for (int i = 0; i < clientCount; i++) {
            TestChatClient client = new TestChatClient("Client-" + i, "localhost", testPort);
            try {
                // 连接
                client.connect();
                assertTrue("客户端" + i + " 应该连接成功", client.isConnected());

                // 发送消息
                client.sendMessage("Hello from " + i);
                Thread.sleep(50);

                // 断开
                client.close();
                Thread.sleep(50);

                assertFalse("客户端" + i + " 应该已断开", client.isConnected());
            } finally {
                client.close();
            }
        }

        // 验证服务端仍然可用
        TestChatClient finalClient = new TestChatClient("FinalClient", "localhost", testPort);
        try {
            finalClient.connect();
            assertTrue("最终客户端应该能连接", finalClient.isConnected());
        } finally {
            finalClient.close();
        }
    }
}
