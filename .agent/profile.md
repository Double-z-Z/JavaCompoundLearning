# Learner Profile

> 学习者画像 - 帮助AI了解学习者的背景、目标和进度
> 
> 🎯 **本文件是动态学习规划的唯一可信来源**
> 📚 技术路线参考：`LEARNING-ROADMAP.md`
> 📊 能力评估数据：`.agent/assessment/current.json`

***

## 基本信息

| 属性   | 值                |
| ---- | ---------------- |
| 当前水平 | 中级 (L2)          |
| 目标水平 | L3（精通级）        |
| 学习风格 | 项目驱动型，偏好通过实践理解原理 |
| 时间投入 | 时间不限             |

***

## 当前学习计划

> 由AI基于能力评估数据实时推荐，用户确认后记录于此
> 每次对话开始前，AI读取此章节了解当前规划

### 本次对话（2026-04-17）
- **状态**：已完成 ✅
- **用户选择**：选项1 - 继续NIO专题 - 学习NIO三大组件
- **实际完成**：
  - ✅ 深入理解NIO Buffer核心概念（capacity/position/limit/mark，flip/clear/rewind）
  - ✅ 理解NIO Channel（阻塞/非阻塞，双向读写）
  - ✅ 深入理解NIO Selector（epoll机制，红黑树+就绪链表，多路复用原理）
  - ✅ 探讨NIO多线程架构（主从Reactor，Channel绑定，线程模型）
  - ✅ 产出4个知识资产（3个原子笔记 + 1个反思记录）
- **关联知识点**：[[NIO-Buffer]]、[[NIO-Channel]]、[[NIO-Selector]]、[[BIO-BlockingIO]]、[[线程池]]、[[分段锁思想]]
- **掌握度提升**：
  - NIO-Buffer: 0 → 55（🌿理解）
  - NIO-Channel: 0 → 50（🌿理解）
  - NIO-Selector: 0 → 60（🍎应用）
  - NIO/网络编程综合: 65分 → 75分

### 本次对话（2026-04-15）
- **状态**：已完成 ✅
- **用户选择**：选项2 - 开始NIO学习 - BIO基础与聊天室项目
- **实际完成**：
  - ✅ BIO聊天室项目完整实现
  - ✅ 理解BIO阻塞模型及其局限性
  - ✅ 产出3个知识资产（练习记录、原子笔记、反思记录）
- **关联知识点**：[[BIO模型]]、[[Socket编程]]、[[线程池]]、[[并发集合]]
- **掌握度提升**：NIO/网络编程 50分 → 65分

### 历史决策记录
| 日期 | 选择 | 实际完成 | 备注 |
|------|------|---------|------|
| 2026-04-15 | 开始NIO学习 - BIO聊天室 | ✅ | 完成BIO聊天室，产出原子笔记 |
| 2026-04-11 | 复习并发编程底层机制 | ✅ | futex、LongAdder、线程池 |
| 2026-04-10 | 学习LongAdder | ✅ | 产出原子笔记 |

### 历史决策记录
| 日期 | 选择 | 实际完成 | 备注 |
|------|------|---------|------|
| 2026-04-11 | 复习并发编程底层机制 | ✅ | futex、LongAdder、线程池 |
| 2026-04-10 | 学习LongAdder | ✅ | 产出原子笔记 |

***

## JEMS 成熟度评估

> 基于《高级Java后端开发人员能力评价体系》
> 评估档案：`.agent/assessment/current.json`

### 五维能力概览

| 维度 | 当前等级 | 得分 | 趋势 | 目标 |
|------|---------|------|------|------|
| 核心技术知识深度 | L2 | 65/100 | ↗️ | L3 |
| 问题分析与解决 | L2 | 55/100 | → | L2.5 |
| 架构设计与权衡 | L1 | 45/100 | ↗️ | L2.5 |
| 工程素养与实践 | L1 | 35/100 | → | L2 |
| 持续学习能力 | L2 | 60/100 | ↗️ | L3 |
| **综合** | **L2** | **52/100** | **↗️** | **L3** |

### 强项子维度（L3）

- ✅ **并发编程** - 80分，高置信度
  - 证据：futex、LongAdder、线程池等10+原子笔记
  - 路径：`02-Knowledge/concurrency/concepts/`

### 薄弱子维度（L1-L2）- 优先学习

| 主题 | 得分 | 优先级 | 前置知识 | 推荐项目 |
|------|------|--------|---------|---------|
| NIO/网络编程 | 65分 | 🔴 高 | 并发编程 | BIO聊天室 ✅ → NIO聊天室 |
| JVM原理 | 40分 | 🟡 中 | 并发编程 | JVM调优案例分析 |
| 分布式系统 | 30分 | 🟢 低 | NIO、JVM | 分布式缓存设计 |
| 工程素养 | 35分 | 🟡 中 | - | Docker容器化项目 |

***

## 能力矩阵

### 强项领域（已掌握）

- ✅ Java 内存模型（JMM）- happens-before、内存屏障、volatile
- ✅ Java 多线程编程 - Thread、Runnable、线程池、锁机制
- ✅ 并发工具类 - CountDownLatch、CyclicBarrier、Semaphore、CompletableFuture
- ✅ 并发集合 - ConcurrentHashMap、CopyOnWriteArrayList、BlockingQueue
- ✅ 原子类 - AtomicLong、LongAdder、CAS 原理、分段思想
- ✅ 并发底层机制 - futex、park()/unpark()、线程阻塞全链路
- ✅ JVM 与 OS 线程状态映射 - 状态流转、调度机制、中断处理

### 薄弱领域（待学习）

- 🌿 BIO模型 - 阻塞IO、Socket编程、多线程处理（mastery=40，已完成基础项目）
- ⏳ NIO/New IO - Buffer、Channel、Selector、内存映射
- ⏳ Netty 框架 - 事件驱动、Pipeline、ByteBuf
- ⏳ JVM 内存模型与调优
- ⏳ 序列化机制 - Serializable、Protobuf、Kryo

### 已掌握领域（更新）

- ✅ Java 内存模型（JMM）- happens-before、内存屏障、volatile
- ✅ Java 多线程编程 - Thread、Runnable、线程池、锁机制
- ✅ 并发工具类 - CountDownLatch、CyclicBarrier、Semaphore、CompletableFuture
- ✅ 并发集合 - ConcurrentHashMap、CopyOnWriteArrayList、BlockingQueue
- ✅ 原子类 - AtomicLong、LongAdder、CAS 原理、分段思想
- ✅ 并发底层机制 - futex、park()/unpark()、线程阻塞全链路
- ✅ JVM 与 OS 线程状态映射 - 状态流转、调度机制、中断处理
- 🌿 **BIO/网络编程** - ServerSocket、Socket、阻塞IO模型、多客户端处理（NEW）

***

## 学习原则（复利规则）

1. **每个知识点必须链接到已有知识**
2. **每个错误必须归类到错误模式库**
3. **每个项目必须产出可复用组件**
4. **每次对话必须沉淀为结构化笔记**

***

## 个性化指令

- 解释原理时，使用我已掌握的概念作类比（如用线程池类比 NIO 的 Selector）
- 生成练习时，优先针对我的错误模式
- 发现知识关联时，主动建议更新知识图谱（如 NIO 与并发编程的关联）
- 长文内容自动提取结构化要点

***

## 知识库索引

### 已掌握知识点路径

- 核心概念：`02-Knowledge/concurrency/concepts/`
- 深度文档：`02-Knowledge/concurrency/deep-dives/`
- 错误模式：`03-Practice/mistakes/`
- 项目组件：`01-Projects/*/src/`

### 待学习主题（按优先级排序）

1. **NIO专题**（🔴 高优先级）
   - 目标：掌握Buffer、Channel、Selector
   - 项目：BIO聊天室 → NIO聊天室 → NIO文件传输
   - 知识库：`02-Knowledge/nio/`（待创建）

2. **JVM专题**（🟡 中优先级）
   - 目标：理解内存模型、GC机制
   - 项目：JVM调优案例分析
   - 知识库：`02-Knowledge/jvm/`（待创建）

3. **设计模式**（🟡 中优先级）
   - 目标：掌握常用模式，能识别框架中的应用
   - 项目：重构实战
   - 知识库：`02-Knowledge/patterns/`（待创建）

***

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

***

## 更新记录

| 日期         | 更新内容          |
| ---------- | ------------- |
| 2026-04-14 | 重构规划体系，明确本文件为动态规划唯一来源 |
| 2026-04-14 | 建立JEMS评估体系，生成基线评估 |
| 2026-04-13 | 重构目录结构，更新路径索引 |
| 2026-04-11 | 完成并发编程底层机制复习 |
