# Java 后端技术知识图谱

## 今日学习

### 2026-04-11
- **并发编程** ✅
  - 线程池：核心参数、执行流程、拒绝策略、性能调优
  - CAS 原理与实践：CAS 基本原理、原子类、高性能 CAS 库
  - LongAdder 深度理解：分段思想、Cell 设计、CAS 冲突优化
  - 线程阻塞全链路：Java→JVM→OS→Hardware 调用层次
  - futex 机制：用户态 CAS + 内核态阻塞、双重检查模式
  - JVM 与 OS 线程状态映射：双层状态管理、状态流转
  - 中断处理机制：硬件中断、状态变更 vs 资源分配
- **NIO 与网络编程** 🔄
  - BIO 基础：IO 流体系、Socket 编程、阻塞 IO 原理
  - NIO 核心：Buffer、Channel、Selector
- **知识管理** 📚
  - 复利工程：知识关联、实践验证、错误学习、定期复习
  - 知识图谱：结构设计、应用场景、学习路径

## 基础核心模块

### 并发编程

#### 理论基础
- Java 内存模型（JMM） ✅
  - happens-before 原则
  - 内存屏障
  - volatile 关键字

#### 核心机制
- 线程基础 ✅
  - Thread、Runnable、Callable
  - 线程生命周期
- 锁机制 ✅
  - synchronized
  - ReentrantLock
  - 读写锁
- CAS 原理 ✅
  - 基本原理：比较并交换
  - 底层实现：CPU 指令（cmpxchg）
  - ABA 问题

#### 原子类与高性能计数
- 原子类 ✅
  - Atomic*系列（AtomicInteger、AtomicLong）
  - AtomicReference
  - 原子数组
- LongAdder 深度理解 ✅
  - 分段思想：base + Cell 数组
  - Cell 设计：@Contended 避免缓存行伪共享
  - CAS 冲突优化：减少冲突概率而非消除 CAS
  - 哈希计算：getProbe() & (n-1)

#### 并发工具类
- 同步工具 ✅
  - CountDownLatch
  - CyclicBarrier
  - Semaphore
- 线程池 ✅
  - 核心参数：corePoolSize、maximumPoolSize、keepAliveTime、workQueue、threadFactory、handler
  - 执行流程：核心线程数未满 → 入队 → 创建非核心线程 → 执行拒绝策略
  - 拒绝策略：AbortPolicy、CallerRunsPolicy、DiscardPolicy、DiscardOldestPolicy
  - 性能调优：CPU 密集型（线程数≈CPU 核心数 +1）、IO 密集型（线程数≈CPU 核心数×2）
- CompletableFuture 🔄

#### 并发集合
- 并发容器 ✅
  - ConcurrentHashMap
  - CopyOnWriteArrayList
  - BlockingQueue

#### 底层机制
- 线程阻塞机制 ✅
  - 调用层次：Java→JDK→JVM→glibc→内核→硬件
  - 关键切换点：futex 系统调用（唯一用户态→内核态切换）
  - park()/unpark()：用户态检查_counter 优化
- futex 机制 ✅
  - 本质：Fast User-space mutex
  - 双重检查模式：用户态 CAS + 内核态阻塞
  - 保护对象：内核等待队列（非 CPU 调度）
  - 性能：无竞争~20 纳秒，有竞争~1-5 微秒
- JVM 与 OS 线程状态 ✅
  - 双层状态管理：Java 层（逻辑）vs OS 层（物理）
  - 状态映射：RUNNABLE→Running/Runnable、WAITING→Sleeping
  - 状态流转：BLOCKED、WAITING、TIMED_WAITING
- 中断处理机制 ✅
  - 硬件中断：APIC、CPU 亲和性
  - 中断流程：暂停→保存→执行→唤醒→返回→调度
  - 关键理解：中断无需"腾出 CPU"，直接改变状态

### NIO 与网络编程
- BIO 基础 🔄
  - IO 流体系：字节流 vs 字符流，装饰器模式
  - Socket 编程：ServerSocket，Socket，一线程一连接
- NIO 核心 🔄
  - Buffer
    - 核心属性：capacity、position、limit
    - 操作方法：put、get、flip、clear
  - Channel
    - 双向通信
    - 非阻塞模式
  - Selector
    - 多路复用
    - 事件驱动
- NIO 高级 ⏳
  - 零拷贝
  - 内存映射
- Netty 框架 ⏳

### JVM 原理
- 内存模型 🔄
- 类加载机制 ⏳
- 字节码执行 ⏳
- 性能调优 ⏳

### 设计模式
- 创建型模式 🔄
- 结构型模式 ⏳
- 行为型模式 ⏳

## 框架生态模块

### Spring 框架
- Spring Core 🔄
- Spring Boot 🔄
- Spring MVC ⏳
- Spring Security ⏳

### Spring Cloud
- 服务治理 ⏳
- 服务容错 ⏳
- 网关 ⏳
- 配置中心 ⏳

### MyBatis
- 核心组件 🔄
- 映射文件 🔄
- 缓存机制 ⏳

## 数据存储模块

### MySQL
- 存储引擎 🔄
- 索引 🔄
- 事务 ⏳
- 高可用 ⏳

### Redis
- 数据结构 🔄
- 持久化 ⏳
- 高可用 ⏳

### Elasticsearch
- 核心概念 ⏳
- 搜索 ⏳
- 集群管理 ⏳

## 系统架构模块

### 消息队列
- Kafka ⏳
- RocketMQ ⏳
- RabbitMQ ⏳

### 容器化与 K8s
- Docker ⏳
- Kubernetes ⏳

### 分布式系统
- 分布式理论 ⏳
- 分布式事务 ⏳
- 分布式锁 ⏳
- 高并发架构 ⏳

## 学习路径

### 基础阶段
1. Java 核心语法 ✅
2. 并发编程基础 ✅
3. 并发编程底层机制 ✅
4. JVM 原理 🔄
5. 设计模式 🔄

### 进阶阶段
1. NIO 与网络编程 🔄
2. Spring 框架 🔄
3. 数据库（MySQL）🔄
4. 缓存（Redis）🔄

### 高级阶段
1. 微服务架构 ⏳
2. 消息队列 ⏳
3. 容器化 ⏳
4. 分布式系统 ⏳

## 知识复利增长策略

### 策略
- 知识点关联：每个新知识点都要与已有知识建立关联
- 实践验证：每个知识点都要通过实践验证
- 错误学习：记录错误模式和解决方案
- 定期复习：按照遗忘曲线定期复习

## 总结

Java 后端技术体系是一个相互关联、不断发展的知识网络。

**图例说明：**
- ✅ 已掌握
- 🔄 学习中
- ⏳ 待探索