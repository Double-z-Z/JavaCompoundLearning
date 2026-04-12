# Java 学习复利工程 - AI 合伙人配置

## 学习者画像
- **当前水平**: 中级
- **学习风格**: 项目驱动型，偏好通过实践理解原理
- **薄弱领域**: NIO、序列化、网络编程底层原理
- **强项领域**: Java 内存模型、多线程编程、并发控制
- **时间投入**: 时间不限

## 已掌握知识点

### 并发编程（系统掌握）
- Java 内存模型（JMM）- happens-before、内存屏障、volatile
- Java 多线程编程 - Thread、Runnable、线程池、锁机制
- 并发工具类 - CountDownLatch、CyclicBarrier、Semaphore、CompletableFuture
- 并发集合 - ConcurrentHashMap、CopyOnWriteArrayList、BlockingQueue
- 原子类 - AtomicLong、LongAdder、CAS 原理、分段思想
- 并发底层机制 - futex、park()/unpark()、线程阻塞全链路
- JVM 与 OS 线程状态映射 - 状态流转、调度机制、中断处理

## 待学习知识点
- NIO/New IO - Buffer、Channel、Selector、内存映射
- 序列化机制和注解 - Serializable、Externalizable、Protobuf、Kryo
- 网络编程 - Socket、ServerSocket、Reactor 模式
- Netty 框架 - 事件驱动、Pipeline、ByteBuf

## 学习原则（复利规则）
1. **每个知识点必须链接到已有知识**
2. **每个错误必须归类到错误模式库**
3. **每个项目必须产出可复用组件**
4. **每次对话必须沉淀为结构化笔记**

## 知识库索引（AI 必须读取）
- 核心概念：`/02-Knowledge-Base/core-java/`
- 并发编程：`/02-Knowledge-Base/concurrency/`
- NIO 专题：`/02-Knowledge-Base/nio/`
- 错误模式：`/03-Practice-Log/mistakes/`
- 项目组件：`/01-Projects/*/src/reusable/`

## 个性化指令
- 解释原理时，使用我已掌握的概念作类比（如用线程池类比 NIO 的 Selector）
- 生成练习时，优先针对我的错误模式
- 发现知识关联时，主动建议更新知识图谱（如 NIO 与并发编程的关联）
- 长文内容自动提取结构化要点

## 历史学习数据（AI 自动更新）

### 已完成项目
- project-concurrency-test - 并发计数器、生产者 - 消费者模型
- 深度复习：并发编程底层机制（2026-04-11）

### 掌握模式
- 分段思想：LongAdder、ConcurrentHashMap 的分段锁
- CAS 优化：用户态优先，减少内核态切换
- 跨层次理解：Java → JVM → OS → Hardware 全链路分析
- 状态管理：JVM 线程状态与 OS 线程状态的映射

### 待强化概念
- NIO 三大组件、零拷贝、Reactor 模式
- 背压（Backpressure）机制
- Netty 事件循环模型

### 知识资产（2026-04-11 创建）
- /02-Knowledge-Base/concurrency/thread-blocking-mechanism.md
- /02-Knowledge-Base/concurrency/longadder-design.md
- /02-Knowledge-Base/concurrency/futex-deep-dive.md
- /02-Knowledge-Base/concurrency/jvm-os-thread-states.md
- /03-Practice-Log/drills/2026-04-11-concurrent-counters.md
- /03-Practice-Log/reflections/2026-04-11-futex-dialogue.md
