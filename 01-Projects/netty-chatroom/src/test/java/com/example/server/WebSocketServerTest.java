package com.example.server;

import com.example.server.message.request.ChatMessage;
import com.example.server.message.request.IdentifyMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSocket 服务端测试
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class WebSocketServerTest {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketServerTest.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private ChatServer server;
    private EventLoopGroup group;
    private int tcpPort = 9998;
    private int wsPort = 9999;

    @BeforeAll
    void setUp() throws Exception {
        server = new ChatServer(tcpPort, wsPort);
        server.startAsync().awaitRunning(5, TimeUnit.SECONDS);
        logger.info("Test server started on TCP port {}, WebSocket port {}", tcpPort, wsPort);

        group = new NioEventLoopGroup();
    }

    @AfterAll
    void tearDown() throws Exception {
        if (server != null && server.isRunning()) {
            server.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);
            logger.info("Test server stopped");
        }
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    @Test
    @DisplayName("测试 WebSocket 连接和认证")
    void testWebSocketConnection() throws Exception {
        URI uri = new URI("ws://localhost:" + wsPort + "/ws");

        CountDownLatch connectLatch = new CountDownLatch(1);
        CountDownLatch messageLatch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
            .channel(NioSocketChannel.class)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(new HttpClientCodec());
                    p.addLast(new HttpObjectAggregator(8192));
                    p.addLast(new WebSocketClientProtocolHandler(
                        WebSocketClientHandshakerFactory.newHandshaker(
                            uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders()
                        )
                    ));
                    p.addLast(new SimpleChannelInboundHandler<TextWebSocketFrame>() {
                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                            if (evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                                logger.info("WebSocket handshake complete");
                                connectLatch.countDown();

                                // 发送认证消息
                                IdentifyMessage identify = new IdentifyMessage("wsTestUser");
                                ctx.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(identify)));
                            }
                            super.userEventTriggered(ctx, evt);
                        }

                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
                            String text = frame.text();
                            logger.info("Received: {}", text);
                            receivedMessage.set(text);
                            messageLatch.countDown();
                        }
                    });
                }
            });

        Channel channel = bootstrap.connect(uri.getHost(), uri.getPort()).sync().channel();

        // 等待握手完成
        assertTrue(connectLatch.await(5, TimeUnit.SECONDS), "WebSocket handshake timeout");

        // 等待收到系统消息
        assertTrue(messageLatch.await(5, TimeUnit.SECONDS), "Message receive timeout");

        String msg = receivedMessage.get();
        assertNotNull(msg);
        assertTrue(msg.contains("wsTestUser") || msg.contains("joined"), "Should receive join notification");

        channel.close();
        logger.info("WebSocket connection test passed");
    }

    @Test
    @DisplayName("测试 WebSocket 聊天消息")
    void testWebSocketChat() throws Exception {
        URI uri = new URI("ws://localhost:" + wsPort + "/ws");

        CountDownLatch handshakeLatch1 = new CountDownLatch(1);
        CountDownLatch handshakeLatch2 = new CountDownLatch(1);
        CountDownLatch chatLatch = new CountDownLatch(1);
        AtomicReference<String> chatMessage = new AtomicReference<>();

        // 客户端1：发送消息
        Bootstrap bootstrap1 = new Bootstrap();
        bootstrap1.group(group)
            .channel(NioSocketChannel.class)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(new HttpClientCodec());
                    p.addLast(new HttpObjectAggregator(8192));
                    p.addLast(new WebSocketClientProtocolHandler(
                        WebSocketClientHandshakerFactory.newHandshaker(
                            uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders()
                        )
                    ));
                    p.addLast(new SimpleChannelInboundHandler<TextWebSocketFrame>() {
                        private boolean identified = false;

                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                            if (evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                                logger.info("Client1: WebSocket handshake complete");
                                handshakeLatch1.countDown();

                                // 发送认证
                                IdentifyMessage identify = new IdentifyMessage("senderUser");
                                ctx.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(identify)));
                            }
                            super.userEventTriggered(ctx, evt);
                        }

                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
                            String text = frame.text();
                            logger.info("Client1 received: {}", text);

                            // 收到自己加入的通知后发送聊天消息
                            if (!identified && text.contains("USER_JOINED") && text.contains("senderUser")) {
                                identified = true;
                                // 立即发送聊天消息
                                ChatMessage chat = new ChatMessage("senderUser", "Hello WebSocket!");
                                ctx.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(chat)));
                                logger.info("Client1 sent chat message");
                            }
                        }
                    });
                }
            });

        // 客户端2：接收消息
        Bootstrap bootstrap2 = new Bootstrap();
        bootstrap2.group(group)
            .channel(NioSocketChannel.class)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(new HttpClientCodec());
                    p.addLast(new HttpObjectAggregator(8192));
                    p.addLast(new WebSocketClientProtocolHandler(
                        WebSocketClientHandshakerFactory.newHandshaker(
                            uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders()
                        )
                    ));
                    p.addLast(new SimpleChannelInboundHandler<TextWebSocketFrame>() {
                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                            if (evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                                logger.info("Client2: WebSocket handshake complete");
                                handshakeLatch2.countDown();

                                // 发送认证
                                IdentifyMessage identify = new IdentifyMessage("receiverUser");
                                ctx.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(identify)));
                            }
                            super.userEventTriggered(ctx, evt);
                        }

                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
                            String text = frame.text();
                            logger.info("Client2 received: {}", text);

                            if (text.contains("Hello WebSocket")) {
                                chatMessage.set(text);
                                chatLatch.countDown();
                            }
                        }
                    });
                }
            });

        // 先连接客户端2（接收方）
        Channel channel2 = bootstrap2.connect(uri.getHost(), uri.getPort()).sync().channel();
        assertTrue(handshakeLatch2.await(5, TimeUnit.SECONDS), "Client2 handshake timeout");

        // 再连接客户端1（发送方）
        Channel channel1 = bootstrap1.connect(uri.getHost(), uri.getPort()).sync().channel();
        assertTrue(handshakeLatch1.await(5, TimeUnit.SECONDS), "Client1 handshake timeout");

        // 等待收到聊天消息
        assertTrue(chatLatch.await(5, TimeUnit.SECONDS), "Chat message timeout");

        String msg = chatMessage.get();
        assertNotNull(msg);
        assertTrue(msg.contains("Hello WebSocket"), "Should receive chat message");

        channel1.close();
        channel2.close();
        logger.info("WebSocket chat test passed");
    }
}
