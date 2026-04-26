---
created: 2026-04-24
tags: [nio, netty, framework]
status: 🌿
mastery: 55
---

# Netty

## 一句话定义
Netty 是基于 Java NIO 的高性能异步事件驱动网络应用框架，通过封装 NIO 复杂性，提供简单的 API 实现高并发网络程序。


## 核心理解

### Netty 与 NIO 的关系

```
┌─────────────────────────────────────────┐
│           你的应用程序                    │
│    （只需关注业务逻辑：编解码、处理消息）    │
├─────────────────────────────────────────┤
│           Netty 框架层                   │
│  ┌─────────┐ ┌─────────┐ ┌───────────┐ │
│  │EventLoop│ │ Channel │ │ Pipeline  │ │
│  │ 事件循环 │ │ 通道抽象 │ │ 处理器链   │ │
│  └─────────┘ └─────────┘ └───────────┘ │
├─────────────────────────────────────────┤
│           Java NIO 层                   │
│    Selector + Channel + Buffer          │
├─────────────────────────────────────────┤
│           操作系统层                     │
│    epoll / kqueue / IOCP                │
└─────────────────────────────────────────┘
```

### 核心组件

| 组件 | 类比（NIO概念） | 核心职责 |
|------|----------------|---------|
| **EventLoop** | Boss + Worker 线程 | 事件循环，线程管理，Channel 绑定 |
| **Channel** | SocketChannel | 连接抽象，I/O 操作入口 |
| **Pipeline** | 处理链 | 组织 ChannelHandler，管理数据流转 |
| **ByteBuf** | ByteBuffer（增强版） | 动态扩容，读写分离，引用计数，零拷贝切片 |
| **Handler** | 业务处理逻辑 | 编解码、业务处理、拦截过滤 |

### EventLoop 的关键设计

**Channel 绑定 EventLoop**：
- 一个 Channel 的所有 I/O 操作都在同一个 EventLoop 线程执行
- 消除竞态条件，无需同步
- 保证消息处理顺序

**代价**：
- 负载可能不均（某个 Channel 繁忙会拖垮绑定的 EventLoop）
- 长时间任务会阻塞该 EventLoop 上的所有 Channel

### ByteBuf 双指针设计

```
┌─────────────────────────────────────────┐
│              ByteBuf                    │
│  0      readerIndex    writerIndex   capacity │
│  │         │              │            │    │
│  ▼         ▼              ▼            ▼    │
│  ├─────────┼──────────────┼────────────┤    │
│  │ 已读区域 │   可读区域    │  可写区域   │    │
│  │ (可丢弃) │             │            │    │
│  └─────────┴──────────────┴────────────┘    │
└─────────────────────────────────────────┘
```

**优势**：
- 无需 flip() 切换读写模式
- 读写可同时进行（单线程内）
- 支持随机访问（getXX(index) 不影响 readerIndex）

### 引用计数机制

```java
ByteBuf buf = ctx.alloc().buffer();  // refCnt = 1
ByteBuf slice = buf.retain().slice(); // refCnt = 2

// 广播给其他 Channel
channelB.writeAndFlush(slice.retain()); // refCnt = 3
channelC.writeAndFlush(slice.retain()); // refCnt = 4

// 各自释放
slice.release();  // refCnt = 3
// B 完成发送... refCnt = 2
// C 完成发送... refCnt = 1
buf.release();    // refCnt = 0，真正释放
```

**关键规则**：
- 创建时 refCnt = 1
- retain() 增加引用
- release() 减少引用，为 0 时归还内存池
- 跨 Channel 传递必须 retain()

### Pipeline 责任链模式

```
入站:  Socket ──▶ Decoder ──▶ Auth ──▶ BizHandler
                              │
出站:  Socket ◀── Encoder ◀──┘
```

**传播机制**：
- 入站：框架自动调用 `fireChannelRead()` 传播
- 出站：`ctx.write()` 进入出站缓冲区
- 解码器：通过 `out.add(msg)` + 父类遍历触发传播

### 半包处理

Netty 提供 `ByteToMessageDecoder` 基类：
- 内部维护 `cumulation` 累积缓冲区
- 自动合并多次读取的数据
- 子类只需实现 `decode()` 方法
- 解析不完整的返回，框架保留剩余数据


## 关键关联

- [[NIO-Selector]] - 关联原因：EventLoop 底层基于 Selector 实现多路复用，Netty 封装了 Selector 的注册、wakeup、事件处理等复杂性
- [[NIO-Buffer]] - 关联原因：ByteBuf 是对 ByteBuffer 的增强，双指针设计替代了 flip/clear 模式，引用计数实现安全共享
- [[Boss-Worker模型]] - 关联原因：EventLoopGroup 可以配置为 Boss+Worker 模式，但 Netty 更灵活，支持单线程、多线程、主从多线程等多种模型
- [[线程池]] - 关联原因：EventLoop 本质上是特殊的线程池，但 Channel 绑定线程，消除了传统线程池的任务竞争
- [[粘包拆包]] - 关联原因：Netty 的 ByteToMessageDecoder 提供了标准的半包处理框架，解决 TCP 字节流无消息边界问题


## 我的误区与疑问

- ❌ 曾认为 ByteBuf 双指针是为了读写并发（实际是在单线程内避免 flip）
- ❌ 曾困惑为什么需要 CompositeByteBuf（实际是为了用户空间零拷贝组合，减少框架层开销）
- ❓ Netty 的内存池（PooledByteBufAllocator）与 JVM GC 的交互细节
- ❓ 高并发下 EventLoop 的负载均衡策略


## 代码与实践

### 最简单的 Echo 服务端

```java
public class EchoServer {
    public static void main(String[] args) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new EchoServerHandler());
                    }
                });
            
            ChannelFuture future = bootstrap.bind(8080).sync();
            future.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}

@Sharable
public class EchoServerHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ctx.write(msg);  // 写回客户端
    }
    
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }
}
```

### 自定义解码器（处理半包）

```java
public class MessageDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < 4) return;
        
        int length = in.readInt();
        if (in.readableBytes() < length) {
            in.readerIndex(in.readerIndex() - 4);  // 回退
            return;
        }
        
        ByteBuf frame = in.readRetainedSlice(length);
        out.add(frame);  // 传递给下一个 handler
    }
}
```


## 深入思考

💡 Netty 的设计哲学是"把复杂性转移到框架内部"，但这也带来了学习成本。如何平衡"使用框架"与"理解底层"？

💡 ByteBuf 的引用计数机制解决了共享内存的安全问题，但也增加了心智负担。在实际项目中，如何确保不会内存泄漏？

💡 EventLoop 的 Channel 绑定设计消除了锁竞争，但也限制了负载均衡。在什么场景下这种设计会成为瓶颈？


## 来源
- 项目：[[nio-chatroom]]（前置知识）
- 对话：[[2026-04-24-netty-learning-dialogue]]


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-04-24: mastery=55 (深入理解核心组件、引用计数、Pipeline机制，能解释设计原理)

### 建议下一步
1. 编写一个完整的 Netty 应用（如简单的 RPC 框架）
2. 深入学习 Netty 的内存池实现（PooledByteBufAllocator）
3. 对比 Netty 与其他 NIO 框架（如 MINA、Vert.x）的设计差异

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #nio
SORT mastery DESC
```
