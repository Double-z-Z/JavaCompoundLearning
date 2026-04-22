package com.example.Server;

import com.example.message.BroadcastMessage;
import com.example.ioutils.IoUtils;
import com.example.message.ChatDataEncoder;
import com.example.message.ChatMessage;
import com.example.message.ChatMessageDecoder;
import com.example.message.ChatMessageEncoder;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 客户业务处理类，分担boss分配的客户端，负责这些客户的消息读写服务
 */
public class ServerHandler {

    private static final Logger logger = LoggerFactory.getLogger(ServerHandler.class);
    private static final Logger perfLogger = LoggerFactory.getLogger("PERFORMANCE");

    private Selector selector;

    private volatile boolean isRunning;

    private ClientManager boss;

    private List<SocketChannel> channels = new CopyOnWriteArrayList<>();

    /**
     * 消息队列 - 存储待广播的消息
     */
    private ConcurrentLinkedQueue<BroadcastMessage> messageQueue = new ConcurrentLinkedQueue<>();

    /**
     * 客户端上下文映射表，用于存储每个客户端的待发送消息队列
     */
    private Map<SocketChannel, ClientContext> clientContextMap = new ConcurrentHashMap<>();

    /**
     * 注册统计 - 使用LongAdder减少并发竞争
     */
    private final LongAdder totalRegisterTime = new LongAdder();
    private final AtomicInteger registerCount = new AtomicInteger(0);

    public ServerHandler(ClientManager boss) {
        this.boss = boss;
    }

    public ServerHandler openSelector() throws IOException {
        this.selector = Selector.open();
        return this;
    }

    /**
     * 运行服务器处理线程，监听客户端的读写服务请求, 直到服务关闭。
     *
     * @throws IOException
     */
    public void run() throws IOException {
        this.isRunning = true;
        while (isRunning) {
            int readyChannels = selector.select(100);
            if (readyChannels <= 0) {
                // 没有就绪的channel，处理消息队列中的广播消息
                processBroadcastMessages();
                continue;
            }

            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                if (!key.isValid()) {
                    iterator.remove();
                    continue;
                }
                if (key.isReadable()) {
                    handleRead(key);
                }
                if (key.isValid() && key.isWritable()) {
                    handleWrite(key);
                }
                iterator.remove();
            }

            // 处理广播消息
            processBroadcastMessages();
        }
    }

    /**
     * 处理广播消息队列
     * @throws IOException 
     */
    private void processBroadcastMessages() throws IOException {
        BroadcastMessage message;
        while ((message = messageQueue.poll()) != null) {
            broadcastToAll(message);
        }
    }

    /**
     * 广播消息到所有客户端
     * 
     * 编码规则：
     * 1. ChatMessage 是应用层对象，只包含内容、时间戳、发送者
     * 2. 发送前调用 chatMessage.encode() 进行网络编码（添加长度前缀）
     * 3. 编码只发生一次，结果被缓存
     */
    private void broadcastToAll(BroadcastMessage message) throws IOException {
        // 应用层消息对象编码为网络传输格式（带长度前缀）
        byte[] messageBytes = ChatMessageEncoder.encode(message.chatMessage);

        for (SocketChannel channel : channels) {
            // 不向消息来源客户端回传
            if (channel.equals(message.source)) {
                continue;
            }

            ClientContext context = clientContextMap.get(channel);
            if (context == null) {
                continue;
            }

            // 尝试直接发送
            if (context.pendingMessages.isEmpty()) {
                ByteBuffer writeBuffer = ByteBuffer.wrap(messageBytes);
                // 注意：wrap() 创建的 Buffer 已经是读模式，不需要 flip()
                try {
                    int written = channel.write(writeBuffer);
                    if (writeBuffer.hasRemaining()) {
                        // 部分发送，剩余部分加入待发送队列
                        byte[] remaining = new byte[writeBuffer.remaining()];
                        writeBuffer.get(remaining);
                        context.pendingMessages.offer(remaining);
                        // 注册写事件
                        channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    }
                } catch (IOException e) {
                    // 发送失败，加入待发送队列稍后重试
                    context.pendingMessages.offer(messageBytes);
                }
            } else {
                // 有待发送消息，加入队列
                context.pendingMessages.offer(messageBytes);
            }
        }
    }

    /**
     * 关闭服务器，关闭所有注册的客户端的连接，并释放所有资源。
     */
    public void shutdown() {
        System.out.println("[ServerHandler] shutdown() 被调用");
        isRunning = false;

        // 关闭所有客户端连接
        for (SocketChannel channel : channels) {
            IoUtils.closeQuietly(channel);
        }
        channels.clear();

        // 唤醒线程，退出阻塞
        if (selector != null) {
            selector.wakeup();
        }
        
        System.out.println("[ServerHandler] shutdown() 完成");
    }

    /**
     * 关闭所有注册的客户端连接，即强制中断所有读写操作。
     */
    public void stop() {
        for (SocketChannel channel : channels) {
            IoUtils.closeQuietly(channel);
        }
        channels.clear();

        IoUtils.closeQuietly(selector);
        selector = null;
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
        
        // 输出每次注册的详细信息（用于诊断）
        System.out.println("[ServerHandler] 注册完成 #" + count + 
            ", channel=" + channel + 
            ", 耗时=" + (registerTime / 1000) + "μs");
        
        // 记录性能日志（每10个客户端输出一次统计）
        if (count % 10 == 0) {
            long avgTime = totalRegisterTime.sum() / count;
            perfLogger.debug("注册统计: 总数={}, 平均耗时={}μs", count, avgTime / 1000);
        }
        
        // DEBUG级别记录每次注册
        if (logger.isDebugEnabled()) {
            logger.debug("客户端注册完成: channel={}, 耗时={}μs", 
                channel, registerTime / 1000);
        }
    }
    
    /**
     * 获取注册统计信息
     */
    public String getRegisterStats() {
        int count = registerCount.get();
        long avgTime = count > 0 ? totalRegisterTime.sum() / count : 0;
        return String.format("注册统计: 总数=%d, 平均耗时=%.3fμs", 
            count, avgTime / 1000.0);
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
                    closeClient(channel, key);
                    return;
                }

                // 若消息不完整，等待更多数据
                if (messageLength > buffer.remaining()) {
                    buffer.reset();
                    break;
                }

                byte[] messageContent = new byte[messageLength];
                buffer.get(messageContent);

                String message = new String(messageContent);
                System.out.println("收到消息 [" + channel.getRemoteAddress() + "]: " + message.trim());

                // 解码为应用层消息对象（会自动添加服务器时间戳）
                ChatMessage chatMessage = ChatMessageDecoder.decode(messageContent);

                // 区分系统消息和聊天消息
                System.out.println("[Server] 消息类型: " + chatMessage.getType());
                if (chatMessage.getType() == com.example.message.MessageType.SYSTEM) {
                    // 系统消息：只回复确认，不广播
                    System.out.println("[Server] 收到系统消息，准备发送确认");
                    sendAckResponse(channel, chatMessage);
                } else {
                    // 聊天消息：广播给所有客户端
                    BroadcastMessage broadMessage = new BroadcastMessage(chatMessage, channel);
                    // 本地广播（同一Worker的其他客户端）
                    broadcastToAll(broadMessage);
                    // 跨Worker广播
                    if (boss != null) {
                        boss.broadcast(this, broadMessage);
                    }
                }
            }

            // 保留未处理的数据到缓冲区头部，供下次读取继续处理
            buffer.compact();
        }
    }

    /**
     * 处理写事件
     *
     * 消费待发送队列中的消息，使用 writeBuffer.hasRemaining() 判断部分发送
     */
    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientContext context = clientContextMap.get(channel);

        // 发送待发送队列中的消息
        byte[] message;
        while ((message = context.pendingMessages.peek()) != null) {
            ByteBuffer writeBuffer = ByteBuffer.wrap(message);
            // 注意：wrap() 创建的 Buffer 已经是读模式，不需要 flip()
            int written = channel.write(writeBuffer);

            if (written == 0) {
                // 发送缓冲区满，下次再试
                break;
            }

            if (writeBuffer.hasRemaining()) {
                // 部分发送，剩余部分放回队首
                context.pendingMessages.poll();
                byte[] remaining = new byte[writeBuffer.remaining()];
                writeBuffer.get(remaining);
                context.pendingMessages.offerFirst(remaining);
                break;
            } else {
                // 发送完成，移除队列
                context.pendingMessages.poll();
            }
        }

        // 如果没有待发送消息，取消写事件
        if (context.pendingMessages.isEmpty()) {
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    /**
     * 发送确认响应给客户端
     */
    private void sendAckResponse(SocketChannel channel, ChatMessage receivedMessage) {
        try {
            // 创建确认消息
            ChatMessage ackMessage = new ChatMessage(
                com.example.message.MessageType.SYSTEM,
                "ACK: Message received from " + receivedMessage.getFrom(),
                new Date(),
                "Server"
            );
            
            // 编码消息
            byte[] encoded = ChatMessageEncoder.encode(ackMessage);
            
            // 将消息加入客户端的发送队列
            ClientContext context = clientContextMap.get(channel);
            if (context != null) {
                context.pendingMessages.offer(encoded);
                
                // 注册写事件，让 selector 触发写操作
                SelectionKey key = channel.keyFor(selector);
                if (key != null && key.isValid()) {
                    key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                    selector.wakeup();
                }
                
                System.out.println("[Server] 确认响应已加入队列: " + channel.getRemoteAddress());
            }
            
            if (logger.isDebugEnabled()) {
                logger.debug("发送确认响应给: {}", channel.getRemoteAddress());
            }
        } catch (IOException e) {
            logger.error("发送确认响应失败", e);
        }
    }

    /**
     * 关闭客户端连接
     */
    private void closeClient(SocketChannel channel, SelectionKey key) {
        System.out.println("客户端断开: " + channel);
        IoUtils.closeQuietly(channel);
        key.cancel();
        channels.remove(channel);
        clientContextMap.remove(channel);
    }

    /**
     * 广播消息（供ClientManager调用，跨Worker广播）
     *
     * @param message 消息内容
     */
    public void broadcast(BroadcastMessage message) {
        messageQueue.offer(message);
        // 唤醒selector立即处理
        if (selector != null) {
            selector.wakeup();
        }
    }
}
