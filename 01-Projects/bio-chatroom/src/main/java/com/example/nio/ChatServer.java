package com.example.nio;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;

public class ChatServer {

    private int port;
    private ExecutorService clientExecutor;

    /**
     * 服务器端口
     */
    public ChatServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        Selector selector = Selector.open();

        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        while (true) {
            int readCount = selector.select();
            if (readCount <= 0) {
                continue;
            }
            for (SelectionKey key : selector.selectedKeys()) {
                if (key.isAcceptable()) {
                    SocketChannel channel = serverChannel.accept();
                    channel.configureBlocking(false);
                    channel.register(selector, SelectionKey.OP_READ);
                }

                if (key.isReadable()) {
                    SocketChannel channel = (SocketChannel) key.channel();
                    clientExecutor.execute(() -> {
                        handle(channel);
                    });
                }
                key.cancel();
            }
        }
    }

    private void handle(SocketChannel channel) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        try {
            channel.read(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }

        buffer.flip();
        while (buffer.hasRemaining()) {
            System.out.print((char) buffer.get());
        }
        buffer.clear();


    }
}