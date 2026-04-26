package com.example.Client;

public interface ClientListener {
    void onConnect();
    void onDisconnect();
    void onMessage(String message);
}
