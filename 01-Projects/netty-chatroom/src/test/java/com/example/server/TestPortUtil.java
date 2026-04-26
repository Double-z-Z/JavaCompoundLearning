package com.example.server;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * 测试端口工具类
 * 提供随机可用端口，避免测试间端口冲突
 */
public class TestPortUtil {
    
    /**
     * 获取一个随机可用端口
     */
    public static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Failed to find available port", e);
        }
    }
    
    /**
     * 检查端口是否可用
     */
    public static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * 等待端口释放
     */
    public static void waitForPortRelease(int port, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (isPortAvailable(port)) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new RuntimeException("Port " + port + " not released within " + timeoutMs + "ms");
    }
}
