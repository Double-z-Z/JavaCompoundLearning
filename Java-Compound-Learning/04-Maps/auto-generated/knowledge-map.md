# Java 后端技术知识图谱

## 今日学习

### 2026-04-11
- **并发编程** ✅
  - 线程池：核心参数、执行流程、拒绝策略、性能调优
  - CAS 原理与实践：CAS 基本原理、原子类、高性能 CAS 库
- **NIO 与网络编程** 🔄
  - BIO 基础：IO 流体系、Socket 编程、阻塞 IO 原理
  - NIO 核心：Buffer、Channel、Selector
- **知识管理** 📚
  - 复利工程：知识关联、实践验证、错误学习、定期复习
  - 知识图谱：结构设计、应用场景、学习路径

## 基础核心模块

### 并发编程
- Java 内存模型（JMM） ✅
- 线程基础 ✅
- 锁机制 ✅
- 并发工具类 ✅
- 原子类 ✅
- 并发集合 ✅
- 线程池 ✅
  - 核心参数：corePoolSize、maximumPoolSize、keepAliveTime、workQueue、threadFactory、handler
  - 执行流程：核心线程数未满 → 入队 → 创建非核心线程 → 执行拒绝策略
  - 拒绝策略：AbortPolicy、CallerRunsPolicy、DiscardPolicy、DiscardOldestPolicy
  - 性能调优：CPU 密集型（线程数≈CPU 核心数 +1）、IO 密集型（线程数≈CPU 核心数×2）
- CAS 原理与实践 ✅
  - CAS 基本原理：比较并交换
  - 底层实现：CPU 指令（cmpxchg）
  - 原子类：Atomic*系列、LongAdder、LongAccumulator
  - 高性能 CAS 库：JCTools、Disruptor
- CompletableFuture 🔄

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
3. JVM 原理 🔄
4. 设计模式 🔄

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