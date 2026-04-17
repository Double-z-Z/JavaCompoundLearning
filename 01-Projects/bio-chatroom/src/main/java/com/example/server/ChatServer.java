package com.example.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ChatServer {

    private int port;

    /**
     * 客户端线程池 ，用于处理客户端请求
     */
    private ThreadPoolExecutor socketExecutor;

    /**
     * 客户端写入器列表 ，用于广播详细到所有客户端
     */
    private Map<Socket, BufferedWriter> clientWriters = new ConcurrentHashMap<>();

    private ServerSocket serverSocket;

    private boolean isRunning;

    public ChatServer(int port) {
        this.port = port;
        socketExecutor = new ThreadPoolExecutor(50, 100, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10));
    }

    public void start() throws IOException {
        if (isRunning) {
            return;
        }
        serverSocket = new ServerSocket(port);
        new Thread(() -> {
            isRunning = true;
            while (isRunning) {
                try  {
                    Socket socket = serverSocket.accept();
                    socketExecutor.execute(() -> {
                        try {
                            handle(socket);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        finally {
                            try {
                                socket.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            clientWriters.remove(socket);
                        }
                    });
                } catch (SocketException e) {
                    if (!isRunning) {
                        break;
                    }
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void shutdown() {
        if (!isRunning) {
            return;
        }
        isRunning = false;
        socketExecutor.shutdown();
        try {
            socketExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            serverSocket.close();
            serverSocket = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 处理客户端请求 ，用于广播详细到所有客户端
     * @param socket 客户端套接字
     */
    private void handle(Socket socket) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        clientWriters.put(socket, writer);

        writer.write("System say: Hello client!\n");
        writer.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String address = socket.getLocalAddress().toString() + ":" + socket.getPort() + " say: ";
        char[] message = new char[1024];
        int readCount;
        while ((readCount = reader.read(message)) != -1) {
            for (BufferedWriter other : clientWriters.values()) {
                String msg = new String(message, 0, readCount);
                if (other != writer) {
                    try {
                        other.write(address + msg);
                        other.flush();
                        System.out.println(address + msg);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
