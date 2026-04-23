package com.example.Client;

import com.example.message.ChatDataEncoder;
import com.example.message.ChatMessage;
import com.example.message.ChatMessageDecoder;
import com.example.message.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * NIO 聊天客户端
 *
 * 使用非阻塞 I/O 和 Selector 实现，支持：
 * - 异步连接服务端
 * - 发送消息（带长度前缀协议）
 * - 接收消息（解析长度前缀协议）
 * - 回调通知（连接成功、收到消息、断开连接、错误）
 */
public class ChatClient {

    private static final Logger logger = LoggerFactory.getLogger(ChatClient.class);

    /** 最大允许的消息长度（与服务端保持一致） */
    public static final int MAX_BUFFER_SIZE = 1024 * 4;

    /**
     * ChatClient 状态枚举
     * 参考服务端状态机设计
     */
    public enum State {
        NEW,           // 新建状态
        CONNECTING,    // 连接中
        RUNNING,       // 运行中（已连接）
        SHUTTING_DOWN, // 正在关闭
        STOPPED        // 已停止
    }

    private final String host;
    private final int port;
    private Selector selector;
    private SocketChannel channel;
    private volatile State state = State.NEW;
    private Thread ioThread;
    private MessageListener messageListener;

    /** 待发送消息队列（每个元素是带长度前缀的 ByteBuffer） */
    private final ConcurrentLinkedQueue<ByteBuffer> sendQueue = new ConcurrentLinkedQueue<>();

    /** 读取缓冲区 */
    private ByteBuffer readBuffer = ByteBuffer.allocate(4096);

    public interface MessageListener {
        void onMessageReceived(ChatMessage message);
        void onConnected();
        void onDisconnected();
        void onError(Exception e);
    }

    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    /**
     * 连接到服务端，启动 I/O 线程
     */
    public void connect() throws IOException {
        if (state != State.NEW && state != State.STOPPED) {
            logger.warn("ChatClient 不在可连接状态，当前 state={}", state);
            return;
        }

        // 重置状态（支持重连）
        if (state == State.STOPPED) {
            resetState();
        }

        state = State.CONNECTING;
        selector = Selector.open();
        channel = SocketChannel.open();
        channel.configureBlocking(false);

        // 关键：先注册 OP_CONNECT，再发起连接，避免事件丢失
        channel.register(selector, SelectionKey.OP_CONNECT);
        channel.connect(new InetSocketAddress(host, port));

        ioThread = new Thread(this::runLoop, "ChatClient-IO");
        ioThread.start();
    }

    /**
     * 重置状态，支持重连
     */
    private void resetState() {
        logger.debug("重置 ChatClient 状态");
        state = State.NEW;
        sendQueue.clear();
        readBuffer.clear();
    }

    private void runLoop() {
        try {
            // 循环条件：运行中或正在关闭（让 run() 自己退出）
            while (state == State.RUNNING || state == State.CONNECTING) {
                int readyChannels = selector.select(100);
                if (readyChannels <= 0) {
                    continue;
                }

                for (SelectionKey key : selector.selectedKeys()) {
                    if (key.isValid() && key.isConnectable()) {
                        handleConnect(key);
                    }
                    if (key.isValid() && key.isReadable()) {
                        handleRead(key);
                    }
                    if (key.isValid() && key.isWritable()) {
                        handleWrite(key);
                    }
                }
                selector.selectedKeys().clear();
            }
        } catch (IOException e) {
            if ((state == State.RUNNING || state == State.CONNECTING) && messageListener != null) {
                messageListener.onError(e);
            }
        } finally {
            // 正常退出，清理资源
            state = State.STOPPED;
            closeConnection();
        }
    }

    private void handleConnect(SelectionKey key) throws IOException {
        SocketChannel ch = (SocketChannel) key.channel();
        if (ch.finishConnect()) {
            key.interestOps(SelectionKey.OP_READ);
            state = State.RUNNING;
            if (messageListener != null) {
                messageListener.onConnected();
            }
        }
    }

    /**
     * 处理读事件：解析长度前缀协议
     *
     * 协议格式：[4字节消息长度(大端)][消息内容]
     */
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel ch = (SocketChannel) key.channel();
        int bytesRead = ch.read(readBuffer);

        if (bytesRead == -1) {
            closeConnection();
            return;
        }

        if (bytesRead > 0) {
            readBuffer.flip();
            // 循环处理缓冲区中所有完整的消息
            while (readBuffer.remaining() >= 4) {
                readBuffer.mark();
                int messageLength = readBuffer.getInt();

                // 防御非法长度
                if (messageLength < 0 || messageLength > MAX_BUFFER_SIZE) {
                    closeConnection();
                    return;
                }

                if (readBuffer.remaining() < messageLength) {
                    readBuffer.reset();
                    break; // 消息不完整，等待更多数据
                }

                byte[] messageContent = new byte[messageLength];
                readBuffer.get(messageContent);

                ChatMessage message = ChatMessageDecoder.decode(messageContent);
                
                // 先调用消息监听器（让测试能够捕获消息）
                if (messageListener != null) {
                    messageListener.onMessageReceived(message);
                }
                
                // 处理服务器关闭通知
                if (message != null && message.getType() == MessageType.SHUTDOWN_NOTICE) {
                    logger.info("[系统通知] {}" , message.getContent());
                    logger.info("[系统通知] 服务器即将关闭，客户端主动断开连接");
                    shutdown();
                    return;
                }
            }

            // 保留未处理的数据到缓冲区头部
            readBuffer.compact();
        }
    }

    /**
     * 处理写事件：发送队列中的消息
     */
    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel ch = (SocketChannel) key.channel();

        ByteBuffer buffer;
        while ((buffer = sendQueue.peek()) != null) {
            int written = ch.write(buffer);

            if (written == 0) {
                // 发送缓冲区满，下次再试
                break;
            }

            if (!buffer.hasRemaining()) {
                // 当前消息发送完成
                sendQueue.poll();
            }
            // 如果 buffer 还有剩余，position 已更新，下次继续写入同一个 buffer
        }

        // 队列为空，取消写事件，避免空轮询
        if (sendQueue.isEmpty()) {
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    /**
     * 发送消息到服务端
     *
     * 自动添加长度前缀，异步发送
     */
    public void sendMessage(String content) {
        if (state != State.RUNNING || channel == null || !channel.isConnected()) {
            return;
        }

        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] framedMessage = ChatDataEncoder.encode(contentBytes);
        sendQueue.offer(ByteBuffer.wrap(framedMessage));

        // 注册写事件并唤醒 selector
        selector.wakeup();
        try {
            SelectionKey key = channel.keyFor(selector);
            if (key != null && key.isValid()
                    && (key.interestOps() & SelectionKey.OP_WRITE) == 0) {
                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            }
        } catch (Exception e) {
            // 连接可能已关闭，忽略
        }
    }

    private void closeConnection() {
        try {
            if (channel != null) {
                channel.close();
            }
            if (selector != null) {
                selector.close();
            }
        } catch (IOException e) {
            // 忽略关闭异常
        }
        if (messageListener != null) {
            messageListener.onDisconnected();
        }
    }

    /**
     * 触发关闭流程（非阻塞）
     * 与 ExecutorService.shutdown() 语义一致
     * 让 run() 自己退出并清理资源
     */
    public void shutdown() {
        if (state != State.RUNNING && state != State.CONNECTING) {
            logger.warn("ChatClient 不在可关闭状态，当前 state={}", state);
            return;
        }

        logger.info("触发 ChatClient 关闭流程");
        state = State.SHUTTING_DOWN;

        // 唤醒 selector，让 runLoop() 检查状态并退出
        if (selector != null) {
            selector.wakeup();
        }
    }

    /**
     * 等待客户端完全停止（阻塞方法）
     * 与 ExecutorService.awaitTermination() 语义一致
     */
    public boolean awaitTermination(long timeoutMs) {
        if (ioThread == null) {
            return true;
        }

        try {
            ioThread.join(timeoutMs);
            boolean terminated = !ioThread.isAlive();
            if (terminated) {
                logger.info("ChatClient 已停止");
            } else {
                logger.warn("ChatClient 停止超时");
            }
            return terminated;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 立即关闭（强制中断）
     * 与 ExecutorService.shutdownNow() 语义一致
     */
    public void shutdownNow() {
        logger.info("shutdownNow() 被调用");
        state = State.STOPPED;

        // 关闭 channel 和 selector，强制 runLoop() 退出
        try {
            if (channel != null) {
                channel.close();
            }
            if (selector != null) {
                selector.close();
            }
        } catch (IOException e) {
            // 忽略
        }

        // 等待线程结束
        if (ioThread != null) {
            try {
                ioThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public State getState() {
        return state;
    }

    public boolean isConnected() {
        return channel != null && channel.isConnected() && state == State.RUNNING;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
