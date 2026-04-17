package com.example;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.platform.commons.annotation.Testable;

import com.example.client.ChatClient;
import com.example.server.ChatServer;

/**
 * Unit test for simple App.
 */
@Testable
public class AppTest 
{
    private int port = 8089;


     @Test
    public void testBrocast() throws IOException, InterruptedException {
        ChatServer server = startServer(port);

        Thread.sleep(1000);

        ChatClient clientA = startClient("A", port);
        ChatClient clientB = startClient("B", port);
        ChatClient clientC = startClient("C", port);
        
        Thread.sleep(1000);

        clientA.send("Hello, i'm client A");
        clientB.send("Hello, i'm client B");
        clientC.send("Hello, i'm client C");

        Thread.sleep(1000);

        clientA.shutdown();
        clientB.shutdown();
        clientC.shutdown();
        server.shutdown();
    }

    public static void main(String[] args) throws IOException {
        ChatServer server = new ChatServer(8089);
        server.start();
    }

    public ChatClient startClient(String name, int port) throws IOException {
        ChatClient client = new ChatClient(name, port);
        client.start();
        return client;
    }

    public ChatServer startServer(int port) throws IOException {
        ChatServer server = new ChatServer(port);
        server.start();
        return server;
    }
}
