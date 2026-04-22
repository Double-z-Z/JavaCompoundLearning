package com.example.Server;

import com.example.ioutils.IoUtils;
import com.example.message.BroadcastMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class ClientManager {

    private static final Logger logger = LoggerFactory.getLogger(ClientManager.class);
    private static final Logger perfLogger = LoggerFactory.getLogger("PERFORMANCE");

    private int port;

    private int workerCount;

    private Selector selector;

    private ServerSocketChannel channel;

    private ServerHandler[] workers;

    private ExecutorService workerExecutor;
    
    /**
     * 全局注册统计
     */
    private final LongAdder totalAcceptTime = new LongAdder();
    private final AtomicInteger acceptCount = new AtomicInteger(0);

    private volatile boolean isRunning;
    
    // 启动完成通知
    private final CompletableFuture<Void> readyFuture = new CompletableFuture<>();

    public ClientManager(int port, int workerCount) {
        this.port = port;
        this.workerCount = workerCount;
        this.workers = new ServerHandler[workerCount];
    }

    /**
     * 运行服务器（阻塞方法）

     */
    public void run() throws IOException, InterruptedException {
        if (isRunning) {
            return;
        }

        // 使用try-finally确保资源被关闭
        try {
            isRunning = true;
            selector = Selector.open();
            channel = ServerSocketChannel.open();
            channel.socket().bind(new InetSocketAddress(port));
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_ACCEPT);

            this.workerExecutor = Executors.newFixedThreadPool(workerCount);
            for (int i = 0; i < workerCount; i++) {
                ServerHandler newHandler = new ServerHandler(this).openSelector();
                workers[i] = newHandler;
                workerExecutor.execute(() -> {
                    try {
                        newHandler.run();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
            
            readyFuture.complete(null);

            int clinetId = 0;
            int selectCount = 0;
            // 主循环：只检查isRunning
            while (isRunning) {
                // 使用超时select，避免阻塞过久，提高注册响应速度
                int readyChannels = selector.select(100);
                selectCount++;
                
                // 每100次select输出一次调试信息
                if (selectCount % 100 == 0) {
                    System.out.println("[ClientManager] select循环运行中... selectCount=" + selectCount + ", acceptCount=" + acceptCount.get());
                }
                
                if (readyChannels <= 0) {
                    continue;
                }

                System.out.println("[ClientManager] select返回 " + readyChannels + " 个就绪通道");

                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    if (key.isAcceptable()) {
                        SocketChannel clientChannel;
                        while ((clientChannel = ((ServerSocketChannel) key.channel()).accept()) != null) {
                            long acceptStart = System.nanoTime();
                            boolean registerSuccess = false;
                            int workerIndex = -1;
                            
                            try {
                                clientChannel.configureBlocking(false);
                                workerIndex = clinetId++ % workerCount;
                                ServerHandler handler = workers[workerIndex];
                                handler.register(clientChannel);
                                registerSuccess = true;
                            } catch (IOException e) {
                                logger.error("注册客户端失败: {}", clientChannel, e);
                                // 关闭失败的连接
                                IoUtils.closeQuietly(clientChannel);
                            }
                            
                            // 统计（无论成功与否都统计，方便排查问题）
                            long acceptTime = System.nanoTime() - acceptStart;
                            totalAcceptTime.add(acceptTime);
                            int count = acceptCount.incrementAndGet();
                            
                            // 输出每次注册的详细信息（用于诊断）
                            String logMsg = "[ClientManager] 连接 #" + count + 
                                " (acceptCount=" + acceptCount.get() + ")" +
                                ", worker=" + workerIndex + 
                                ", 成功=" + registerSuccess + 
                                ", 耗时=" + (acceptTime / 1000) + "μs";
                            System.out.println(logMsg);
                            System.out.flush();
                            
                            // 每10个连接输出一次统计
                            if (count % 10 == 0) {
                                long avgTime = totalAcceptTime.sum() / count;
                                perfLogger.debug("全局注册统计: 总数={}, 成功={}, 平均耗时={}μs", 
                                    count, registerSuccess, avgTime / 1000);
                            }
                        }
                    }
                    iterator.remove();
                }
            }
        } finally {
            // 统一关闭资源
            isRunning = false;
            IoUtils.closeQuietly(channel, selector);
            if (workerExecutor != null) {
                workerExecutor.shutdown();
                try {
                    if (!workerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        workerExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    workerExecutor.shutdownNow();
                }
            }
        }
    }

    public void broadcast(ServerHandler from, BroadcastMessage message) {
        for (ServerHandler worker : workers) {
            if (worker != from) {
                worker.broadcast(message);
            }
        }
    }

    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * 获取启动完成的Future，用于异步等待服务器准备好
     */
    public CompletableFuture<Void> getReadyFuture() {
        return readyFuture;
    }
    
    /**
     * 获取当前接受的连接数
     */
    public int getAcceptCount() {
        return acceptCount.get();
    }

    /**
     * 获取全局注册统计信息
     */
    public String getGlobalRegisterStats() {
        int count = acceptCount.get();
        long totalTime = totalAcceptTime.sum();
        long avgTime = count > 0 ? totalTime / count : 0;
        
        System.out.println("[DEBUG] getGlobalRegisterStats: acceptCount=" + count + ", totalAcceptTime=" + totalTime);
        
        StringBuilder sb = new StringBuilder();
        sb.append("全局注册统计: 总数=").append(count)
          .append(", 总耗时=").append(totalTime / 1000).append("μs")
          .append(", 平均耗时=").append(avgTime / 1000.0).append("μs\n");
        
        for (int i = 0; i < workers.length; i++) {
            sb.append("  Worker-").append(i).append(": ")
              .append(workers[i].getRegisterStats()).append("\n");
        }
        
        return sb.toString();
    }

    public void shutdown() {
        System.out.println("[ClientManager] shutdown() 被调用");
        isRunning = false;
        
        // 1. 关闭 ServerSocketChannel，停止接受新连接
        if (channel != null) {
            try {
                channel.close();
                System.out.println("[ClientManager] ServerSocketChannel 已关闭");
            } catch (IOException e) {
                logger.error("关闭 ServerSocketChannel 失败", e);
            }
        }
        
        // 2. 唤醒 selector，让 run() 方法退出循环
        if (selector != null) {
            selector.wakeup();
        }
        
        // 3. 关闭所有 Worker 的 selector，让它们退出
        for (ServerHandler worker : workers) {
            if (worker != null) {
                worker.shutdown();
            }
        }
        
        System.out.println("[ClientManager] shutdown() 完成");
    }
}
