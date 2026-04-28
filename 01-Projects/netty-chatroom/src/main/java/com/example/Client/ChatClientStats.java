package com.example.Client;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 聊天客户端统计信息
 * 
 * 设计要点：
 * 1. 线程安全：使用 AtomicLong 保证并发安全
 * 2. 只统计可准确测量的指标（不统计延迟，因为需要服务端配合）
 * 3. 快照机制：getReport() 返回某一时刻的统计快照
 */
public class ChatClientStats {
    
    // 消息计数（AtomicLong 适合 CAS 操作）
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong messagesReceived = new AtomicLong(0);
    
    // 字节计数
    private final AtomicLong bytesSent = new AtomicLong(0);
    private final AtomicLong bytesReceived = new AtomicLong(0);
    
    // 开始时间
    private final long startTime = System.currentTimeMillis();
    
    /**
     * 记录发送消息
     * @param bytes 消息字节数
     */
    public void recordSent(int bytes) {
        messagesSent.incrementAndGet();
        bytesSent.addAndGet(bytes);
    }
    
    /**
     * 记录接收消息
     * @param bytes 消息字节数
     */
    public void recordReceived(int bytes) {
        messagesReceived.incrementAndGet();
        bytesReceived.addAndGet(bytes);
    }
    
    /**
     * 获取当前统计快照
     */
    public StatsReport getReport() {
        long currentTime = System.currentTimeMillis();
        long elapsedMs = currentTime - startTime;
        
        long sent = messagesSent.get();
        long received = messagesReceived.get();
        
        return new StatsReport(
            sent,
            received,
            bytesSent.get(),
            bytesReceived.get(),
            elapsedMs,
            elapsedMs > 0 ? (sent * 1000.0 / elapsedMs) : 0.0
        );
    }
    
    /**
     * 重置统计（谨慎使用）
     */
    public void reset() {
        messagesSent.set(0);
        messagesReceived.set(0);
        bytesSent.set(0);
        bytesReceived.set(0);
    }
    
    /**
     * 统计报告（不可变对象，线程安全）
     */
    public static class StatsReport {
        public final long messagesSent;
        public final long messagesReceived;
        public final long bytesSent;
        public final long bytesReceived;
        public final long elapsedMs;
        public final double throughputPerSecond;
        
        public StatsReport(long messagesSent, long messagesReceived,
                          long bytesSent, long bytesReceived,
                          long elapsedMs, double throughputPerSecond) {
            this.messagesSent = messagesSent;
            this.messagesReceived = messagesReceived;
            this.bytesSent = bytesSent;
            this.bytesReceived = bytesReceived;
            this.elapsedMs = elapsedMs;
            this.throughputPerSecond = throughputPerSecond;
        }
        
        @Override
        public String toString() {
            return String.format(
                "StatsReport{sent=%d, received=%d, bytesSent=%d, bytesReceived=%d, " +
                "elapsedMs=%d, throughput=%.2f msg/s}",
                messagesSent, messagesReceived, bytesSent, bytesReceived,
                elapsedMs, throughputPerSecond
            );
        }
    }
}
