---
created: 2026-04-17
updated: 2026-04-19
status: Phase 2 - 实现客户端连接和消息转发
tags: [project, NIO, 聊天室, 网络编程]
---

# NIO聊天室项目

> 基于Java NIO的非阻塞聊天室服务端，实现Boss-Worker线程模型


## 项目概述

### 目标
实现一个高性能的NIO聊天室服务端，支持：
- 多客户端并发连接
- 消息广播（跨Worker）
- 优雅关闭
- 性能可扩展

### 技术栈
- Java NIO (Selector, Channel, Buffer)
- Boss-Worker线程模型
- ConcurrentLinkedQueue消息队列


## 项目结构

```
nio-chatroom/
├── src/main/java/com/example/Server/
│   ├── ChatServer.java          # 服务端启动类
│   ├── ClientManager.java       # Boss线程，管理连接分配
│   ├── ServerHandler.java       # Worker线程，处理读写
│   ├── ClientContext.java       # 客户端上下文
│   └── ChatMessage.java         # 消息封装
├── src/test/java/com/example/Server/
│   ├── ChatServerIntegrationTest.java
│   ├── ClientManagerShutdownTest.java
│   ├── BroadcastTest.java       # 广播功能测试
│   └── BroadcastPerformanceTest.java  # 性能测试
├── pom.xml
└── TODO.md                      # 待办清单
```


## 架构设计

### 线程模型
```
┌─────────────────────────────────────────────────────────┐
│                      Boss线程                            │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐ │
│  │ Selector    │    │ 监听ACCEPT  │    │ 分配Worker  │ │
│  └─────────────┘    └─────────────┘    └─────────────┘ │
└─────────────────────────────────────────────────────────┘
                           │
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│   Worker 1    │  │   Worker 2    │  │   Worker N    │
│ ┌───────────┐ │  │ ┌───────────┐ │  │ ┌───────────┐ │
│ │ Selector  │ │  │ │ Selector  │ │  │ │ Selector  │ │
│ │ 管理Channel│ │  │ │ 管理Channel│ │  │ │ 管理Channel│ │
│ │ 处理READ  │ │  │ │ 处理READ  │ │  │ │ 处理READ  │ │
│ │ 处理WRITE │ │  │ │ 处理WRITE │ │  │ │ 处理WRITE │ │
│ └───────────┘ │  │ └───────────┘ │  │ └───────────┘ │
└───────────────┘  └───────────────┘  └───────────────┘
```

### 消息广播流程
```
Client A (Worker 1) 发送消息
    ↓
Worker 1 读取消息
    ↓
Worker 1.messageQueue.offer()  ← 入队自己的队列
    ↓
ClientManager.broadcast() 转发给其他Worker
    ↓
Worker 2, 3 收到消息并入队
    ↓
所有Worker的 processBroadcastMessages() 消费队列
    ↓
broadcastToAll() 发送给管理的所有Channel（排除来源）
```


## 当前进度

### ✅ 已完成

#### Phase 1: 基础架构
- [x] Boss线程实现（ClientManager）
- [x] Worker线程实现（ServerHandler）
- [x] 客户端连接管理
- [x] 优雅关闭流程

#### Phase 2: 消息转发（部分完成）
- [x] 消息队列设计（ConcurrentLinkedQueue）
- [x] 广播功能基础实现
- [x] 写缓冲区处理（OP_WRITE管理）
- [x] 跨Worker消息转发
- [x] 资源关闭优化（集成java-io-utils）

### 🚧 进行中
- [ ] 广播功能测试验证
- [ ] 性能基准测试

### ⏳ 待开始
- [ ] 消息协议设计（JSON）
- [ ] 私聊功能
- [ ] 共享队列优化方案
- [ ] 性能优化（Buffer池化）


## 关键技术点

### 1. OP_WRITE事件管理
```java
// 有待发送数据时注册
channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

// 发送完成后取消
key.interestOps(SelectionKey.OP_READ);
```

### 2. 写半包处理
```java
int written = channel.write(buffer);
if (written < messageBytes.length) {
    // 部分发送，剩余入队
    byte[] remaining = // ...提取剩余数据
    context.pendingMessages.offer(remaining);
}
```

### 3. 跨Worker广播
```java
// ClientManager转发
public void broadcast(ServerHandler source, String message) {
    for (ServerHandler handler : workers) {
        if (handler != null && handler != source) {
            handler.broadcast(message);
        }
    }
}
```


## 关联知识

- [[NIO-Selector]] - Selector线程模型和事件处理
- [[NIO-Channel]] - Channel管理和操作
- [[ConcurrentLinkedQueue]] - 无锁消息队列
- [[资源关闭最佳实践]] - IoUtils设计原理
- [[线程池]] - Worker线程管理


## 练习记录

- [2026-04-19 广播功能实现](../03-Practice/drills/2026-04-19-broadcast-implementation.md)


## 反思记录

- [2026-04-19 广播设计讨论](../03-Practice/reflections/2026-04-19-broadcast-design-dialogue.md)


## 待办清单

详见 [TODO.md](./TODO.md)


---

*最后更新：2026-04-19*
