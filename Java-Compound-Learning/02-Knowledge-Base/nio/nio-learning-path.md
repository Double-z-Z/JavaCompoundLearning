# NIO学习路线

> 基于你的并发编程基础，链接NIO与多线程的知识网络

## 前置知识链接

你已经掌握的并发知识将帮助你理解NIO：

| 并发知识 | NIO对应概念 | 类比说明 |
|---------|------------|---------|
| 线程池 | Selector事件循环 | 都是任务调度机制 |
| 阻塞队列 | Channel缓冲区 | 都是数据暂存区域 |
| CAS原子操作 | Buffer状态更新 | 都是无锁更新机制 |
| 锁机制 | Channel线程安全 | 都需要考虑并发访问 |

---

## 第一阶段：BIO基础（1天）

### 1.1 IO流体系

#### 字节流 vs 字符流
```
InputStream/OutputStream（字节流）
    ↓ 装饰器模式
BufferedInputStream → 增加缓冲
DataInputStream → 增加基本类型读写
ObjectInputStream → 增加对象序列化

Reader/Writer（字符流）
    ↓ 装饰器模式
BufferedReader → 增加缓冲
InputStreamReader → 桥接字节流到字符流（解码）
```

#### 装饰器模式在IO中的应用
```java
// 层层包装，增强功能
InputStream is = new FileInputStream("file.txt");
BufferedInputStream bis = new BufferedInputStream(is);  // 增加缓冲
DataInputStream dis = new DataInputStream(bis);         // 增加数据类型支持
```

#### 字符编码
- **ASCII**：1字节，英文字符
- **Unicode**：2字节，全球字符集
- **UTF-8**：变长编码，兼容ASCII，中文3字节

### 1.2 BIO阻塞原理

#### 阻塞的本质
```
Socket.read() 
  ↓
OS系统调用recv()
  ↓
数据未到达 → 线程阻塞（WAITING状态）
  ↓
数据到达 → 唤醒线程 → 继续执行
```

#### 一线程一连接的局限性
```java
// 传统BIO服务器
while (true) {
    Socket socket = serverSocket.accept();  // 阻塞
    new Thread(() -> {
        handle(socket);  // 每个连接一个线程
    }).start();
}
```

**问题**：
- 线程资源消耗大（1线程 ≈ 1MB栈内存）
- 上下文切换开销
- C10K问题（无法支持1万并发连接）

### 1.3 与并发知识的链接

| BIO问题 | 并发解决方案 | NIO解决方案 |
|--------|------------|------------|
| 线程过多 | 线程池限流 | 单线程处理多连接 |
| 阻塞等待 | 异步回调 | 非阻塞+事件驱动 |
| 资源浪费 | 队列缓冲 | 缓冲区复用 |

---

## 第二阶段：NIO核心三大件（3天）

### 2.1 Buffer缓冲区

#### 核心属性
```
┌─────────────────────────────────────┐
│  0  │  1  │  2  │  3  │  4  │  5  │  ← capacity = 6
├─────┼─────┼─────┼─────┼─────┼─────┤
│  H  │  e  │  l  │  l  │  o  │     │
└─────┴─────┴─────┴─────┴─────┴─────┘
  ↑                           ↑
position = 0              limit = 5

position: 下一个读写位置
limit: 最大可读写位置
capacity: 缓冲区容量
```

#### 状态转换
```java
// 写入数据
ByteBuffer buffer = ByteBuffer.allocate(1024);
buffer.put("Hello".getBytes());
// position = 5, limit = 1024

// 切换为读模式
buffer.flip();
// position = 0, limit = 5

// 读取数据
byte[] data = new byte[buffer.remaining()];
buffer.get(data);

// 清空，准备再次写入
buffer.clear();
// position = 0, limit = 1024

// 压缩（保留未读数据，继续写入）
buffer.compact();
```

#### 手写flip方法
```java
public void flip() {
    limit = position;   // 限制设为当前位置
    position = 0;       // 位置归零
    mark = -1;          // 清除标记
}
```

#### 堆内存 vs 直接内存
```java
// 堆内存（HeapByteBuffer）
ByteBuffer heapBuffer = ByteBuffer.allocate(1024);
// 数据在JVM堆中，读写需要拷贝到内核缓冲区

// 直接内存（DirectByteBuffer）
ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024);
// 数据在堆外内存，直接对接OS内核，零拷贝
```

**直接内存注意事项**：
- 分配和回收成本高（malloc/free）
- 不受GC管理，需手动释放
- 设置上限：`-XX:MaxDirectMemorySize=256m`

#### 与并发知识的链接
- Buffer是**线程不安全**的（类似ArrayList）
- 多线程访问需要同步或使用ThreadLocal

### 2.2 Channel通道

#### 核心特性
- **双向通信**：同时支持读和写
- **非阻塞模式**：可配置为非阻塞
- **直接传输**：支持零拷贝

#### FileChannel零拷贝
```java
// 传统IO：4次拷贝
// 磁盘 → 内核缓冲区 → 用户缓冲区 → Socket缓冲区 → 网卡

// 零拷贝：2次拷贝（transferTo）
// 磁盘 → 内核缓冲区 → 网卡（通过DMA）
FileChannel sourceChannel = new FileInputStream("source.zip").getChannel();
FileChannel targetChannel = new FileInputStream("target.zip").getChannel();

sourceChannel.transferTo(0, sourceChannel.size(), targetChannel);
```

#### SocketChannel非阻塞模式
```java
SocketChannel channel = SocketChannel.open();
channel.configureBlocking(false);  // 设置为非阻塞

// 非阻塞connect
channel.connect(new InetSocketAddress("localhost", 8080));

// 需要轮询或使用Selector检查连接状态
while (!channel.finishConnect()) {
    // 做其他事情...
}
```

#### 与并发知识的链接
- Channel的某些操作是**线程安全**的（如read/write）
- 但**注册到Selector**和**改变阻塞模式**需要同步

### 2.3 Selector多路复用

#### 核心概念
```
Selector（多路复用器）
    ↓ 管理多个Channel
SelectionKey（选择键）
    ↓ 绑定Channel和事件
Channel（通道）
    ↓ 实际IO操作
```

#### 事件类型
- **OP_ACCEPT**：接受连接（ServerSocketChannel）
- **OP_CONNECT**：连接完成（SocketChannel）
- **OP_READ**：可读
- **OP_WRITE**：可写

#### 与线程池的类比
```
Selector（单线程） ≈ 线程池的调度线程
Channel ≈ 任务队列中的任务
SelectionKey ≈ 任务包装器（包含任务状态和类型）
事件循环 ≈ 线程池的任务分发循环
```

#### 空轮询Bug
```java
// 问题：epoll_wait无事件返回，但返回值为0
// 导致：while循环空转，CPU 100%

// Netty解决方案
if (selectCnt > 512) {
    // 重建Selector
    selector = selectRebuildSelector();
}
```

---

## 第三阶段：NIO高级主题（2天）

### 3.1 内存映射文件

```java
FileChannel channel = new RandomAccessFile("file.txt", "rw").getChannel();
MappedByteBuffer buffer = channel.map(
    FileChannel.MapMode.READ_WRITE, 0, channel.size()
);

// 读写如同操作数组
buffer.put(0, (byte) 'H');
```

**适用场景**：
- 大文件处理（比传统IO快10倍以上）
- 进程间通信（共享内存）
- 数据库文件存储

### 3.2 IO模型对比

| 模型 | 阻塞/非阻塞 | 同步/异步 | Java实现 | 适用场景 |
|------|-----------|----------|---------|---------|
| BIO | 阻塞 | 同步 | InputStream | 连接数少 |
| NIO | 非阻塞 | 同步 | Channel+Selector | 连接数多、数据量小 |
| AIO | 非阻塞 | 异步 | AsynchronousChannel | 连接数多、数据量大 |

### 3.3 Reactor模式

#### 单线程Reactor
```
Reactor线程
├── 接收Accept事件 → 创建Handler
└── 处理Read/Write事件 → 执行业务逻辑
```

#### 多线程Reactor
```
MainReactor线程（Boss）
└── 接收Accept事件 → 创建Handler → 注册到SubReactor

SubReactor线程池（Worker）
└── 处理Read/Write事件 → 执行业务逻辑
```

#### 主从多线程Reactor（Netty默认）
```
Boss Group（1个线程）
└── 接收连接

Worker Group（N个线程）
└── IO读写

Business ThreadPool
└── 业务处理
```

#### 与线程池的链接
```
Reactor的线程模型 ≈ 线程池的调度策略
主从Reactor ≈ ForkJoinPool的工作窃取（任务分发）
```

---

## 实战项目

### 项目1：BIO聊天室（Day 1）
**目标**：理解BIO的阻塞特性
**功能**：多客户端聊天，每连接一线程
**产出**：BIO网络通信基础组件

### 项目2：NIO单线程聊天室（Day 2-3）
**目标**：掌握Selector事件驱动编程
**功能**：单线程处理多客户端连接和消息
**产出**：NIO网络通信框架雏形

### 项目3：NIO文件传输工具（Day 4-5）
**目标**：掌握零拷贝和内存映射
**功能**：断点续传的大文件传输
**产出**：高性能文件传输组件

### 项目4：手写Reactor服务器（Day 6）
**目标**：深入理解Reactor模式
**功能**：支持多线程Reactor模型
**产出**：Reactor框架核心组件

---

## 知识图谱

```
并发编程
├── 线程池 ──────┐
├── 阻塞队列 ────┼──→ NIO Selector/Channel
└── CAS/锁 ──────┘

NIO
├── Buffer ──────┐
├── Channel ─────┼──→ Netty ByteBuf/Channel
└── Selector ────┘

NIO高级
├── 零拷贝 ──────┐
├── 内存映射 ────┼──→ 高性能IO
└── Reactor ─────┘
```

---

## 下一步

完成NIO学习后，进入 [Netty学习路线](../netty/netty-learning-path.md)
