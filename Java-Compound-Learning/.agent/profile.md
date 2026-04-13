# Learner Profile

> 学习者画像 - 帮助AI了解学习者的背景、目标和进度

---

## 基本信息

| 属性 | 值 |
|-----|---|
| 当前水平 | 中级 |
| 学习风格 | 项目驱动型，偏好通过实践理解原理 |
| 时间投入 | 时间不限 |

## 能力矩阵

### 强项领域
- ✅ Java 内存模型（JMM）- happens-before、内存屏障、volatile
- ✅ Java 多线程编程 - Thread、Runnable、线程池、锁机制
- ✅ 并发工具类 - CountDownLatch、CyclicBarrier、Semaphore、CompletableFuture
- ✅ 并发集合 - ConcurrentHashMap、CopyOnWriteArrayList、BlockingQueue
- ✅ 原子类 - AtomicLong、LongAdder、CAS 原理、分段思想
- ✅ 并发底层机制 - futex、park()/unpark()、线程阻塞全链路
- ✅ JVM 与 OS 线程状态映射 - 状态流转、调度机制、中断处理

### 薄弱领域
- ⏳ NIO/New IO - Buffer、Channel、Selector、内存映射
- ⏳ 序列化机制和注解 - Serializable、Externalizable、Protobuf、Kryo
- ⏳ 网络编程 - Socket、ServerSocket、Reactor 模式
- ⏳ Netty 框架 - 事件驱动、Pipeline、ByteBuf

---

## 学习原则（复利规则）

1. **每个知识点必须链接到已有知识**
2. **每个错误必须归类到错误模式库**
3. **每个项目必须产出可复用组件**
4. **每次对话必须沉淀为结构化笔记**

---

## 知识库索引

### 已掌握知识点路径
- 核心概念：`02-Knowledge/concurrency/concepts/`
- 深度文档：`02-Knowledge/concurrency/deep-dives/`
- 错误模式：`03-Practice/mistakes/`
- 项目组件：`01-Projects/*/src/`

### 待学习主题
- NIO专题：`02-Knowledge/nio/` (待创建)

---

## 个性化指令

- 解释原理时，使用我已掌握的概念作类比（如用线程池类比 NIO 的 Selector）
- 生成练习时，优先针对我的错误模式
- 发现知识关联时，主动建议更新知识图谱（如 NIO 与并发编程的关联）
- 长文内容自动提取结构化要点

---

## 历史学习数据

### 已完成项目
- project-concurrency-test - 并发计数器、生产者-消费者模型
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

---

## 知识资产

### 原子概念笔记 (02-Knowledge/concurrency/concepts/)
- futex.md - 用户态CAS+内核态阻塞
- LongAdder.md - 分段累加计数器
- 线程池.md - 线程复用与管理
- 线程阻塞.md - 线程阻塞全链路
- Parker.md - JVM层阻塞封装
- 分段锁思想.md - 分而治之设计模式
- 缓存行伪共享.md - CPU缓存性能问题
- 双重检查模式.md - 防止竞态条件
- 用户态与内核态切换.md - 特权级切换
- 拒绝策略.md - 线程池过载保护

### 深度文档 (02-Knowledge/concurrency/deep-dives/)
- futex-deep-dive.md
- longadder-design.md
- thread-blocking-mechanism.md
- thread-pool-design.md

### 练习记录 (03-Practice/drills/)
- 2026-04-11-concurrent-counters.md

### 对话反思 (03-Practice/reflections/)
- 2026-04-11-futex-dialogue.md

---

## 更新记录

| 日期 | 更新内容 |
|-----|---------|
| 2026-04-13 | 重构目录结构，更新路径索引 |
| 2026-04-11 | 完成并发编程底层机制复习 |
