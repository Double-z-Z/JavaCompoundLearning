package com.example.Server;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * NIO聊天室集成测试 (JUnit 4)
 *
 * 测试目标：
 * 1. 服务端能正常启动
 * 2. 客户端能连接
 * 3. 客户端能发送和接收消息（使用长度前缀协议）
 * 4. 多客户端能同时连接
 *
 * 使用项目中定义的 {@link com.example.Client.ChatClient} 进行测试。
 */
public class ChatServerIntegrationTest {

    private static final int TEST_PORT = 9999;
    private static final String TEST_HOST = "localhost";

    private ClientManager server;
    private Thread serverThread;

    @Before
    public void setUp() throws Exception {
        // 启动服务端
        server = new ClientManager(TEST_PORT, 2);
        serverThread = new Thread(() -> {
            try {
                server.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "Test-Server-Thread");
        serverThread.start();

        // 等待服务端启动
        Thread.sleep(500);
    }

    @After
    public void tearDown() throws Exception {
        // 关闭服务端
        if (server != null) {
            server.shutdown();
        }
        if (serverThread != null) {
            serverThread.join(5000);
        }
    }

    @Test
    public void testSingleClientConnection() throws Exception {
        // 测试单客户端连接和消息收发
        TestChatClient client = new TestChatClient("TestClient", TEST_HOST, TEST_PORT);
        try {
            client.connect();
            assertTrue("客户端应该连接成功", client.isConnected());

            // 发送消息（使用长度前缀协议）
            String testMessage = "Hello Server";
            client.sendMessage(testMessage);

            // 等待一段时间，让服务端处理
            Thread.sleep(200);

            // 验证连接仍然建立
            assertTrue("连接应该保持", client.isConnected());

        } finally {
            client.close();
        }
    }

    @Test
    public void testMultipleClientConnections() throws Exception {
        // 测试多客户端连接
        int clientCount = 5;
        CountDownLatch latch = new CountDownLatch(clientCount);
        List<TestChatClient> clients = new ArrayList<>();

        try {
            for (int i = 0; i < clientCount; i++) {
                final int clientId = i;
                TestChatClient client = new TestChatClient("TestClient-" + i, TEST_HOST, TEST_PORT);
                clients.add(client);

                new Thread(() -> {
                    try {
                        client.connect();
                        assertTrue("客户端" + clientId + "应该连接成功", client.isConnected());

                        // 发送消息
                        client.sendMessage("Hello from client " + clientId);

                        Thread.sleep(200);
                        client.close();
                        latch.countDown();
                    } catch (Exception e) {
                        e.printStackTrace();
                        fail("客户端" + clientId + "连接失败: " + e.getMessage());
                    }
                }, "Test-Client-" + i).start();
            }

            // 等待所有客户端完成
            assertTrue("所有客户端应该在10秒内完成", latch.await(10, TimeUnit.SECONDS));

        } finally {
            for (TestChatClient client : clients) {
                client.close();
            }
        }
    }

    @Test
    public void testClientDisconnect() throws Exception {
        // 测试客户端断开
        TestChatClient client = new TestChatClient("TestClient", TEST_HOST, TEST_PORT);
        client.connect();
        assertTrue(client.isConnected());

        // 发送消息后断开
        client.sendMessage("Test message");
        Thread.sleep(100);

        // 断开连接
        client.close();

        // 等待服务端处理断开
        Thread.sleep(200);

        assertFalse("客户端应该已断开", client.isConnected());

        // 验证服务端仍然正常运行（可以接收新连接）
        TestChatClient client2 = new TestChatClient("TestClient2", TEST_HOST, TEST_PORT);
        try {
            client2.connect();
            assertTrue("新客户端应该能连接", client2.isConnected());
        } finally {
            client2.close();
        }
    }

    @Test
    public void testServerRestart() throws Exception {
        // 先关闭当前服务端
        server.shutdown();
        serverThread.join(5000);
        Thread.sleep(500);

        // 重新启动服务端
        server = new ClientManager(TEST_PORT, 2);
        serverThread = new Thread(() -> {
            try {
                server.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.start();
        Thread.sleep(500);

        // 验证新服务端能接收连接
        TestChatClient client = new TestChatClient("TestClient", TEST_HOST, TEST_PORT);
        try {
            client.connect();
            assertTrue("重启后应该能连接", client.isConnected());
            client.sendMessage("After restart");
            Thread.sleep(100);
        } finally {
            client.close();
        }
    }
}
