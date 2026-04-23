package com.example.Server;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * 广播功能测试
 * 测试消息是否能正确广播给所有客户端
 *
 * 使用项目中定义的 {@link com.example.Client.ChatClient} 进行测试，
 * 避免重复实现协议解析逻辑。
 */
public class BroadcastTest {

    private static final int PORT = 9094; // 使用不同端口避免与其他测试冲突
    private static final int WORKER_COUNT = 2;
    private static final int CLIENT_COUNT = 3; // 减少客户端数量提高稳定性

    private ChatServer chatServer;

    @Before
    public void setUp() throws Exception {
        System.out.println("[BroadcastTest] 开始启动服务器...");
        
        // 使用 ChatServer 的阻塞式启动
        chatServer = new ChatServer(PORT, WORKER_COUNT);
        chatServer.start(); // 阻塞直到服务器准备好
        
        System.out.println("[BroadcastTest] 服务器启动完成，状态: " + chatServer.getState());
    }

    @After
    public void tearDown() throws Exception {
        System.out.println("\n[BroadcastTest] 开始清理...");
        
        // 停止服务器
        chatServer.stop();
        
        System.out.println("[BroadcastTest] 清理完成");
    }

    /**
     * 测试基础广播功能：一个客户端发送消息，其他客户端都能收到
     */
    @Test
    public void testBasicBroadcast() throws Exception {
        List<TestChatClient> clients = new ArrayList<>();

        try {
            // 创建多个客户端
            System.out.println("[Test] 创建 " + CLIENT_COUNT + " 个客户端...");
            for (int i = 0; i < CLIENT_COUNT; i++) {
                TestChatClient client = new TestChatClient("Client-" + i, "localhost", PORT);
                clients.add(client);
                client.connect();
                System.out.println("[Test] 客户端 " + i + " 已连接");
            }

            // 等待所有客户端连接完成（关键：服务器注册需要时间）
            System.out.println("[Test] 等待客户端就绪...");
            Thread.sleep(5000);

            // 第一个客户端发送消息
            String testMessage = "Hello Broadcast!";
            System.out.println("[Test] 客户端 0 发送消息: " + testMessage);
            clients.get(0).sendMessage(testMessage);

            // 等待消息广播
            System.out.println("[Test] 等待消息广播...");
            Thread.sleep(2000);

            // 验证其他客户端都收到了消息（除了发送者）
            System.out.println("[Test] 验证消息接收...");
            for (int i = 1; i < clients.size(); i++) {
                String received = clients.get(i).getLastMessage(2, TimeUnit.SECONDS);
                System.out.println("[Test] 客户端 " + i + " 收到: " + received);
                assertNotNull("客户端 " + i + " 应该收到消息", received);
                assertTrue("消息内容应该包含发送内容",
                    received.contains(testMessage) || received.trim().equals(testMessage.trim()));
            }

            // 验证发送者自己没有收到回传
            String senderReceived = clients.get(0).getLastMessage(500, TimeUnit.MILLISECONDS);
            System.out.println("[Test] 发送者收到: " + senderReceived);
            // 发送者不应该收到自己的消息（或者收到的是空）
            if (senderReceived != null) {
                assertFalse("发送者不应该收到自己的消息回传",
                    senderReceived.contains(testMessage));
            }

            System.out.println("[Test] 测试通过!");

        } finally {
            // 清理客户端
            System.out.println("[Test] 关闭客户端...");
            for (TestChatClient client : clients) {
                client.close();
            }
        }
    }
}
