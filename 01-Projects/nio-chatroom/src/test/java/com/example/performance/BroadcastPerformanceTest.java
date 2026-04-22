package com.example.performance;

import com.example.Server.ChatServer;
import com.example.Server.TestChatClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;

/**
 * 广播性能测试
 * 用于对比不同方案的性能差异（内存、CPU）
 *
 * 使用项目中定义的 {@link com.example.Client.ChatClient} 进行测试，
 * 避免重复实现协议解析逻辑。
 *
 * ⚠️ 注意：由于N^2消息复杂度，不要设置过大的客户端数量
 */
public class BroadcastPerformanceTest {

    private static final int PORT = 9092;
    private static final int WORKER_COUNT = 2;

    // 性能测试参数（保守设置，避免内存溢出）
    private static final int CLIENT_COUNT = 10;      // 客户端数量
    private static final int MESSAGE_COUNT = 50;     // 每个客户端发送消息数
    private static final int MESSAGE_SIZE = 100;     // 消息大小（字节）

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
     * 测试广播性能：测量吞吐量、延迟、内存使用
     *
     * N^2复杂度说明：
     * - N个客户端，每个发送M条消息
     * - 总消息数 = N * M * (N - 1) 条广播消息
     * - 10客户端 * 50条 * 9 = 4500条广播消息
     */
    @Test
    public void testBroadcastPerformance() throws Exception {
        System.out.println("========== 广播性能测试 ==========");
        System.out.println("客户端数量: " + CLIENT_COUNT);
        System.out.println("每客户端消息数: " + MESSAGE_COUNT);
        System.out.println("消息大小: " + MESSAGE_SIZE + " bytes");

        // 计算理论消息数
        long totalBroadcastMessages = (long) CLIENT_COUNT * MESSAGE_COUNT * (CLIENT_COUNT - 1);
        System.out.println("理论广播消息总数: " + totalBroadcastMessages);
        System.out.println("==================================");

        // 记录初始内存
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        Thread.sleep(1000);
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        List<TestChatClient> clients = new ArrayList<>();

        try {
            // 创建所有客户端
            long connectStart = System.currentTimeMillis();
            for (int i = 0; i < CLIENT_COUNT; i++) {
                TestChatClient client = new TestChatClient("Client-" + i, "localhost", PORT);
                clients.add(client);
                client.connect();
                if (i % 5 == 0) {
                    Thread.sleep(100); // 避免连接过快
                }
            }
            long connectTime = System.currentTimeMillis() - connectStart;
            System.out.println("客户端连接耗时: " + connectTime + " ms");

            // 等待所有客户端就绪
            Thread.sleep(1000);

            // 生成测试消息
            String testMessage = generateMessage(MESSAGE_SIZE);

            // 开始性能测试
            long testStart = System.currentTimeMillis();

            // 所有客户端同时发送消息
            for (int msgIdx = 0; msgIdx < MESSAGE_COUNT; msgIdx++) {
                for (TestChatClient client : clients) {
                    client.sendMessage(testMessage);
                }
                // 控制发送速率，避免瞬间压垮服务器
                if (msgIdx % 10 == 0) {
                    Thread.sleep(10);
                }
            }

            // 等待消息广播完成
            Thread.sleep(2000);

            long testDuration = System.currentTimeMillis() - testStart;

            // 记录内存使用
            System.gc();
            Thread.sleep(1000);
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = (memoryAfter - memoryBefore) / 1024 / 1024; // MB

            // 统计结果
            long totalMessagesSent = 0;
            long totalMessagesReceived = 0;

            for (TestChatClient client : clients) {
                totalMessagesSent += client.getMessagesSent();
                totalMessagesReceived += client.getMessageCount();
            }

            // 输出性能报告
            System.out.println("\n========== 性能测试结果 ==========");
            System.out.println("测试耗时: " + testDuration + " ms");
            System.out.println("发送消息数: " + totalMessagesSent);
            System.out.println("接收消息数: " + totalMessagesReceived);
            System.out.println("消息到达率: " + String.format("%.2f%%",
                (double) totalMessagesReceived / totalBroadcastMessages * 100));
            System.out.println("内存使用: " + memoryUsed + " MB");
            System.out.println("广播吞吐量: " + String.format("%.2f", totalBroadcastMessages * 1000.0 / testDuration) + " 消息/秒");
            System.out.println("==================================");

            // 基本断言
            assertTrue("应该发送了足够数量的消息", totalMessagesSent >= CLIENT_COUNT * MESSAGE_COUNT * 0.9);
            assertTrue("应该收到大部分消息", totalMessagesReceived >= totalBroadcastMessages * 0.7);

        } finally {
            // 清理
            for (TestChatClient client : clients) {
                client.close();
            }
        }
    }

    /**
     * 测试消息复制开销
     * 对比不同消息大小下的性能差异
     */
    @Test
    public void testMessageCopyOverhead() throws Exception {
        System.out.println("========== 消息复制开销测试 ==========");

        int[] messageSizes = {50, 100, 200, 500}; // 不同消息大小
        int testClientCount = 5;
        int testMessageCount = 20;

        for (int msgSize : messageSizes) {
            System.out.println("\n消息大小: " + msgSize + " bytes");

            List<TestChatClient> clients = new ArrayList<>();

            try {
                // 创建客户端
                for (int i = 0; i < testClientCount; i++) {
                    TestChatClient client = new TestChatClient("Client-" + i, "localhost", PORT);
                    clients.add(client);
                    client.connect();
                }
                Thread.sleep(500);

                String message = generateMessage(msgSize);
                long start = System.currentTimeMillis();

                // 发送消息
                for (int i = 0; i < testMessageCount; i++) {
                    for (TestChatClient client : clients) {
                        client.sendMessage(message);
                    }
                }

                Thread.sleep(1000);
                long duration = System.currentTimeMillis() - start;

                long totalMessages = (long) testClientCount * testMessageCount * (testClientCount - 1);
                double throughput = totalMessages * 1000.0 / duration;

                System.out.println("  耗时: " + duration + " ms");
                System.out.println("  吞吐量: " + String.format("%.2f", throughput) + " 消息/秒");

            } finally {
                for (TestChatClient client : clients) {
                    client.close();
                }
                Thread.sleep(500); // 等待清理
            }
        }

        System.out.println("==================================");
    }

    /**
     * 生成指定大小的测试消息
     */
    private String generateMessage(int size) {
        StringBuilder sb = new StringBuilder();
        sb.append("MSG:");
        for (int i = 0; i < size - 4; i++) {
            sb.append('X');
        }
        return sb.toString();
    }
}
