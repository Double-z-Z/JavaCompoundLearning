---
created: 2026-04-15
tags: [drill, nio, bio, network]
difficulty: 🌿
related_concepts:
  - [[BIO模型]]
  - [[Socket编程]]
  - [[线程池]]
  - [[并发集合]]
---

# BIO聊天室实现练习

> 🎯 目标：通过实现基于BIO的多人聊天室，理解阻塞IO模型的工作原理及其局限性


## 练习内容

### 题目/需求
实现一个基于BIO（阻塞IO）的多人聊天室：
1. 服务端能够监听端口，接受多个客户端连接
2. 每个客户端使用独立线程处理
3. 实现消息广播功能（一个客户端发送的消息，其他客户端都能收到）
4. 支持客户端优雅断开


### 我的实现

#### 服务端核心代码
```java
public class ChatServer {
    private int port;
    private ThreadPoolExecutor socketExecutor;
    private Map<Socket, BufferedWriter> clientWriters = new ConcurrentHashMap<>();
    private CopyOnWriteArrayList<Socket> sockets = new CopyOnWriteArrayList<>();
    private ServerSocket serverSocket;
    private volatile boolean isRunning = false;

    public void start() throws IOException {
        if (isRunning) return;
        
        serverSocket = new ServerSocket(port);
        isRunning = true;
        
        new Thread(() -> {
            while (isRunning) {
                try {
                    Socket socket = serverSocket.accept(); // 阻塞等待连接
                    sockets.add(socket);
                    
                    socketExecutor.execute(() -> {
                        try {
                            handle(socket);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            cleanup(socket);
                        }
                    });
                } catch (IOException e) {
                    if (isRunning) e.printStackTrace();
                }
            }
        }).start();
    }
    
    private void handle(Socket socket) throws IOException {
        BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(socket.getOutputStream())
        );
        clientWriters.put(socket, writer);
        
        // 发送欢迎消息
        writer.write("hello client!\n");
        writer.flush();
        
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(socket.getInputStream())
        );
        
        String address = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        String line;
        while ((line = reader.readLine()) != null) { // 阻塞读取
            System.out.println(address + " say: " + line);
            broadcast(line, writer);
        }
    }
    
    private void broadcast(String message, BufferedWriter sender) {
        for (BufferedWriter writer : clientWriters.values()) {
            if (writer != sender) {
                try {
                    writer.write(message + "\n");
                    writer.flush();
                } catch (IOException e) {
                    System.out.println("广播失败，客户端可能已断开");
                }
            }
        }
    }
}
```

#### 客户端核心代码
```java
public class ChatClient {
    private String name;
    private int port;
    private Socket socket;
    private volatile boolean running = false;
    
    public void start() throws IOException {
        socket = new Socket("localhost", port);
        running = true;
        
        // 启动接收线程
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()))) {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    System.out.println("[" + name + "] 收到: " + line);
                }
            } catch (SocketException e) {
                // 正常关闭，忽略
                System.out.println("[" + name + "] 连接已关闭");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    public void send(String message) throws IOException {
        BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(socket.getOutputStream())
        );
        writer.write(message + "\n");
        writer.flush();
    }
}
```


### 遇到的困难

#### 困难1：try-with-resources导致Socket提前关闭
**现象**：客户端连接后立即断开
**原因**：使用了`try (Socket socket = serverSocket.accept())`，导致socket在try块结束时被关闭
**解决**：改为手动管理socket生命周期，在finally块中关闭
**教训**：这让我想起之前学的[[线程池]]——异步执行时要注意资源的生命周期

#### 困难2：isRunning初始值错误导致服务端无法启动
**现象**：Connection refused，服务端没有监听端口
**原因**：`isRunning`初始值为`true`，`start()`方法直接return
**解决**：将初始值改为`false`
**教训**：标志位的初始状态要仔细考虑

#### 困难3：客户端关闭时出现SocketException
**现象**：调用shutdown()时出现`SocketException: Socket closed`
**原因**：BIO的`readLine()`是阻塞的，关闭socket会强制中断它
**解决**：捕获SocketException并忽略，这是预期的正常行为
**教训**：这让我想起之前学的[[线程阻塞]]——BIO没有优雅中断阻塞IO的方式

#### 困难4：消息格式问题
**现象**：`\n`没有换行，`/127.0.0.1`前面有斜杠
**原因**：使用了`/n`而不是`\n`；`getInetAddress().toString()`自带斜杠
**解决**：使用`\n`和`getHostAddress()`

#### 困难5：双重循环导致服务端线程死循环（后续发现）
**现象**：客户端正常关闭后，服务端处理线程不退出
**原因**：在 `handle` 方法中使用了外层 `while (isRunning)` 包裹内层 `while (read() != -1)`，内层循环因 `read() = -1` 退出后，外层循环又将其重新推入阻塞
**解决**：去掉外层 `while (isRunning)`，只保留内层循环。`read() = -1` 即表示客户端已关闭，直接退出 `handle` 方法
**教训**：这让我想起之前学的[[Socket-EOF-Semantics]]——`read() = -1` 不是消息结束，而是整个流永久结束
- 详见错误档案：[[MISTAKE-001-SocketEOFDeadloop]]


## 验证与测试

### 测试方案
使用JUnit测试多客户端通信：
1. 启动服务端
2. 连接2个客户端
3. 客户端A发送消息，验证客户端B能收到
4. 验证广播功能

### 测试结果
```
Client(A) connected to server, local port(56266)
Client(B) connected to server, local port(56267)
Thread(B): hello client!
127.0.0.1:56266 say: Hello, i'm client A
Thread(A): hello client!
127.0.0.1:56267 say: Hello, i'm client B
```
✅ 测试通过，消息广播正常


## 复盘总结

### 学到的
1. **BIO的核心特性**：accept()和read()都是阻塞的，必须配合多线程处理并发
2. **这让我想起之前学的[[线程池]]**——BIO的痛点正是每个连接需要一个线程，高并发时线程资源耗尽
3. **资源管理**：异步场景下try-with-resources要谨慎使用
4. **异常处理**：BIO关闭连接时的SocketException是正常的，需要优雅处理
5. **流关闭语义**：`read() = -1` 表示连接已关闭，不是消息边界

### 关联知识
- [[BIO-BlockingIO]] - 应用场景：理解阻塞IO的工作原理
- [[线程池]] - 应用场景：管理客户端处理线程
- [[并发集合]] - 应用场景：CopyOnWriteArrayList管理在线客户端列表
- [[线程阻塞]] - 应用场景：理解BIO阻塞的本质
- [[Socket-EOF-Semantics]] - 应用场景：正确理解 `read() = -1` 的含义，避免死循环
- [[MISTAKE-001-SocketEOFDeadloop]] - 错误复盘：双重循环陷阱

### 待深化
- BIO的C10K问题（后续通过性能测试验证）
- NIO如何解决BIO的局限性
- Netty的线程模型


---

## 🤖 AI评价

### 完成质量
- 功能实现：✅ 完整
- 代码质量：✅ 良好（正确处理了资源管理和并发安全）
- 概念应用：✅ 正确（正确应用了线程池、并发集合等已掌握知识）

### 对掌握度的影响
- [[BIO模型]]: +40分 (从初识到理解，完成完整实现)
- [[Socket编程]]: +35分 (掌握ServerSocket和Socket的使用)
- [[线程池]]: +10分 (巩固，正确应用于新场景)
- [[并发集合]]: +10分 (巩固，正确选择CopyOnWriteArrayList)
- [[Socket-EOF-Semantics]]: +20分 (通过错误纠正，深刻理解流结束语义)

### 建议
1. 进行性能测试，验证BIO的C10K问题
2. 对比NIO实现，理解非阻塞IO的优势
3. 学习Selector多路复用机制


---

## 📊 相关练习统计

```dataview
TABLE difficulty, created, related_concepts
FROM #drill AND #nio
SORT created DESC
```
