package com.example.Server;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;

/**
 * ChatServer 生命周期功能测试
 *
 * 测试目标：验证服务器状态机转换的正确性
 * - 不涉及客户端交互
 * - 专注于服务器自身状态管理
 *
 * 状态转换图：
 * NEW -> RUNNING -> STOPPED
 *  ^______|
 * （支持重复启停）
 */
public class ChatServerLifecycleTest {

    private static final int BASE_PORT = 19000;
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
        if (server != null && (server.getState() == EventLoopBoss.State.RUNNING)) {
            server.shutdown();
            server.awaitTermination(5000);
        }
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 基本状态转换 ====================

    /**
     * 测试：正常启动和停止
     */
    @Test
    public void testStartAndStop() throws Exception {
        // 初始状态
        assertEquals("初始状态应该是 NEW", EventLoopBoss.State.NEW, server.getState());
        assertFalse("初始状态 isRunning 应该是 false", (server.getState() == EventLoopBoss.State.RUNNING));

        // 启动服务器
        server.start();

        // 验证启动后状态
        assertEquals("启动后状态应该是 RUNNING", EventLoopBoss.State.RUNNING, server.getState());
        assertTrue("启动后 isRunning 应该是 true", (server.getState() == EventLoopBoss.State.RUNNING));

        // 停止服务器
        server.stop();

        // 验证停止后状态
        assertEquals("停止后状态应该是 STOPPED", EventLoopBoss.State.STOPPED, server.getState());
        assertFalse("停止后 isRunning 应该是 false", (server.getState() == EventLoopBoss.State.RUNNING));
    }

    /**
     * 测试：启动是同步的（start() 返回时服务器已准备好接受连接）
     */
    @Test
    public void testStartIsSynchronous() throws Exception {
        long startTime = System.currentTimeMillis();

        server.start();

        long startupTime = System.currentTimeMillis() - startTime;

        assertEquals("状态应该是 RUNNING", EventLoopBoss.State.RUNNING, server.getState());
        assertTrue("启动应该很快完成，实际耗时：" + startupTime + "ms", startupTime < 3000);

        System.out.println("服务器同步启动耗时：" + startupTime + "ms");

        // 验证可以立即连接
        TestChatClient client = new TestChatClient("SyncTest", "localhost", testPort);
        try {
            client.connect();
            assertTrue("start() 返回后应该能立即连接", client.isConnected());
        } finally {
            client.close();
        }
    }

    /**
     * 测试：支持重复启停
     */
    @Test
    public void testRestart() throws Exception {
        // 第一次启动
        server.start();
        assertEquals(EventLoopBoss.State.RUNNING, server.getState());

        // 第一次停止
        server.stop();
        assertEquals(EventLoopBoss.State.STOPPED, server.getState());

        // 第二次启动（复用同一个实例）
        server.start();
        assertEquals(EventLoopBoss.State.RUNNING, server.getState());

        // 验证可以连接
        TestChatClient client = new TestChatClient("RestartTest", "localhost", testPort);
        try {
            client.connect();
            assertTrue("重启后应该能连接", client.isConnected());
        } finally {
            client.close();
        }

        // 第二次停止
        server.stop();
        assertEquals(EventLoopBoss.State.STOPPED, server.getState());
    }

    // ==================== 边界情况 ====================

    /**
     * 测试：重复启动应该抛出异常
     */
    @Test(expected = IllegalStateException.class)
    public void testDoubleStartThrowsException() throws Exception {
        server.start();
        server.start(); // 应该抛出 IllegalStateException
    }

    /**
     * 测试：停止未启动的服务应该安全
     */
    @Test
    public void testStopWithoutStart() {
        server.stop();
        assertEquals(EventLoopBoss.State.NEW, server.getState());
    }

    /**
     * 测试：重复停止应该安全
     */
    @Test
    public void testDoubleStop() throws Exception {
        server.start();
        server.stop();
        server.stop(); // 第二次停止应该安全
        assertEquals(EventLoopBoss.State.STOPPED, server.getState());
    }

    // ==================== 超时机制 ====================

    /**
     * 测试：启动超时机制
     */
    @Test
    public void testStartTimeoutMechanism() throws Exception {
        ChatServer serverWithZeroTimeout = new ChatServer(testPort, WORKER_COUNT, 0);
        try {
            serverWithZeroTimeout.start();
            fail("应该抛出 TimeoutException");
        } catch (TimeoutException e) {
            System.out.println("预期异常：启动超时 - " + e.getMessage());
        } finally {
            if (serverWithZeroTimeout.getState() == EventLoopBoss.State.RUNNING) {
                serverWithZeroTimeout.stop();
            }
        }
    }

    // ==================== 并发安全 ====================

    /**
     * 测试：并发启动应该安全（只有一个能成功）
     */
    @Test
    public void testConcurrentStart() throws Exception {
        final boolean[] results = new boolean[3];
        final Exception[] exceptions = new Exception[3];

        Thread[] threads = new Thread[3];
        for (int i = 0; i < 3; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    server.start();
                    results[index] = true;
                } catch (Exception e) {
                    exceptions[index] = e;
                }
            }, "Start-Thread-" + i);
        }

        for (Thread t : threads) {
            t.start();
        }

        for (Thread t : threads) {
            t.join(10000);
        }

        int successCount = 0;
        int illegalStateCount = 0;
        for (int i = 0; i < 3; i++) {
            if (results[i]) {
                successCount++;
            } else if (exceptions[i] instanceof IllegalStateException) {
                illegalStateCount++;
            }
        }

        assertEquals("只有一个线程能成功启动", 1, successCount);
        assertEquals("其他线程应该收到 IllegalStateException", 2, illegalStateCount);
        assertEquals("服务器状态应该是 RUNNING", EventLoopBoss.State.RUNNING, server.getState());
    }

    /**
     * 测试：并发停止应该安全
     */
    @Test
    public void testConcurrentStop() throws Exception {
        server.start();

        Thread[] threads = new Thread[3];
        for (int i = 0; i < 3; i++) {
            threads[i] = new Thread(() -> {
                server.stop();
            }, "Stop-Thread-" + i);
        }

        for (Thread t : threads) {
            t.start();
        }

        for (Thread t : threads) {
            t.join(10000);
        }

        assertEquals("服务器状态应该是 STOPPED", EventLoopBoss.State.STOPPED, server.getState());
    }
}
