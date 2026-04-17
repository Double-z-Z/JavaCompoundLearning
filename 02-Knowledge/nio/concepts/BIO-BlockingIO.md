---
created: 2026-04-15
tags: [nio, network, io-model]
status: 🌿
mastery: 40
---

# BIO模型（Blocking IO）

## 一句话定义
BIO（阻塞IO）是Java传统的网络编程模型，所有IO操作（accept、read、write）都会阻塞当前线程直到操作完成。


## 核心理解

### BIO的核心特性

```
┌─────────────────────────────────────────────────────────────┐
│                        BIO 阻塞模型                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│   主线程                                                      │
│     │                                                        │
│     ▼                                                        │
│   serverSocket.accept()  ◄──── 阻塞，直到有客户端连接          │
│     │                                                        │
│     ▼                                                        │
│   创建新线程处理客户端                                         │
│     │                                                        │
│     ▼                                                        │
│   回到accept()继续阻塞  ◄──── 循环等待新连接                   │
│                                                              │
│   客户端处理线程                                               │
│     │                                                        │
│     ▼                                                        │
│   socket.read()  ◄────────── 阻塞，直到有数据可读              │
│     │                                                        │
│     ▼                                                        │
│   处理数据                                                    │
│     │                                                        │
│     ▼                                                        │
│   socket.write()  ◄───────── 阻塞，直到数据发送完成            │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 关键特点

1. **阻塞性**
   - `ServerSocket.accept()`：阻塞直到有客户端连接
   - `Socket.read()`：阻塞直到有数据可读
   - `Socket.write()`：阻塞直到数据发送完成

2. **一连接一线程**
   - 每个客户端连接需要一个独立线程处理
   - 这让我想起之前学的[[线程池]]——BIO必须使用线程池管理线程资源

3. **简单易用**
   - 代码直观，符合同步编程思维
   - 适合低并发场景


### BIO的局限性

| 问题 | 原因 | 后果 |
|------|------|------|
| **C10K问题** | 每个连接需要一个线程 | 线程资源耗尽，内存溢出 |
| **上下文切换开销** | 大量线程切换 | CPU资源浪费 |
| **阻塞等待** | 线程阻塞在IO上 | 资源利用率低 |

这让我想起之前学的[[用户态与内核态切换]]——每个线程阻塞/唤醒都涉及系统调用。


## 关键关联

- [[线程池]] - 关联原因：BIO必须使用线程池管理客户端处理线程，避免线程爆炸
- [[线程阻塞]] - 关联原因：BIO的本质就是线程阻塞，理解阻塞状态对调试很重要
- [[NIO模型]] - 关联原因：NIO是为了解决BIO的局限性而设计的，后续学习对比
- [[Socket编程]] - 关联原因：BIO基于Socket API实现网络通信


## 我的误区与疑问

- ❌ 误区：以为try-with-resources在异步场景下也能正常工作
  - 纠正：异步执行时，try块结束资源就被释放了，而异步任务还在使用

- ❌ 误区：以为 `read() = -1` 表示一段消息结束，服务端应该继续等待下一条消息
  - 纠正：`read() = -1` 表示整个输入流已永久结束（对端发送了FIN），应该退出处理循环
  - 详见：[[MISTAKE-001-SocketEOFDeadloop]]

- ❓ 疑问：BIO的阻塞是操作系统层面的还是JVM层面的？
  - 答案：是操作系统层面的，JVM调用native方法进入内核态阻塞


## 代码与实践

### 服务端核心模式
```java
ServerSocket serverSocket = new ServerSocket(port);

while (true) {
    // 阻塞等待连接
    Socket socket = serverSocket.accept();
    
    // 提交线程池处理（避免一连接一线程的资源浪费）
    executor.execute(() -> {
        handleClient(socket);
    });
}
```

### 客户端处理模式
```java
private void handleClient(Socket socket) {
    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(socket.getInputStream()))) {
        
        String line;
        // 阻塞读取，直到连接关闭
        while ((line = reader.readLine()) != null) {
            process(line);
        }
    } catch (SocketException e) {
        // 正常关闭，忽略
    }
}
```


## 深入思考

💡 为什么BIO的`read()`阻塞时，线程状态是`RUNNABLE`而不是`WAITING`？
（提示：查看JVM线程状态定义和操作系统线程状态的区别）

💡 BIO的C10K问题具体是指什么？如果单机要支持10万连接，BIO需要多少线程？

💡 这让我想起之前学的[[futex]]——BIO的阻塞和futex的阻塞有什么异同？


## 来源
- 项目：[[bio-chatroom]]
- 对话：[[2026-04-15-bio-dialogue]]
- 练习：[[2026-04-15-bio-chatroom]]


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-04-15: mastery=40 (完成BIO聊天室项目，理解阻塞特性和局限性)

### 建议下一步
1. 进行BIO性能测试，验证C10K问题
2. 学习NIO模型，对比非阻塞IO的优势
3. 理解Selector多路复用机制

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #nio
SORT mastery DESC
```
