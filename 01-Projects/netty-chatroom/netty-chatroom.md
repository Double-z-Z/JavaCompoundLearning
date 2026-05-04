---
created: 2026-04-24
updated: 2026-04-26
status: Phase 3 - WebSocket支持已完成
tags: [project, netty, 聊天室, 网络编程, websocket]
---

# Netty 聊天室项目

> 基于 Netty 4.x 的高性能多客户端聊天室服务端


## 项目概述

### 目标
实现一个基于 Netty 的聊天室服务端，实践 Netty 核心概念：
- EventLoop 线程模型
- ChannelPipeline 责任链
- ByteBuf 内存管理
- Handler 设计模式

### 技术栈
- Netty 4.1.100.Final
- Jackson JSON 处理
- SLF4J + Logback 日志
- Maven 构建


## 项目结构

```
netty-chatroom/
├── pom.xml                          # Maven 配置
├── src/
│   ├── main/
│   │   ├── java/com/example/server/
│   │   │   ├── ChatServer.java          # 服务端入口
│   │   │   ├── ChatServerInitializer.java  # Pipeline 组装
│   │   │   ├── protocol/                # 协议包
│   │   │   │   ├── Message.java         # 消息基类
│   │   │   │   ├── ChatMessage.java     # 聊天消息
│   │   │   │   ├── IdentifyMessage.java # 认证消息
│   │   │   │   ├── HeartbeatMessage.java # 心跳消息
│   │   │   │   ├── SystemMessage.java   # 系统消息
│   │   │   │   ├── UserListMessage.java # 用户列表
│   │   │   │   └── ProtocolCodec.java   # 编解码工具
│   │   │   ├── handler/                 # Handler 包
│   │   │   │   ├── ProtocolDecoder.java # 协议解码器
│   │   │   │   ├── ProtocolEncoder.java # 协议编码器
│   │   │   │   ├── IdentifyHandler.java # 认证处理器
│   │   │   │   └── ChatHandler.java     # 业务处理器
│   │   │   ├── service/                 # 服务层
│   │   │   │   ├── SessionManager.java  # 会话管理
│   │   │   │   └── MessageService.java  # 消息服务
│   │   │   └── guard/
│   │   │       └── HealthKeeper.java    # 心跳检测
│   │   └── resources/
│   │       └── logback.xml
│   └── test/
└── logs/
```


## 架构设计

### 线程模型
```
ChatServer 主线程
    ↓
BossGroup (1线程) ──▶ 处理 accept
    ↓
WorkerGroup (N线程) ──▶ 处理 I/O
    ↓
Handler 链条（单线程内执行）
    - ProtocolDecoder
    - IdentifyHandler
    - ChatHandler
    - ProtocolEncoder

HealthKeeper (独立线程)
    ↓ 定时任务
SessionManager (共享状态，ConcurrentHashMap 保护)
```

### Pipeline 设计
```
入站方向（Socket → 应用）：
┌─────────────────────────────────────────┐
│  1. ProtocolDecoder                     │
│     长度前缀解码 + JSON 解析              │
│          ↓                              │
│  2. IdentifyHandler                     │
│     认证 + 心跳处理                       │
│          ↓                              │
│  3. ChatHandler                         │
│     业务消息处理                          │
└─────────────────────────────────────────┘

出站方向（应用 → Socket）：
┌─────────────────────────────────────────┐
│  1. ProtocolEncoder                     │
│     JSON 编码 + 长度前缀                  │
└─────────────────────────────────────────┘
```

### 协议格式
```
长度前缀(4字节) + JSON 内容

消息类型：
- IDENTIFY              认证消息
- CHAT                  聊天消息（支持私聊/广播）
- HEARTBEAT             心跳消息
- SYSTEM                系统消息（用户加入/离开）
- USER_LIST             用户列表
- HISTORY_REQUEST       历史消息请求
- HISTORY_RESPONSE      历史消息响应
```


## 当前进度

### ✅ 已完成

#### Phase 1: 核心功能
- [x] Maven 项目搭建
- [x] 消息协议设计（JSON + 长度前缀）
- [x] 编解码器实现（ProtocolDecoder/Encoder）
- [x] 用户认证（IdentifyHandler）
- [x] 聊天消息处理（ChatHandler）
- [x] 会话管理（SessionManager）
- [x] 消息服务（MessageService）
- [x] 心跳检测（HealthKeeper）
- [x] 广播/私聊功能
- [x] 用户列表查询

### ✅ Phase 2: 增强功能（已完成）
- [x] 测试客户端实现（基础功能验证）
- [x] 消息持久化（最近100条，内存存储）
- [x] ~~压力测试~~（跳过，使用专业工具替代）
- [x] ~~配置文件支持~~（跳过，非核心需求）

### ✅ Phase 3: 高级功能（进行中）

#### Phase 3: WebSocket 支持（已完成）
- [x] WebSocket 协议理解与实现
- [x] WebSocketServerProtocolHandler 配置
- [x] HTTP 握手升级处理
- [x] WebSocketFrame 编解码
- [x] 双协议支持（TCP + WebSocket）
- [x] 浏览器客户端测试
- [x] 自动化测试覆盖

#### 待完成
- [ ] 集群部署（多节点消息同步）
- [ ] 监控指标（连接数、消息量）


## 练习记录

- [2026-04-24 Netty聊天室实现](../03-Practice/drills/2026-04-24-netty-chatroom.md) — Phase 1 核心功能
- [2026-04-26 Netty聊天室Phase2](../03-Practice/drills/2026-04-26-netty-chatroom-phase2.md) — 消息持久化
- [2026-04-26 WebSocket协议对话](../03-Practice/reflections/2026-04-26-websocket-protocol-dialogue.md) — WebSocket 原理深度理解


## 关键技术点

### 1. 半包处理
```java
// ProtocolDecoder 自动处理
if (in.readableBytes() < length) {
    in.resetReaderIndex();  // 数据不够，等待下次
    return;
}
```

### 2. 会话管理
```java
// SessionManager 维护多映射
userId -> Channel          // 发送消息
ChannelId -> userId        // 断开时清理
ChannelId -> lastActiveTime // 心跳检测
```

### 3. 心跳机制
```
HealthKeeper (30秒周期)
    ↓
扫描超时 Channel (60秒)
    ↓
发送心跳检测包
    ↓
等待 5 秒响应
    ↓
无响应则关闭连接
```

### 4. Handler 共享
```java
@ChannelHandler.Sharable
public class IdentifyHandler extends SimpleChannelInboundHandler<Message> {
    // Handler 实例可复用，无状态设计
}
```


## 运行方式

```bash
# 编译
mvn clean package

# 运行（默认端口 8080）
java -jar target/netty-chatroom-1.0-SNAPSHOT.jar

# 指定端口
java -jar target/netty-chatroom-1.0-SNAPSHOT.jar 9090
```


## 关联知识

- [[Netty]] - Netty 框架核心概念
- [[NIO-Selector]] - 底层多路复用机制
- [[NIO-Buffer]] - ByteBuf 设计原理
- [[Boss-Worker模型]] - 线程模型设计
- [[粘包拆包]] - 协议设计


## 练习记录

- [2026-04-24 Netty聊天室实现](../03-Practice/drills/2026-04-24-netty-chatroom.md)


## 反思记录

- [2026-04-24 Netty学习对话](../03-Practice/reflections/2026-04-24-netty-learning-dialogue.md)


## 待办清单

- [x] 编写测试客户端
- [ ] 压力测试
- [x] 消息持久化


---

*最后更新：2026-04-24*
