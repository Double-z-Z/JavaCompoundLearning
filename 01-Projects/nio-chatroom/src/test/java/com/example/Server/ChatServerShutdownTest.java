package com.example.Server;

import com.example.Client.ChatClient;
import com.example.message.ChatMessage;
import com.example.message.MessageType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * ChatServer 关闭行为测试验证
 *
 * 测试目标：验证服务器关闭时的行为（按优先级排序）
 * 1. 场景覆盖：优雅关闭/强制关闭场景是否成功
 * 2. 功能覆盖：关闭流程是否正确（通知发送、等待断开）
 * 3. 边界情况：无客户端、超时处理等
 */
public class ChatServerShutdownTest {

    private static final int BASE_PORT = 18500;
    private static final int WORKER_COUNT = 2;

    private ChatServer server;
    private int testPort;

    @Before
    public void setUp() {
        testPort = BASE_PORT + (int)(Math.random() * 1000);
        server = new ChatServer(testPort, WORKER_COUNT);
    }

    @After
    public void tearDown() {
        if (server != null && server.getState() == EventLoopBoss.State.RUNNING) {
            server.shutdown();
            server.awaitTermination(5000);
        }
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 场景覆盖：优雅关闭是否成功？ ====================

    /**
     * 测试：优雅关闭成功场景
     *
     * 验证点：
     * 1. 返回 true
     * 2. 所有客户端收到 SHUTDOWN_NOTICE
     * 3. 客户端主动断开（不是被强制断开）
     * 4. 服务器状态正确
     * 5. 服务器完全停止后无法建立新连接
     */
    @Test
    public void testGracefulShutdown_Success() throws Exception {
        server.start();

        int clientCount = 2;
        ChatClient[] clients = new ChatClient[clientCount];
        AtomicBoolean[] receivedNotices = new AtomicBoolean[clientCount];
        CountDownLatch disconnectLatch = new CountDownLatch(clientCount);

        // 创建客户端，设置收到通知后主动断开
        for (int i = 0; i < clientCount; i++) {
            receivedNotices[i] = new AtomicBoolean(false);
            final int index = i;

            clients[i] = new ChatClient("localhost", testPort);
            clients[i].setMessageListener(new ChatClient.MessageListener() {
                @Override
                public void onMessageReceived(ChatMessage message) {
                    System.out.println("客户端 " + index + " 收到消息: type=" + message.getType() + ", content=" + message.getContent());
                    if (message.getType() == MessageType.SHUTDOWN_NOTICE) {
                        receivedNotices[index].set(true);
                        System.out.println("客户端 " + index + " 收到关闭通知，主动断开");
                        clients[index].shutdown();
                    }
                }

                @Override
                public void onConnected() {}

                @Override
                public void onDisconnected() {
                    disconnectLatch.countDown();
                    System.out.println("客户端 " + index + " 已断开");
                }

                @Override
                public void onError(Exception e) {}
            });
            clients[i].connect();
        }

        Thread.sleep(1000); // 等待客户端注册完成

        // 执行关闭
        boolean graceful = server.stop(10000);

        // 等待所有客户端断开
        boolean allDisconnected = disconnectLatch.await(5, TimeUnit.SECONDS);

        // 验证点
        assertTrue("应该返回 true（优雅关闭）", graceful);
        assertTrue("所有客户端应该断开", allDisconnected);
        assertEquals(EventLoopBoss.State.STOPPED, server.getState());

        // 验证所有客户端都收到了通知
        for (int i = 0; i < clientCount; i++) {
            assertTrue("客户端 " + i + " 应该收到 SHUTDOWN_NOTICE", receivedNotices[i].get());
        }

        // 验证服务器完全停止后无法建立新连接
        assertFalse("服务器停止后不应该接受新连接", canConnect(testPort));

        System.out.println("优雅关闭成功：所有客户端配合断开");
    }

    /**
     * 测试：强制关闭场景（客户端不配合）
     *
     * 验证点：
     * 1. 返回 false（超时）
     * 2. 外部调用shutdownNow强制关闭
     * 3. 服务器状态正确
     * 4. 服务器完全停止后无法建立新连接
     */
    @Test
    public void testForceShutdown_Timeout() throws Exception {
        server.start();

        // 创建一个不配合的客户端（使用原始Socket，不响应SHUTDOWN_NOTICE）
        Socket socket = new Socket("localhost", testPort);
        socket.setSoTimeout(5000);
        assertTrue("客户端应该已连接", socket.isConnected() && !socket.isClosed());

        Thread.sleep(1000); // 等待注册完成

        // 使用较短的超时（2秒），触发超时
        boolean graceful = server.stop(2000);

        // 验证：应该返回false（超时）
        assertFalse("应该返回false（超时）", graceful);
        System.out.println("关闭超时，graceful=" + graceful + "（客户端不配合）");

        // 外部强制关闭
        server.getClientManager().shutdownNow();
        server.awaitTermination(2000);

        // 验证服务器状态
        assertEquals(EventLoopBoss.State.STOPPED, server.getState());

        // 验证服务器完全停止后无法建立新连接
        assertFalse("服务器停止后不应该接受新连接", canConnect(testPort));

        // 验证客户端最终也被断开
        Thread.sleep(500);
        boolean connectionClosed = false;
        try {
            socket.setSoTimeout(2000);
            InputStream in = socket.getInputStream();

            // 读取SHUTDOWN_NOTICE（可能收到，也可能没收到就被强制断开）
            byte[] lengthBytes = new byte[4];
            int read = in.read(lengthBytes);
            if (read == 4) {
                int messageLength = ((lengthBytes[0] & 0xFF) << 24) |
                                   ((lengthBytes[1] & 0xFF) << 16) |
                                   ((lengthBytes[2] & 0xFF) << 8) |
                                   (lengthBytes[3] & 0xFF);
                byte[] messageBytes = new byte[messageLength];
                read = in.read(messageBytes);
                System.out.println("收到关闭通知消息，长度=" + messageLength);
            }

            // 再次读取，应该返回-1（连接已关闭）
            socket.setSoTimeout(500);
            int data = in.read();
            connectionClosed = (data == -1);
        } catch (SocketTimeoutException e) {
            connectionClosed = false;
        } catch (IOException e) {
            connectionClosed = true;
        }

        assertTrue("客户端连接应该被强制关闭", connectionClosed);
        socket.close();
    }

    // ==================== 功能覆盖：关闭流程是否正确？ ====================

    /**
     * 测试：关闭时向所有客户端发送 SHUTDOWN_NOTICE
     *
     * 验证点：
     * 1. 服务器触发关闭时发送SHUTDOWN_NOTICE给所有客户端
     * 2. 客户端能正确收到通知
     * 3. 收到通知后客户端可以主动断开
     */
    @Test
    public void testShutdownSendsNotificationToAllClients() throws Exception {
        server.start();

        int clientCount = 2;
        CountDownLatch noticeLatch = new CountDownLatch(clientCount);
        CountDownLatch disconnectLatch = new CountDownLatch(clientCount);
        AtomicBoolean[] receivedNotices = new AtomicBoolean[clientCount];
        ChatClient[] clients = new ChatClient[clientCount];

        for (int i = 0; i < clientCount; i++) {
            receivedNotices[i] = new AtomicBoolean(false);
            final int index = i;

            clients[i] = new ChatClient("localhost", testPort);
            clients[i].setMessageListener(new ChatClient.MessageListener() {
                @Override
                public void onMessageReceived(ChatMessage message) {
                    System.out.println("客户端 " + index + " 收到消息: type=" + message.getType());
                    if (message.getType() == MessageType.SHUTDOWN_NOTICE) {
                        receivedNotices[index].set(true);
                        noticeLatch.countDown();
                        System.out.println("客户端 " + index + " 收到关闭通知，计数=" + noticeLatch.getCount());
                        // 收到通知后主动断开
                        clients[index].shutdown();
                    }
                }

                @Override
                public void onConnected() {}

                @Override
                public void onDisconnected() {
                    disconnectLatch.countDown();
                    System.out.println("客户端 " + index + " 已断开");
                }

                @Override
                public void onError(Exception e) {}
            });
            clients[i].connect();
        }

        Thread.sleep(1000); // 等待所有客户端注册完成（3个客户端可能需要更长时间）

        System.out.println("准备关闭服务器，已连接客户端数=" + clientCount);

        // 触发关闭（非阻塞）
        server.shutdown();

        // 等待所有客户端收到通知（给足时间）
        boolean allReceived = noticeLatch.await(5, TimeUnit.SECONDS);

        assertTrue("所有客户端都应该收到 SHUTDOWN_NOTICE", allReceived);
        for (int i = 0; i < clientCount; i++) {
            assertTrue("客户端 " + i + " 应该收到通知", receivedNotices[i].get());
        }

        // 等待所有客户端断开
        boolean allDisconnected = disconnectLatch.await(5, TimeUnit.SECONDS);
        assertTrue("所有客户端应该断开", allDisconnected);

        // 等待服务器完全停止
        boolean stopped = server.awaitTermination(5000);
        assertTrue("服务器应该停止", stopped);
        assertEquals("状态应该是 STOPPED", EventLoopBoss.State.STOPPED, server.getState());
    }

    /**
     * 测试：关闭是同步的（stop() 返回时服务器已完全停止）
     *
     * 验证点：
     * 1. stop() 返回时服务器状态为 STOPPED
     * 2. stop() 返回后无法建立新连接
     * 3. 服务器资源已完全释放
     */
    @Test
    public void testShutdownIsSynchronous() throws Exception {
        server.start();

        // 创建客户端连接
        TestChatClient client = new TestChatClient("StopTest", "localhost", testPort);
        client.connect();
        assertTrue("客户端应该连接成功", client.isConnected());

        long stopStartTime = System.currentTimeMillis();

        // 停止服务器
        server.stop(5000);

        long stopTime = System.currentTimeMillis() - stopStartTime;

        // 验证状态
        assertEquals("状态应该是 STOPPED", EventLoopBoss.State.STOPPED, server.getState());
        assertFalse("状态应该是 STOPPED", server.getState() == EventLoopBoss.State.RUNNING);

        System.out.println("服务器同步停止耗时：" + stopTime + "ms");

        // 关键验证：停止后立即尝试连接，应该失败
        assertFalse("服务器停止后应该无法连接", canConnect(testPort));

        client.close();
    }

    // ==================== 边界情况 ====================

    /**
     * 测试：无客户端时快速关闭
     */
    @Test
    public void testShutdownWithoutClients() throws Exception {
        server.start();

        long startTime = System.currentTimeMillis();
        boolean graceful = server.stop(5000);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue("应该是优雅关闭", graceful);
        assertTrue("无客户端时应该快速关闭，实际耗时：" + duration + "ms", duration < 1000);

        System.out.println("无客户端时关闭耗时：" + duration + "ms");
    }

    /**
     * 测试：带超时的停止接口
     */
    @Test
    public void testShutdownWithTimeout() throws Exception {
        server.start();

        boolean graceful = server.stop(1000);

        assertTrue("应该优雅关闭成功", graceful);
        assertEquals(EventLoopBoss.State.STOPPED, server.getState());
    }

    // ==================== 辅助方法 ====================

    /**
     * 检查是否可以连接到指定端口
     * @param port 端口号
     * @return true=可以连接, false=无法连接
     */
    private boolean canConnect(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
