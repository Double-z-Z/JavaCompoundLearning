# Java学习路线图

> 本文件是**静态技术百科全书**，定义"有什么可学"
> 
> 🔄 动态规划（"下一步学什么"）见 `.agent/profile.md`
> 📊 能力评估见 `.agent/assessment/current.json`

---

## 技术体系总览

```
┌─────────────────────────────────────────────────────────────┐
│                      Java后端技术体系                        │
├─────────────┬─────────────┬─────────────┬─────────────────────┤
│  基础核心    │   框架生态   │   数据存储   │     系统架构        │
├─────────────┼─────────────┼─────────────┼─────────────────────┤
│ • 并发编程   │ • Spring    │ • MySQL     │ • 微服务架构        │
│ • NIO网络   │ • SpringBoot│ • Redis     │ • 消息队列          │
│ • JVM原理   │ • SpringCloud│ • ES       │ • 容器化/K8s       │
│ • 设计模式   │ • MyBatis   │             │ • 分布式理论        │
└─────────────┴─────────────┴─────────────┴─────────────────────┘
```

---

## 模块一：基础核心

### 1.1 并发编程

**核心概念**：
- Java内存模型（JMM）- happens-before、内存屏障、volatile
- 多线程编程 - Thread、Runnable、线程池、锁机制
- 并发工具类 - CountDownLatch、CyclicBarrier、Semaphore、CompletableFuture
- 并发集合 - ConcurrentHashMap、CopyOnWriteArrayList、BlockingQueue
- 原子类 - AtomicLong、LongAdder、CAS原理、分段思想
- 并发底层机制 - futex、park()/unpark()、线程阻塞全链路

**项目实践**：
- 高并发计数器系统
- 生产者消费者队列
- 简易线程池实现

**知识库位置**：`02-Knowledge/concurrency/`

---

### 1.2 NIO与网络编程

**学习阶段**：
1. **BIO基础**
   - IO流体系与装饰器模式
   - Socket编程与阻塞原理

2. **NIO核心三大件**
   - Buffer缓冲区与状态机
   - Channel通道与零拷贝
   - Selector多路复用

3. **NIO高级主题**
   - 内存映射文件
   - Reactor模式
   - AIO与IO模型对比

**项目实践**：
- BIO聊天室
- NIO单线程聊天室
- NIO文件传输工具

**进阶主题**：
- 服务启停设计模式（状态管理、优雅关闭、ShutdownHook）
- 生产级服务生命周期管理

**知识库位置**：`02-Knowledge/nio/`

---

### 1.3 JVM原理与调优

**学习阶段**：
1. **JVM内存模型**
   - 运行时数据区域（堆、栈、方法区）
   - 对象创建与内存分配
   - 垃圾回收算法与收集器

2. **类加载机制**
   - 类加载过程
   - 双亲委派模型
   - 打破双亲委派

3. **性能调优实战**
   - JVM参数配置
   - 内存泄漏排查
   - GC日志分析

**知识库位置**：`02-Knowledge/jvm/`（待创建）

---

### 1.4 设计模式

**学习阶段**：
1. **创建型模式** - 单例、工厂、建造者、原型
2. **结构型模式** - 代理、装饰器、适配器、桥接、组合、外观、享元
3. **行为型模式** - 观察者、策略、模板、责任链、状态、命令、迭代器
4. **模式实战** - Spring中的设计模式、JDK中的设计模式

**知识库位置**：`02-Knowledge/patterns/`（待创建）

---

## 模块二：框架生态

### 2.1 Spring框架

**学习阶段**：
1. **Spring Core** - IoC容器、Bean生命周期、AOP、事务管理
2. **Spring Boot** - 自动配置、Starter机制、内嵌容器
   - 生产级特性：Actuator健康检查、优雅关闭机制
3. **Spring源码** - BeanFactory、循环依赖、Spring MVC流程

**知识库位置**：`02-Knowledge/spring/`（待创建）

---

### 2.2 Spring Cloud

**学习阶段**：
1. **服务治理** - Eureka/Nacos、Ribbon、Feign
2. **服务容错** - Hystrix/Sentinel、限流算法
3. **网关与配置** - Gateway、Config配置中心

**知识库位置**：`02-Knowledge/springcloud/`（待创建）

---

### 2.3 MyBatis

**学习阶段**：
1. **基础使用** - SQL映射、动态SQL、缓存机制
2. **源码分析** - 执行流程、插件机制、与Spring整合

**知识库位置**：`02-Knowledge/mybatis/`（待创建）

---

## 模块三：数据存储

### 3.1 MySQL

**学习阶段**：
1. **索引与优化** - B+树索引、执行计划、SQL优化
2. **事务与锁** - ACID、MVCC、锁类型与死锁
3. **架构设计** - 主从复制、分库分表、读写分离

**知识库位置**：`02-Knowledge/mysql/`（待创建）

---

### 3.2 Redis

**学习阶段**：
1. **数据结构与使用** - 五大数据结构、应用场景
2. **持久化与高可用** - RDB/AOF、主从复制、Sentinel/Cluster
3. **源码与原理** - 单线程模型、IO多路复用、缓存问题

**知识库位置**：`02-Knowledge/redis/`（待创建）

---

### 3.3 Elasticsearch

**学习阶段**：
1. **核心概念** - 倒排索引、分词与映射、查询DSL
2. **集群与优化** - 分片与副本、集群架构、性能调优

**知识库位置**：`02-Knowledge/elasticsearch/`（待创建）

---

## 模块四：系统架构

### 4.1 消息队列

**学习阶段**：
1. **Kafka** - 架构与存储、生产者与消费者、分区与副本
2. **RocketMQ** - 架构设计、消息模型、事务消息
3. **RabbitMQ** - 交换机与队列、消息可靠性

**知识库位置**：`02-Knowledge/mq/`（待创建）

---

### 4.2 容器化与K8s

**学习阶段**：
1. **Docker** - 镜像与容器、Dockerfile、网络与存储
2. **Kubernetes** - Pod/Deployment/Service、调度与资源管理
   - 服务生命周期：就绪探针(Readiness)、存活探针(Liveness)
   - 优雅关闭：preStop钩子、terminationGracePeriodSeconds
   - 滚动更新策略与零停机部署

**知识库位置**：`02-Knowledge/kubernetes/`（待创建）

---

### 4.3 分布式理论

**学习阶段**：
1. **分布式基础** - CAP/BASE、一致性协议（Paxos/Raft）
2. **分布式问题** - 分布式事务、分布式锁、分布式ID
3. **高并发架构** - 限流降级、异步处理、缓存架构

**知识库位置**：`02-Knowledge/distributed/`（待创建）

---

## 学习资源

### 书籍
- 《Java并发编程实战》
- 《深入理解Java虚拟机》
- 《Netty实战》
- 《MySQL技术内幕：InnoDB存储引擎》
- 《Redis设计与实现》
- 《Kubernetes权威指南》

### 工具
- Arthas（JVM诊断）
- Wireshark（网络抓包）
- JMH（性能测试）

---

## 关联文件

- **动态规划**：`.agent/profile.md` - 当前学习计划和下一步行动
- **能力评估**：`.agent/assessment/current.json` - 五维能力得分
- **执行计划**：`EXECUTION-PLAN.md` - 阶段目标与评估机制
- **主题地图**：`04-Maps/MOC-*.md` - 各主题学习进度可视化

---

*本文件最后更新：2026-04-14*
*动态规划请查看：`.agent/profile.md`*
