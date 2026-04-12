# 并发编程学习总结

> 基于已掌握的Java并发知识，构建系统化的并发编程知识体系

## 核心知识框架

### 1. Java内存模型（JMM）

**核心概念**：
- **主内存**：所有线程共享的内存区域
- **工作内存**：每个线程私有的内存区域
- **happens-before**：操作间的可见性规则
- **内存屏障**：保证内存操作的顺序性

**关键规则**：
- 程序顺序规则：线程内的操作按代码顺序执行
- 监视器锁规则：解锁操作 happens-before 后续的加锁操作
- volatile变量规则：volatile写操作 happens-before 后续的volatile读操作
- 线程启动规则：Thread.start() happens-before 线程内的操作
- 线程终止规则：线程内的操作 happens-before 线程终止检测

**volatile关键字**：
- 可见性：保证变量的修改对所有线程可见
- 有序性：禁止指令重排序
- 不保证原子性：复合操作仍需同步

### 2. 线程基础

**线程创建方式**：
- 继承Thread类
- 实现Runnable接口
- 实现Callable接口（结合Future）

**线程状态**：
- NEW：新建
- RUNNABLE：运行中
- BLOCKED：阻塞
- WAITING：等待
- TIMED_WAITING：超时等待
- TERMINATED：终止

**线程优先级**：
- 1-10级，默认5级
- 仅作为调度器参考，不保证执行顺序

**线程安全**：
- 原子性：操作不可中断
- 可见性：修改对其他线程可见
- 有序性：操作按预期顺序执行

### 3. 锁机制

**synchronized**：
- 内置锁，自动加锁和释放
- 可修饰方法、代码块
- 原理：对象头Mark Word + Monitor

**Lock接口**：
- 显式锁，需手动加锁和释放
- 支持公平锁、非公平锁
- 支持可中断锁、超时锁
- 实现：ReentrantLock、ReadWriteLock

**AQS（AbstractQueuedSynchronizer）**：
- 构建锁和同步器的框架
- 基于双向链表的等待队列
- 支持独占模式和共享模式

**锁优化**：
- 偏向锁：减少无竞争情况下的锁开销
- 轻量级锁：减少线程切换
- 重量级锁：互斥同步
- 自旋锁：减少线程阻塞
- 锁消除：编译器移除不必要的锁
- 锁粗化：合并连续的加锁操作

### 4. 并发工具类

**CountDownLatch**：
- 倒计时门闩
- 等待多个线程完成
- 不可重用

**CyclicBarrier**：
- 循环屏障
- 等待所有线程到达屏障点
- 可重用
- 支持屏障操作

**Semaphore**：
- 信号量
- 控制并发访问的线程数
- 支持公平和非公平模式

**Exchanger**：
- 线程间交换数据
- 适用于生产者-消费者场景

**Phaser**：
- 阶段同步器
- 支持动态注册和分阶段执行

### 5. 原子类

**基本原子类**：
- AtomicBoolean：原子布尔值
- AtomicInteger：原子整数
- AtomicLong：原子长整型

**引用原子类**：
- AtomicReference：原子引用
- AtomicStampedReference：带版本戳的原子引用（解决ABA问题）
- AtomicMarkableReference：带标记的原子引用

**数组原子类**：
- AtomicIntegerArray：原子整数数组
- AtomicLongArray：原子长整型数组
- AtomicReferenceArray：原子引用数组

**字段原子类**：
- AtomicIntegerFieldUpdater：原子更新整型字段
- AtomicLongFieldUpdater：原子更新长整型字段
- AtomicReferenceFieldUpdater：原子更新引用字段

**累加器**：
- LongAdder：高并发计数器（分段累加）
- LongAccumulator：自定义累加器
- DoubleAdder：原子双精度浮点数
- DoubleAccumulator：自定义双精度浮点数累加器

### 6. 并发集合

**阻塞队列**：
- ArrayBlockingQueue：有界数组阻塞队列
- LinkedBlockingQueue：可选有界链表阻塞队列
- PriorityBlockingQueue：优先级阻塞队列
- DelayQueue：延迟阻塞队列
- SynchronousQueue：同步队列（无缓冲）
- LinkedTransferQueue：链表传输队列
- LinkedBlockingDeque：链表双向阻塞队列

**非阻塞队列**：
- ConcurrentLinkedQueue：非阻塞链表队列
- ConcurrentLinkedDeque：非阻塞双向链表队列

**并发映射**：
- ConcurrentHashMap：并发哈希表
- ConcurrentSkipListMap：并发跳表映射

**其他并发集合**：
- CopyOnWriteArrayList：写时复制数组列表
- CopyOnWriteArraySet：写时复制集合
- ConcurrentSkipListSet：并发跳表集合

### 7. 线程池

**核心参数**：
- corePoolSize：核心线程数
- maximumPoolSize：最大线程数
- keepAliveTime：线程存活时间
- workQueue：工作队列
- threadFactory：线程工厂
- handler：拒绝策略

**执行流程**：
1. 核心线程数未满，创建核心线程
2. 核心线程满，尝试入队
3. 队列满，创建非核心线程
4. 线程数达到上限，执行拒绝策略

**内置线程池**：
- Executors.newFixedThreadPool：固定大小线程池
- Executors.newCachedThreadPool：可缓存线程池
- Executors.newSingleThreadExecutor：单线程线程池
- Executors.newScheduledThreadPool：定时线程池

**自定义线程池**：
- ThreadPoolExecutor：灵活配置的线程池
- ForkJoinPool：工作窃取线程池

**拒绝策略**：
- AbortPolicy：抛出异常
- CallerRunsPolicy：调用者执行
- DiscardPolicy：静默丢弃
- DiscardOldestPolicy：丢弃最旧任务

### 8. CAS原理与实践

**CAS（Compare-And-Swap）**：
- 基本思想：比较并交换
- 底层实现：CPU指令（cmpxchg）
- 优势：无锁操作，减少线程切换
- 劣势：ABA问题、自旋开销

**ABA问题解决方案**：
- AtomicStampedReference：带版本戳
- AtomicMarkableReference：带标记

**高性能原子操作库**：
- JCTools：高性能并发数据结构
- Disruptor：高性能环形缓冲区

### 9. CompletableFuture

**核心功能**：
- 异步任务执行
- 任务编排（thenApply、thenAccept、thenRun）
- 任务组合（thenCompose、thenCombine、allOf、anyOf）
- 异常处理（exceptionally、handle）

**使用场景**：
- 异步非阻塞操作
- 并行任务执行
- 复杂的异步工作流

## 设计模式

### 1. 生产者-消费者模式

**核心组件**：
- 生产者：生成数据
- 消费者：处理数据
- 缓冲区：存储数据

**实现方式**：
- BlockingQueue
- 自定义队列 + wait/notify
- Disruptor

### 2. 读写锁模式

**核心思想**：
- 读共享，写独占
- 提高并发读性能

**实现**：
- ReentrantReadWriteLock
- StampedLock（Java 8+）

### 3. 线程本地存储模式

**核心思想**：
- 每个线程拥有独立的变量副本
- 避免线程安全问题
- 减少参数传递

**实现**：
- ThreadLocal
- InheritableThreadLocal

### 4. 两阶段终止模式

**核心思想**：
- 优雅终止线程
- 避免线程被强制中断

**实现步骤**：
1. 设置终止标志
2. 中断线程
3. 处理中断逻辑

## 性能优化

### 1. 减少锁竞争

**策略**：
- 减小锁粒度（如ConcurrentHashMap的分段锁）
- 锁分离（如读写锁）
- 无锁编程（CAS）
- 线程本地存储

### 2. 线程池调优

**CPU密集型任务**：
- 线程数 ≈ CPU核心数 + 1

**IO密集型任务**：
- 线程数 ≈ CPU核心数 × 2
- 或根据IO等待时间调整

**队列选择**：
- 无界队列：任务量稳定
- 有界队列：流量波动大

### 3. 内存优化

**减少伪共享**：
- @Contended注解
- 内存填充

**减少GC压力**：
- 对象池
- 避免频繁创建临时对象
- 使用合适的集合类型

### 4. 并发工具选择

**计数器**：
- 低并发：AtomicLong
- 高并发：LongAdder

**队列**：
- 阻塞队列：ArrayBlockingQueue、LinkedBlockingQueue
- 非阻塞队列：ConcurrentLinkedQueue
- 高性能队列：JCTools

**事件处理**：
- 低延迟：Disruptor

## 常见问题与解决方案

### 1. 死锁

**原因**：
- 循环等待
- 不可抢占
- 互斥资源
- 持有并等待

**解决方案**：
- 避免循环依赖
- 使用超时锁
- 统一锁顺序
- 使用Lock接口替代synchronized

### 2. 活锁

**原因**：
- 线程间相互谦让
- 没有进展

**解决方案**：
- 随机等待时间
- 优先级机制

### 3. 饥饿

**原因**：
- 低优先级线程长期得不到执行
- 高优先级线程持续占用资源

**解决方案**：
- 使用公平锁
- 合理设置线程优先级
- 避免长时间持有锁

### 4. 内存可见性问题

**原因**：
- 缓存一致性
- 指令重排序

**解决方案**：
- 使用volatile
- 使用synchronized
- 使用原子类

## 实战项目

### 1. 高并发计数器系统

**实现方案**：
- SynchronizedCounter：synchronized实现
- AtomicCounter：AtomicLong实现
- StripedCounter：分段计数器

**性能对比**：
- AtomicCounter > StripedCounter > SynchronizedCounter

### 2. 生产者-消费者队列

**核心功能**：
- 任务调度
- 队列管理
- 优雅关闭
- 监控指标

**实现**：
- BlockingQueue
- 线程池
- 监控统计

### 3. 简易线程池实现

**核心功能**：
- 线程管理
- 任务队列
- 拒绝策略
- 线程回收

**实现**：
- 工作线程
- 任务队列
- 状态管理

## 学习资源

### 书籍
- 《Java并发编程实战》
- 《Java并发编程艺术》
- 《深入理解Java虚拟机》

### 在线资源
- Java并发编程指南
- JDK源码分析
- 并发编程实战博客

### 工具
- JMH：性能测试
- Arthas：JVM诊断
- VisualVM：内存分析

## 总结

并发编程是Java后端开发的核心技能之一，掌握并发编程不仅能解决高并发场景下的性能问题，还能提高系统的稳定性和可靠性。通过系统化学习Java内存模型、锁机制、并发工具类、线程池等核心知识，并结合实战项目进行巩固，可以构建起完整的并发编程知识体系。

在实际应用中，应根据具体场景选择合适的并发工具和方案，注重性能优化和代码可读性，同时关注线程安全问题，避免常见的并发陷阱。