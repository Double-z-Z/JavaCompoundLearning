package com.example.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

public class ChatClient {

    /**
     * 客户端属性
     */
    private String name;
    private int port;
    
    /**
     * 客户端状态
     */
    private volatile boolean isRunning = false;
    
    /**
     * 客户端使用的资源对象
     */
    private Socket socket;
    private BufferedWriter writer;

    
    
    public ChatClient(String name, int port) {
        this.name = name;
        this.port = port;
    }

    public void start() throws UnknownHostException, IOException {
        socket = new Socket((String)null, port);

        System.out.println("Client(" + name + ") Connected to server, local port: (" + socket.getLocalPort() + ")");

        isRunning = true;

        new Thread(() -> {
            try {
                handle(socket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void shutdown() {
        if (!isRunning) {
            return;
        }
        isRunning = false;
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        socket = null;
    }

    private void handle(Socket socket) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        while (isRunning) {
            String line = null;
            try {
                line = reader.readLine();
            } catch (SocketException e) {
                if (!isRunning) {
                    break;
                }
                e.printStackTrace();
            }
            if (line == null) {
                break;
            }
            System.out.println("Client(" + name + ") received: " + line);
        }
    }

    public void send(String string) {
        if (!isRunning) {
            return;
        }
        try {
            System.out.println("Client(" + name + ") sent: " + string);
            writer.write(string);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
