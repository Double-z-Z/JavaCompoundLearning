# NIO学习大纲

> 基于你的并发编程基础，链接NIO与多线程的知识网络

## 前置知识链接

你已经掌握的并发知识将帮助你理解NIO：
- **线程池** → Selector的事件循环机制
- **阻塞队列** → Channel的读写缓冲区
- **CAS原子操作** → Buffer的状态更新
- **锁机制** → Channel的线程安全访问

---

## 第一阶段：BIO基础（1天）

### 1.1 IO流体系
- InputStream/OutputStream 字节流
- Reader/Writer 字符流
- 装饰器模式的应用（BufferedInputStream、DataInputStream）
- 字符编码（ASCII、Unicode、UTF-8）

### 1.2 BIO阻塞原理
```
Socket.read() → OS系统调用recv() → 数据未到达 → 线程阻塞
```
- 阻塞的本质：线程状态从RUNNABLE变为WAITING
- 一线程一连接的局限性：C10K问题

### 1.3 与并发知识的链接
- BIO的线程模型 vs 线程池的任务分发
- 阻塞IO如何浪费线程资源

---

## 第二阶段：NIO核心三大件（3天）

### 2.1 Buffer缓冲区

#### 核心概念
- **三个关键属性**：position、limit、capacity
- **状态转换**：flip()、clear()、compact()
- **堆内存 vs 直接内存**：HeapByteBuffer vs DirectByteBuffer

#### 与并发知识的链接
- Buffer的线程不安全特性（类似ArrayList）
- DirectByteBuffer的堆外内存管理（类似堆外缓存）

#### 实践要点
```java
// 手写flip方法逻辑
public void flip() {
    limit = position;
    position = 0;
    mark = -1;
}
```

### 2.2 Channel通道

#### 核心概念
- **双向通信**：同时支持读和写
- **非阻塞模式**：configureBlocking(false)
- **零拷贝**：transferTo/transferFrom

#### 零拷贝原理
```
传统IO：磁盘 → 内核缓冲区 → 用户缓冲区 → Socket缓冲区 → 网卡
零拷贝：磁盘 → 内核缓冲区 → Socket缓冲区 → 网卡（减少一次拷贝）
```

#### 与并发知识的链接
- Channel的线程安全特性（类似ConcurrentHashMap的分段锁）
- 多线程同时读写同一个Channel的问题

### 2.3 Selector多路复用

#### 核心概念
- **事件驱动**：OP_ACCEPT、OP_CONNECT、OP_READ、OP_WRITE
- **多路复用**：一个线程管理多个Channel
- **底层实现**：select → poll → epoll的演进

#### 与线程池的类比
```
Selector（单线程） ≈ 线程池的调度线程
Channel ≈ 任务队列中的任务
SelectionKey ≈ 任务包装器
```

#### 空轮询Bug
- 现象：epoll_wait无事件返回，但返回值为0
- 影响：CPU 100%
- 解决：Netty的计数器方案（selectCnt > 512时重建Selector）

---

## 第三阶段：NIO高级主题（2天）

### 3.1 内存映射文件
- MappedByteBuffer原理
- 文件映射到虚拟内存，读写如同操作数组
- 适用场景：大文件处理、进程间通信

### 3.2 IO模型对比

| 模型 | 阻塞/非阻塞 | 同步/异步 | Java实现 |
|------|-----------|----------|---------|
| BIO | 阻塞 | 同步 | InputStream |
| NIO | 非阻塞 | 同步 | Channel + Selector |
| AIO | 非阻塞 | 异步 | AsynchronousChannel |

### 3.3 Reactor模式
- **单线程Reactor**：Accept和IO处理都在一个线程
- **多线程Reactor**：Accept单独线程，IO处理线程池
- **主从多线程Reactor**：Accept和IO都有线程池（Netty默认）

#### 与线程池的链接
```
Reactor的线程模型 ≈ 线程池的调度策略
主从Reactor ≈ ForkJoinPool的工作窃取
```

---

## 第四阶段：Netty框架（5天）

### 4.1 Netty核心组件
- **EventLoop**：事件循环，绑定一个Selector
- **ChannelPipeline**：责任链模式，处理入站/出站事件
- **ByteBuf**：增强版ByteBuffer，支持动态扩容、引用计数

### 4.2 Netty与NIO的对比

| 特性 | NIO | Netty |
|------|-----|-------|
| API复杂度 | 复杂 | 简洁 |
| 内存管理 | 手动 | 池化+引用计数 |
| 粘包拆包 | 手动处理 | 内置解码器 |
| 性能优化 | 手动 | 自动（零拷贝、内存池） |

### 4.3 线程模型
- **Boss Group**：处理Accept事件（通常1个线程）
- **Worker Group**：处理IO事件（通常CPU核心数*2）
- **业务线程池**：处理耗时业务逻辑

---

## 第五阶段：序列化（2天）

### 5.1 Java原生序列化
- Serializable接口
- serialVersionUID的作用
- transient关键字
- 自定义序列化：writeObject/readObject

### 5.2 高性能序列化框架
- **Protobuf**：Google出品，二进制，跨语言
- **Kryo**：Java专用，高性能，无需Schema
- **Hessian**：RPC友好，兼容性好

### 5.3 序列化与NIO的结合
- ByteBuf的编解码
- LengthFieldBasedFrameDecoder解决粘包问题
- 自定义协议设计

---

## 知识图谱关联

```
并发编程
├── 线程池 ──────┐
├── 阻塞队列 ────┼──→ NIO Selector/Channel
└── CAS/锁 ──────┘

NIO
├── Buffer ──────┐
├── Channel ─────┼──→ Netty ByteBuf/Channel
└── Selector ────┘

Netty
├── 编解码 ──────┐
└── 协议设计 ────┼──→ RPC框架

序列化
└── 高效传输 ────┘
```

---

## 推荐实践顺序

1. **Day 1**：BIO聊天室（理解阻塞）
2. **Day 2-3**：手写Buffer状态机 + NIO单线程聊天室
3. **Day 4**：零拷贝文件传输
4. **Day 5-6**：手写Reactor模式
5. **Day 7-9**：Netty HTTP服务器
6. **Day 10-11**：RPC框架（整合所有知识）
