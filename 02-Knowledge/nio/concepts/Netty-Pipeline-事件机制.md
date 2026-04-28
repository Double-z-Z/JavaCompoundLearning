---
created: 2026-04-25
tags: [nio, netty]
status: 🌿
mastery: 55
---

# Netty Pipeline 事件机制

## 一句话定义
Netty 的 ChannelPipeline 是责任链模式的实现，通过双向链表组织 Handler，入站事件从 Head 向 Tail 传播，出站操作从 Tail 向 Head 传播。


## 核心理解

### Pipeline 结构
```
┌─────────────────────────────────────────────────────────────────┐
│                     Pipeline 结构                                │
│                                                                   │
│   入站方向（事件传播）      出站方向（操作传播）                     │
│   ──────────────────        ──────────────────                   │
│                                                                   │
│        Head ─────────────────────────► Tail                       │
│   ┌─────────┐                    ┌─────────┐                     │
│   │  Head   │◀───────────────────│  Tail   │                     │
│   │ (入站入口)│   出站操作反向传播   │ (出站入口)│                     │
│   └────┬────┘                    └────┬────┘                     │
│        │                              │                          │
│        │    ┌─────────┐    ┌─────────┐│                          │
│        └───▶│ Decoder │    │ Encoder │◀┘                          │
│             │(入站处理)│    │(出站处理)│                           │
│             └────┬────┘    └────┬────┘                           │
│                  │              │                                │
│        ┌─────────┴──────────────┴─────────┐                      │
│        │                                  │                      │
│        ▼                                  ▼                      │
│   ┌─────────┐                        ┌─────────┐                 │
│   │ Session │                        │  Chat   │                 │
│   │ Handler │                        │ Handler │                 │
│   │(业务处理)│                        │(业务处理)│                 │
│   └─────────┘                        └─────────┘                 │
│                                                                   │
│   关键：入站从头开始，出站从尾开始！                                │
└─────────────────────────────────────────────────────────────────┘
```

### 入站事件（Inbound）- 被动触发
```
触发源：网络数据到达、连接建立/断开等

channelRegistered   ← Channel 注册到 EventLoop
channelActive       ← Channel 就绪（连接建立）
channelRead         ← 读到数据（被动触发）
channelReadComplete ← 本次读取完成（一个读操作结束）
channelInactive     ← Channel 不再就绪
channelUnregistered ← Channel 注销
```

### 出站操作（Outbound）- 主动调用
```
触发源：应用代码主动调用

bind()      ← 绑定端口
connect()   ← 连接服务端
read()      ← 请求读取数据（触发一次读操作）
write()     ← 写入数据
flush()     ← 刷新缓冲区
close()     ← 关闭连接
```

### 关键区别：事件 vs 操作

| | `channelRead()` | `read()` | `write()` |
|---|---|---|---|
| **方向** | 入站 | 出站 | 出站 |
| **类型** | 事件 | 操作 | 操作 |
| **触发方式** | 被动触发 | 主动调用 | 主动调用 |
| **谁调用** | Netty 框架 | 应用代码 | 应用代码 |
| **作用** | 通知有数据可读 | 请求读取数据 | 发送数据 |
| **传播方向** | Head → Tail | Tail → Head | Tail → Head |

### 出站操作传播机制
```java
// 应用层调用
ctx.write(response);

// 从当前 Handler 向前传播（出站方向）
AbstractChannelHandlerContext.write()
    ├── 找到下一个出站 Handler（findContextOutbound）
    ├── 调用下一个 Handler 的 write()
    └── 直到 Head，将数据写入 Channel

// 传播链：
// SessionHandler.write() → ProtocolEncoder.write() → HeadContext.write()
```

### Channel 与 Pipeline 的关系
- **每个 Channel 有自己的 Pipeline**：`channel.pipeline()` 返回独立的 Pipeline 实例
- **Pipeline 在 Channel 创建时初始化**：`ChannelInitializer.initChannel()`
- **Channel 生命周期内 Pipeline 不变**：Handler 可以动态增删，但 Pipeline 实例不变


## 关键关联

- [[ChannelHandler]] - 关联原因：Pipeline 是 Handler 的容器，Handler 是事件处理的基本单元
- [[ChannelInboundHandler]] - 关联原因：处理入站事件（读数据、连接建立等）
- [[ChannelOutboundHandler]] - 关联原因：处理出站操作（写数据、连接等）
- [[ChannelHandlerContext]] - 关联原因：Handler 与 Pipeline 交互的上下文，负责事件传播
- [[责任链模式]] - 关联原因：Pipeline 是责任链模式的经典实现


## 我的误区与疑问

- ❌ 误区：以为 `read()` 和 `channelRead()` 是一回事
  - ✅ 纠正：`read()` 是出站操作（主动请求读取），`channelRead()` 是入站事件（被动通知有数据）

- ❌ 误区：以为整个 Server 共享同一个 Pipeline
  - ✅ 纠正：每个 Channel 有自己的 Pipeline，Server 有 N 个连接就有 N 个 Pipeline

- ❌ 误区：以为出站操作也是"事件"
  - ✅ 纠正：出站是"操作"（命令框架去做），入站是"事件"（框架通知你发生了什么）


## 代码与实践

### Pipeline 配置
```java
public class ChatServerInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        
        // 入站解码器（先添加的先执行）
        pipeline.addLast(new ProtocolDecoder());
        
        // 业务 Handler
        pipeline.addLast(sessionHandler);
        pipeline.addLast(chatHandler);

        // 出站编码器（后添加的先执行）
        pipeline.addLast(new ProtocolEncoder());
    }
}
```

### Handler 实现
```java
// 入站 Handler
public class SessionHandler extends SimpleChannelInboundHandler<Message> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        // 处理入站数据
        
        // 发送响应（出站操作）
        ctx.write(response);  // 从当前 Handler 向前传播
    }
}

// 出站 Handler
public class ProtocolEncoder extends MessageToByteEncoder<Message> {
    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) {
        // 编码并写入 out
        // 数据会自动传递给下一个出站 Handler，直到 Head
    }
}
```


## 深入思考

💡 为什么入站和出站方向相反？
- **对称设计**：编码器靠近网络层（Head），业务处理器在中间
- **数据流向**：入站从网络到应用，出站从应用到网络
- **职责分离**：入站处理"收到的数据"，出站处理"发送的数据"

💡 `channelReadComplete` 的边界是什么？
- 表示**一次读操作**完成，不是**一个完整消息**
- 一个消息可能被拆分到多个 `channelRead` 中（TCP 粘包/拆包）
- 消息完整性由 Decoder 判定（如 `ByteToMessageDecoder`）


## 来源
- 项目：[[netty-chatroom]]
- 对话：[[2026-04-25-Pipeline-事件机制-对话]]


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-04-25: mastery=55 (理解入站/出站区别，能解释传播机制)

### 建议下一步
1. 阅读 Netty 源码，理解 `DefaultChannelPipeline` 的实现
2. 实践：实现自定义的 Inbound/Outbound Handler
3. 深入：理解 `ChannelHandlerContext` 在事件传播中的作用

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #nio OR #netty
SORT mastery DESC
```
