# Java学习动态路线图

> 🔄 本路线图会根据学习进度动态更新
> 📊 当前状态：基于6年Java后端经验，系统化提升核心技术深度

---

## 学习模块总览

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

## 模块一：基础核心（当前重点）

### 1.1 并发编程 ✅（已掌握，待测试巩固）
**当前状态**：已学习JMM、多线程、并发工具类

**测试项目**：
- [ ] 高并发计数器系统（30分钟）
- [ ] 生产者消费者队列（45分钟）
- [ ] 简易线程池实现（1小时）

**详细学习路线**：[concurrency-learning-path.md](./02-Knowledge-Base/concurrency/concurrency-learning-path.md)

---

### 1.2 NIO与网络编程 🚧（当前学习重点）
**当前状态**：已规划，未开始

**学习阶段**：
1. **BIO基础**（1天）
   - IO流体系与装饰器模式
   - Socket编程与阻塞原理
   - 项目：BIO聊天室

2. **NIO核心三大件**（3天）
   - Buffer缓冲区与状态机
   - Channel通道与零拷贝
   - Selector多路复用
   - 项目：NIO单线程聊天室

3. **NIO高级主题**（2天）
   - 内存映射文件
   - Reactor模式
   - AIO与IO模型对比
   - 项目：NIO文件传输工具

**详细学习路线**：[nio-learning-path.md](./02-Knowledge-Base/nio/nio-learning-path.md)

---

### 1.3 JVM原理与调优 📋（待开始）
**学习阶段**：
1. **JVM内存模型**（2天）
   - 运行时数据区域（堆、栈、方法区）
   - 对象创建与内存分配
   - 垃圾回收算法与收集器

2. **类加载机制**（1天）
   - 类加载过程
   - 双亲委派模型
   - 打破双亲委派

3. **性能调优实战**（2天）
   - JVM参数配置
   - 内存泄漏排查
   - GC日志分析
   - 项目：JVM调优案例分析

**详细学习路线**：[jvm-learning-path.md](./02-Knowledge-Base/jvm/jvm-learning-path.md)

---

### 1.4 设计模式 📋（待开始）
**学习阶段**：
1. **创建型模式**（2天）
   - 单例、工厂、建造者、原型

2. **结构型模式**（3天）
   - 代理、装饰器、适配器、桥接、组合、外观、享元

3. **行为型模式**（3天）
   - 观察者、策略、模板、责任链、状态、命令、迭代器

4. **模式实战**（2天）
   - Spring中的设计模式
   - JDK中的设计模式
   - 项目：设计模式重构实战

**详细学习路线**：[patterns-learning-path.md](./02-Knowledge-Base/patterns/patterns-learning-path.md)

---

## 模块二：框架生态

### 2.1 Spring框架 📋（待开始）
**学习阶段**：
1. **Spring Core**（3天）
   - IoC容器与Bean生命周期
   - AOP原理与实现
   - 事务管理

2. **Spring Boot**（3天）
   - 自动配置原理
   - Starter机制
   - 内嵌容器与启动流程

3. **Spring源码**（4天）
   - BeanFactory与ApplicationContext
   - 循环依赖解决
   - Spring MVC流程

**详细学习路线**：[spring-learning-path.md](./02-Knowledge-Base/spring/spring-learning-path.md)

---

### 2.2 Spring Cloud 📋（待开始）
**学习阶段**：
1. **服务治理**（2天）
   - Eureka/Nacos服务注册发现
   - Ribbon负载均衡
   - Feign声明式调用

2. **服务容错**（2天）
   - Hystrix/Sentinel熔断降级
   - 限流算法与实现

3. **网关与配置**（2天）
   - Gateway路由与过滤
   - Config配置中心

**详细学习路线**：[springcloud-learning-path.md](./02-Knowledge-Base/springcloud/springcloud-learning-path.md)

---

### 2.3 MyBatis 📋（待开始）
**学习阶段**：
1. **基础使用**（1天）
   - SQL映射与动态SQL
   - 缓存机制

2. **源码分析**（2天）
   - 执行流程
   - 插件机制
   - 与Spring整合

**详细学习路线**：[mybatis-learning-path.md](./02-Knowledge-Base/mybatis/mybatis-learning-path.md)

---

## 模块三：数据存储

### 3.1 MySQL 📋（待开始）
**学习阶段**：
1. **索引与优化**（3天）
   - B+树索引原理
   - 执行计划分析
   - SQL优化技巧

2. **事务与锁**（2天）
   - ACID与隔离级别
   - MVCC机制
   - 锁类型与死锁

3. **架构设计**（2天）
   - 主从复制
   - 分库分表
   - 读写分离

**详细学习路线**：[mysql-learning-path.md](./02-Knowledge-Base/mysql/mysql-learning-path.md)

---

### 3.2 Redis 📋（待开始）
**学习阶段**：
1. **数据结构与使用**（2天）
   - 五大数据结构
   - 应用场景设计

2. **持久化与高可用**（2天）
   - RDB与AOF
   - 主从复制
   - Sentinel与Cluster

3. **源码与原理**（2天）
   - 单线程模型
   - IO多路复用
   - 缓存问题（穿透、击穿、雪崩）

**详细学习路线**：[redis-learning-path.md](./02-Knowledge-Base/redis/redis-learning-path.md)

---

### 3.3 Elasticsearch 📋（待开始）
**学习阶段**：
1. **核心概念**（2天）
   - 倒排索引
   - 分词与映射
   - 查询DSL

2. **集群与优化**（2天）
   - 分片与副本
   - 集群架构
   - 性能调优

**详细学习路线**：[es-learning-path.md](./02-Knowledge-Base/elasticsearch/es-learning-path.md)

---

## 模块四：系统架构

### 4.1 消息队列 📋（待开始）
**学习阶段**：
1. **Kafka**（3天）
   - 架构与存储机制
   - 生产者与消费者
   - 分区与副本

2. **RocketMQ**（3天）
   - 架构设计
   - 消息模型
   - 事务消息

3. **RabbitMQ**（2天）
   - 交换机与队列
   - 消息可靠性

**详细学习路线**：[mq-learning-path.md](./02-Knowledge-Base/mq/mq-learning-path.md)

---

### 4.2 容器化与K8s 📋（待开始）
**学习阶段**：
1. **Docker**（2天）
   - 镜像与容器
   - Dockerfile编写
   - 网络与存储

2. **Kubernetes**（4天）
   - Pod/Deployment/Service
   - 调度与资源管理
   - 网络与存储
   - 实战：K8s部署微服务

**详细学习路线**：[k8s-learning-path.md](./02-Knowledge-Base/kubernetes/k8s-learning-path.md)

---

### 4.3 分布式理论 📋（待开始）
**学习阶段**：
1. **分布式基础**（2天）
   - CAP理论
   - BASE理论
   - 一致性协议（Paxos/Raft）

2. **分布式问题**（3天）
   - 分布式事务（2PC/3PC/TCC）
   - 分布式锁
   - 分布式ID

3. **高并发架构**（3天）
   - 限流降级
   - 异步处理
   - 缓存架构

**详细学习路线**：[distributed-learning-path.md](./02-Knowledge-Base/distributed/distributed-learning-path.md)

---

## 知识掌握度追踪

| 模块 | 主题 | 掌握度 | 状态 | 最后更新 |
|------|------|--------|------|----------|
| 基础核心 | Java内存模型 | ⭐⭐⭐⭐⭐ | ✅ | 2026-04-10 |
| 基础核心 | 多线程编程 | ⭐⭐⭐⭐⭐ | ✅ | 2026-04-10 |
| 基础核心 | 并发工具类 | ⭐⭐⭐⭐☆ | ✅ | 2026-04-10 |
| 基础核心 | NIO | ⭐☆☆☆☆ | 🚧 | - |
| 基础核心 | JVM原理 | ⭐⭐☆☆☆ | 📋 | - |
| 基础核心 | 设计模式 | ⭐⭐⭐☆☆ | 📋 | - |
| 框架生态 | Spring | ⭐⭐⭐☆☆ | 📋 | - |
| 框架生态 | Spring Boot | ⭐⭐⭐⭐☆ | 📋 | - |
| 框架生态 | Spring Cloud | ⭐⭐⭐☆☆ | 📋 | - |
| 框架生态 | MyBatis | ⭐⭐⭐⭐☆ | 📋 | - |
| 数据存储 | MySQL | ⭐⭐⭐⭐☆ | 📋 | - |
| 数据存储 | Redis | ⭐⭐⭐☆☆ | 📋 | - |
| 数据存储 | Elasticsearch | ⭐⭐☆☆☆ | 📋 | - |
| 系统架构 | 消息队列 | ⭐⭐⭐☆☆ | 📋 | - |
| 系统架构 | Docker/K8s | ⭐⭐☆☆☆ | 📋 | - |
| 系统架构 | 分布式理论 | ⭐⭐☆☆☆ | 📋 | - |

---

## 当前学习重点

### 🎯 本周目标（2026-04-10 ~ 2026-04-17）
1. 完成并发编程能力测试（3个项目）
2. 开始NIO学习 - BIO基础与聊天室项目

### 📅 近期里程碑
- [ ] 完成并发能力测试项目
- [ ] 完成BIO聊天室
- [ ] 完成NIO单线程聊天室
- [ ] 完成NIO文件传输工具

### 📚 长期里程碑
- [ ] 完成基础核心模块（并发+NIO+JVM+设计模式）
- [ ] 完成框架生态模块（Spring全家桶+MyBatis）
- [ ] 完成数据存储模块（MySQL+Redis+ES）
- [ ] 完成系统架构模块（MQ+K8s+分布式）

---

## 推荐学习资源

### 书籍
- 《Java并发编程实战》（已读）
- 《深入理解Java虚拟机》
- 《Netty实战》
- 《MySQL技术内幕：InnoDB存储引擎》
- 《Redis设计与实现》
- 《Kubernetes权威指南》

### 项目源码
- [netty-study](https://github.com/xiaoxiunique/netty-study)
- [spring-analysis](https://github.com/seaswalker/spring-analysis)
- [JCSprout](https://github.com/crossoverJie/JCSprout)

### 工具
- Arthas（JVM诊断）
- Wireshark（网络抓包）
- JMH（性能测试）

---

*最后更新：2026-04-10 | 下一 Review 日期：2026-04-17*
