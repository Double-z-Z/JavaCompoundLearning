package com.example.Server;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 简单测试：验证ClientManager关闭是否阻塞
 */
public class ClientManagerShutdownTest {

    private static final int TEST_PORT = 9998;

    @Test
    public void testShutdownDoesNotBlock() throws Exception {
        // 1. 创建并启动服务端
        ClientManager server = new ClientManager(TEST_PORT, 2);
        Thread serverThread = new Thread(() -> {
            try {
                server.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "Test-Server-Thread");
        serverThread.start();

        // 等待服务端启动
        Thread.sleep(2000);
        assertTrue("服务端应该已启动", server.isRunning());

        // 2. 记录关闭开始时间
        long startTime = System.currentTimeMillis();

        // 3. 关闭服务端
        server.shutdown();
        System.out.println("shutdown() 已调用");

        // 4. 等待线程结束（最多5秒）
        serverThread.join(5000);
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("关闭耗时: " + duration + " ms");
        System.out.println("线程是否存活: " + serverThread.isAlive());

        // 5. 验证
        assertFalse("服务端线程应该在5秒内结束", serverThread.isAlive());
        assertTrue("关闭应该在1秒内完成", duration < 1000);
    }
}
