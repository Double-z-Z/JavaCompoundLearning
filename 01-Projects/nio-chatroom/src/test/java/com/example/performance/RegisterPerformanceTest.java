package com.example.performance;

import com.example.Server.ChatServer;
import com.example.message.ChatMessage;
import com.example.message.ChatMessageEncoder;
import com.example.message.MessageType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * 客户端注册性能基准测试
 * 
 * 测试目标：测量服务器端客户端注册的实际耗时
 * 测试流程：连接 -> 发送消息 -> 接收响应 -> 关闭
 */
public class RegisterPerformanceTest {

    private static final int PORT = 9093;
    private static final int WORKER_COUNT = 2;
    
    // 测试参数
    private static final int CONNECTION_COUNT = 10;       // 总连接数
    private static final int WARMUP_COUNT = 3;            // 预热连接数
    private static final int RESPONSE_TIMEOUT_MS = 5000;  // 响应超时时间

    private ChatServer chatServer;

    @Before
    public void setUp() throws Exception {
        System.out.println("[Test] 开始启动服务器...");
        
        // 使用 ChatServer 的阻塞式启动
        chatServer = new ChatServer(PORT, WORKER_COUNT);
        chatServer.start(); // 阻塞直到服务器准备好
        
        System.out.println("[Test] 服务器启动完成，状态: " + chatServer.getState());
    }

    @After
    public void tearDown() throws Exception {
        System.out.println("\n[Test] 开始清理...");
        
        // 输出最终统计
        System.out.println("\n========== 服务器注册性能统计 ==========");
        System.out.println(chatServer.getGlobalRegisterStats());
        System.out.println("========================================\n");
        
        // 停止服务器
        chatServer.stop();
        
        System.out.println("[Test] 清理完成");
    }

    /**
     * 测试客户端注册性能
     */
    @Test
    public void testRegisterPerformance() throws Exception {
        System.out.println("========== 客户端注册性能测试 ==========");
        System.out.println("测试连接数: " + CONNECTION_COUNT);
        System.out.println("预热连接数: " + WARMUP_COUNT);
        
        // 预热阶段
        System.out.println("\n预热阶段...");
        for (int i = 0; i < WARMUP_COUNT; i++) {
            System.out.println("[Test] 预热连接 #" + (i + 1));
            createSendReceiveAndClose();
        }
        System.out.println("预热完成");
        
        // 正式测试
        System.out.println("\n正式测试开始...");
        long testStartTime = System.currentTimeMillis();
        
        for (int i = 0; i < CONNECTION_COUNT; i++) {
            System.out.println("[Test] 测试连接 #" + (i + 1));
            createSendReceiveAndClose();
        }
        
        long testTotalTime = System.currentTimeMillis() - testStartTime;
        
        System.out.println("\n测试完成!");
        System.out.println("总耗时: " + testTotalTime + " ms");
        System.out.println("平均每个连接: " + (testTotalTime * 1.0 / CONNECTION_COUNT) + " ms");
        
        // 等待统计数据稳定
        Thread.sleep(200);
    }

    /**
     * 创建连接，发送消息，接收响应，然后关闭
     */
    private void createSendReceiveAndClose() throws Exception {
        SocketChannel channel = null;
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 建立连接
            channel = SocketChannel.open();
            channel.configureBlocking(true);
            channel.connect(new InetSocketAddress("localhost", PORT));
            System.out.println("[Test] 连接成功: " + channel);
            
            // 2. 发送系统消息（用于测试服务器响应）
            ChatMessage message = new ChatMessage(MessageType.SYSTEM, "Ping", new java.util.Date(), "TestClient");
            byte[] encodedBytes = ChatMessageEncoder.encode(message);
            ByteBuffer buffer = ByteBuffer.wrap(encodedBytes);
            
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            System.out.println("[Test] 消息已发送");
            
            // 3. 等待并接收响应（使用非阻塞方式）
            channel.configureBlocking(false);
            ByteBuffer readBuffer = ByteBuffer.allocate(1024);
            
            // 轮询等待响应
            int waitCount = 0;
            int bytesRead = 0;
            while (bytesRead == 0 && waitCount < (RESPONSE_TIMEOUT_MS / 100)) {
                bytesRead = channel.read(readBuffer);
                if (bytesRead == 0) {
                    Thread.sleep(100);
                    waitCount++;
                }
            }
            
            if (bytesRead > 0) {
                readBuffer.flip();
                System.out.println("[Test] 收到服务器响应，共 " + bytesRead + " 字节");
            } else if (bytesRead == 0) {
                System.out.println("[Test] 警告: 等待响应超时（" + RESPONSE_TIMEOUT_MS + "ms）");
            } else {
                System.out.println("[Test] 警告: 服务器关闭连接");
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("[Test] 完整流程耗时: " + elapsed + " ms");
            
        } catch (Exception e) {
            System.err.println("[Test] 连接失败: " + e.getMessage());
            throw e;
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.close();
                System.out.println("[Test] 连接关闭");
            }
        }
    }
}
