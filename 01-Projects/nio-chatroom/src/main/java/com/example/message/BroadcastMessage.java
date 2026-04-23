package com.example.message;

import java.nio.channels.SocketChannel;

public class BroadcastMessage {
    public final SocketChannel source;
    public final ChatMessage chatMessage;

    public BroadcastMessage(ChatMessage message, SocketChannel source) {
        this.chatMessage = message;
        this.source = source;
    }
}
