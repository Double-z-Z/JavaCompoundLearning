package com.example.Server;

import com.example.message.BroadcastMessage;
import com.example.message.ChatMessage;
import com.example.ioutils.IoUtils;
import com.example.message.ChatMessageDecoder;
import com.example.message.ChatMessageEncoder;
import com.example.message.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 客户业务处理类，分担boss分配的客户端，负责这些客户的消息读写服务
 */
public class EventLoopWorker {

    private static final Logger logger = LoggerFactory.getLogger(EventLoopWorker.class);
    private static final Logger perfLogger = LoggerFactory.getLogger("PERFORMANCE");

    private EventLoopBoss boss;

    private Selector selector;

    private List<SocketChannel> channels = new CopyOnWriteArrayList<>();

    /**
     * 客户端上下文映射表，用于存储每个客户端的消息缓冲区
     */
    private Map<SocketChannel, ClientContext> clientContextMap = new ConcurrentHashMap<>();

    /**
     * 消息队列 - 存储待广播的消息
     */
    private ConcurrentLinkedQueue<BroadcastMessage> messageQueue = new ConcurrentLinkedQueue<>();

    /**
     * Worker 状态枚举
     * 参考 ChatServer.State 设计
     */
    public enum State {
        NEW,        // 新建状态
        RUNNING,    // 运行中（处理 I/O 和消息）
        SHUTTING_DOWN,  // 正在关闭（不再接受新消息，处理队列剩余消息，等待客户端断开）
        STOPPED     // 已停止
    }

    private volatile State state = State.NEW;

    /**
     * 停止完成通知 - 类似 ClientManager 的 shutdownFuture
     */
    private final CompletableFuture<Void> stopFuture = new CompletableFuture<>();

    /**
     * 注册统计 - 使用LongAdder减少并发竞争
     */
    private final LongAdder totalRegisterTime = new LongAdder();
    private final AtomicInteger registerCount = new AtomicInteger(0);

    public EventLoopWorker(EventLoopBoss boss) {
        this.boss = boss;
    }

    public EventLoopWorker openSelector() throws IOException {
        this.selector = Selector.open();
        return this;
    }

    /**
     * 运行服务器处理线程，监听客户端的读写服务请求, 直到服务关闭。
     *
     * 主循环：RUNNING阶段处理I/O和消息，SHUTTING_DOWN阶段等待客户端断开
     *
     * @throws IOException
     */
    public void run() throws IOException {
        this.state = State.RUNNING;
        logger.info("Worker启动，开始处理客户端请求");

        try {
            // 主循环：运行中 或 正在关闭但还有客户端连接
            while (state == State.RUNNING || (state == State.SHUTTING_DOWN && !channels.isEmpty())) {
                int readyChannels = selector.select(100);

                if (readyChannels > 0) {
                    // 处理所有就绪的I/O事件
                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        if (key.isValid() && key.isReadable()) {
                            handleRead(key);
                        }
                        if (key.isValid() && key.isWritable()) {
                            handleWrite(key);
                        }
                        iterator.remove();
                    }
                }

                // 处理广播消息队列
                handleMessageQueue();
            }

            logger.info("Worker主循环退出，state={}, 剩余客户端数={}", state, channels.size());

        } finally {
            // 统一关闭资源
            shutdownNow();
        }
    }

    /**
     * 触发关闭流程（非阻塞）
     * 与 ExecutorService.shutdown() 语义一致
     *
     * 流程：
     * 1. 进入 SHUTTING_DOWN 状态（不再接受新消息）
     * 2. 立即发送 SHUTDOWN_NOTICE 给所有客户端（不排队）
     * 3. 唤醒 Selector，让 Worker 处理剩余消息和客户端断开
     *
     * 注意：资源释放在 run() 的 finally 块中完成
     * 注意：此方法不等待，需要调用 awaitTermination() 等待完成
     */
    public void shutdown() {
        logger.info("shutdown 被调用，state={}, channels.size()={}",
            state, channels.size());

        if (state != State.RUNNING) {
            logger.debug("Worker 不在运行状态，当前 state={}", state);
            return;
        }

        // 进入 SHUTTING_DOWN 状态（不再接受新消息）
        state = State.SHUTTING_DOWN;

        // 发送关闭通知给所有客户端（通过消息队列）
        ChatMessage shutdownMessage = new ChatMessage(
            MessageType.SHUTDOWN_NOTICE,
            "服务器即将关闭，请保存您的数据",
            new Date(),
            "SYSTEM"
        );
        // 通过广播消息队列发送，让Worker按正常流程处理
        broadcast(new BroadcastMessage(shutdownMessage, null));

        logger.info("关闭通知已加入消息队列，等待客户端断开，当前客户端数: {}", channels.size());
    }

    /**
     * 关闭服务器，关闭所有注册的客户端的连接，并释放所有资源。
     * 同时通知等待的线程（通过 stopFuture）。
     */
    public void shutdownNow() {
        logger.info("Worker 开始关闭资源");

        state = State.STOPPED;

        // 关闭所有客户端连接
        for (SocketChannel channel : channels) {
            IoUtils.closeQuietly(channel);
        }
        channels.clear();
        clientContextMap.clear();

        if (selector != null) {
            IoUtils.closeQuietly(selector);
            selector = null;
        }

        // 通知等待的线程
        stopFuture.complete(null);
        logger.info("Worker 资源清理完成，stopFuture 已通知");
    }

    /**
     * 等待 Worker 完全停止（阻塞方法）
     * 与 ExecutorService.awaitTermination() 语义一致
     * 
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return true=关闭成功, false=超时
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        try {
            stopFuture.get(timeout, unit);
            logger.info("Worker 已停止");
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            logger.warn("等待 Worker 停止超时或异常", e);
            return false;
        }
    }

    /**
     * 等待 Worker 完全停止（阻塞方法）- 便捷方法，默认毫秒
     * 
     * @param timeoutMs 超时时间（毫秒）
     * @return true=关闭成功, false=超时
     */
    public boolean awaitTermination(long timeoutMs) {
        return awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 注册客户端到本线程，负责该客户的读写服务，直到客户端关闭连接或者服务关闭。
     *
     * @param channel 客户端channel
     */
    public void register(SocketChannel channel) throws ClosedChannelException {
        long startTime = System.nanoTime();
        
        // 先准备上下文，确保channel注册后上下文已就绪
        ClientContext context = new ClientContext();
        channels.add(channel);
        clientContextMap.put(channel, context);
        // 最后注册到selector，避免竞态条件
        channel.register(selector, SelectionKey.OP_READ);
        // 唤醒selector，确保新注册的channel能被立即监听
        selector.wakeup();
        
        // 统计注册耗时
        long registerTime = System.nanoTime() - startTime;
        totalRegisterTime.add(registerTime);
        int count = registerCount.incrementAndGet();
        
        // 记录性能日志（每10个客户端输出一次统计）
        if (count % 10 == 0) {
            long avgTime = totalRegisterTime.sum() / count;
            perfLogger.debug("注册统计: 总数={}, 平均耗时={}ms", count, avgTime / 1_000_000);
        }
        
        // DEBUG级别记录每次注册
        logger.debug("客户端注册完成 #{}: channel={}, 耗时={}ms", 
            count, channel, registerTime / 1_000_000);
    }

    /**
     * 处理读事件
     *
     * 协议格式：[4字节消息长度(大端)][消息内容]
     * 循环处理缓冲区中所有完整的消息，使用 compact() 保留未处理的数据
     */
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientContext context = clientContextMap.get(channel);
        ByteBuffer buffer = context.getBuffer();

        // 读取数据，并自动扩容
        int bytesRead;
        do {
            bytesRead = channel.read(buffer);
            buffer = context.extendBuffer();
        } while (context.extendable());

        // 客户端关闭连接
        if (bytesRead == -1) {
            closeClient(channel, key);
            return;
        }

        if (bytesRead > 0) {
            buffer.flip();

            // 循环处理缓冲区中所有完整的消息
            while (buffer.remaining() >= 4) {
                buffer.mark();
                int messageLength = buffer.getInt();

                // 防御非法长度
                if (messageLength < 0 || messageLength > ClientContext.MAX_BUFFER_SIZE) {
                    logger.warn("收到非法消息长度: {}", messageLength);
                    closeClient(channel, key);
                    return;
                }

                // 若消息不完整，等待更多数据
                if (messageLength > buffer.remaining()) {
                    buffer.reset();
                    break;
                }

                // 读取消息内容
                byte[] messageBytes = new byte[messageLength];
                buffer.get(messageBytes);

                // 解码并处理消息
                try {
                    ChatMessage message = ChatMessageDecoder.decode(messageBytes);
                    logger.debug("收到消息: type={}, from={}, content={}", 
                        message.getType(), message.getFrom(), message.getContent());

                    // 只处理普通消息，系统消息（如SHUTDOWN_NOTICE）由客户端处理
                    if (message.getType() == MessageType.MSG) {
                        // 广播给其他客户端
                        BroadcastMessage broadcastMessage = new BroadcastMessage(message, channel);
                        messageQueue.offer(broadcastMessage);
                        boss.broadcast(this, broadcastMessage);
                    }
                } catch (Exception e) {
                    logger.error("消息解码失败", e);
                }
            }

            // 保留未处理的数据
            buffer.compact();
        }
    }

    /**
     * 处理写事件
     */
    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientContext context = clientContextMap.get(channel);

        // 获取当前正在发送的写缓冲区，或从队列中取新消息
        ByteBuffer writeBuffer = context.getWriteBuffer();
        if (writeBuffer == null || !writeBuffer.hasRemaining()) {
            // 开始发送新消息
            byte[] messageBytes = context.pollPendingMessage();
            if (messageBytes == null) {
                // 没有数据要写，取消写兴趣
                key.interestOps(SelectionKey.OP_READ);
                return;
            }
            writeBuffer = ByteBuffer.wrap(messageBytes);
            context.setWriteBuffer(writeBuffer);
        }

        int bytesWritten = channel.write(writeBuffer);
        logger.debug("写入 {} 字节到 {}", bytesWritten, channel);

        if (!writeBuffer.hasRemaining()) {
            // 写完了，清空writeBuffer
            context.setWriteBuffer(null);
            // 如果没有更多消息，取消写兴趣
            if (!context.hasPendingMessages()) {
                key.interestOps(SelectionKey.OP_READ);
            }
        }
        // 如果还有数据没写完，保持写兴趣（writeBuffer还在context中，下次继续发送）
    }

    /**
     * 处理消息队列中的广播消息
     */
    private void handleMessageQueue() {
        BroadcastMessage broadcastMessage;
        while ((broadcastMessage = messageQueue.poll()) != null) {
            ChatMessage message = broadcastMessage.chatMessage;
            SocketChannel from = broadcastMessage.source;

            logger.debug("处理广播消息: type={}, channels.size()={}", message.getType(), channels.size());

            // 只编码一次消息，避免重复编码
            byte[] messageBytes = ChatMessageEncoder.encode(message);

            for (SocketChannel channel : channels) {
                // 不发送给发送者自己
                if (channel == from) {
                    continue;
                }

                try {
                    if (channel.isOpen() && channel.isConnected()) {
                        // 使用队列方式发送，避免阻塞
                        ClientContext context = clientContextMap.get(channel);
                        if (context != null) {
                            // 为每个客户端复制一份消息，避免共享问题
                            context.pushPendingMessage(messageBytes.clone());
                            // 注册写兴趣
                            SelectionKey key = channel.keyFor(selector);
                            if (key != null && key.isValid()) {
                                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                                logger.debug("已向客户端 {} 推送消息，注册写兴趣", channel);
                            } else {
                                logger.warn("无法为客户端 {} 注册写兴趣: key={}", channel, key);
                            }
                        } else {
                            logger.warn("客户端 {} 没有对应的ClientContext", channel);
                        }
                    } else {
                        logger.debug("客户端 {} 未连接，跳过", channel);
                    }
                } catch (Exception e) {
                    logger.warn("广播消息失败: {}", channel, e);
                }
            }
        }
    }

    /**
     * 广播消息给本Worker的所有客户端
     * @param message 要广播的消息
     */
    public void broadcast(BroadcastMessage message) {
        messageQueue.offer(message);
        if (selector != null) {
            selector.wakeup();
        }
    }

    /**
     * 关闭客户端连接
     */
    private void closeClient(SocketChannel channel, SelectionKey key) {
        logger.debug("关闭客户端连接: {}", channel);

        key.cancel();
        boolean removed = channels.remove(channel);
        clientContextMap.remove(channel);
        IoUtils.closeQuietly(channel);
        if (removed) {
            logger.info("客户端已关闭并从channels移除: {}，剩余客户端数: {}", channel, channels.size());
        } else {
            logger.debug("客户端已关闭: {}", channel);
        }
    }

    /**
     * 获取注册统计信息
     */
    public String getRegisterStats() {
        int count = registerCount.get();
        long totalTime = totalRegisterTime.sum();
        long avgTime = count > 0 ? totalTime / count : 0;
        return String.format("注册数=%d, 平均耗时=%.2fms", count, avgTime / 1_000_000.0);
    }

    /**
     * 获取当前状态
     */
    public State getState() {
        return state;
    }

    /**
     * 获取当前连接的客户端数量
     */
    public int getClientCount() {
        return channels.size();
    }
}
