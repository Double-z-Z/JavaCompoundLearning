---
type: emrg
id: EMRG-并发编程
title: 并发编程网络
maturity: theoretical
created: 2026-04-12
updated: 2026-05-16
related_goals:
  - GOAL-Java核心深化
subtopics:
  - "线程同步机制"
  - "高性能并发设计"
  - "线程池与资源管理"
---

# EMRG-并发编程

> 成熟度: 🟡 theoretical

## 一句话定义
Java并发编程是从底层硬件缓存一致性到高层线程池调度的**分层知识体系**，核心矛盾是"共享资源的正确访问"与"极致性能"之间的平衡。

## 知识拓扑

[线程同步机制]
  ├─ [[futex]] → 用户态CAS+内核态阻塞的混合锁
  │   └─ 关联 [[用户态与内核态切换]]、[[双重检查模式]]
  ├─ [[Parker]] → JVM层阻塞/唤醒的C++封装
  │   └─ 关联 [[线程阻塞]]、[[futex]]
  ├─ [[线程阻塞]] → 线程让出CPU进入等待状态
  │   └─ 关联 [[futex]]、[[Parker]]
  ├─ [[双重检查模式]] → 用户态和内核态各做一次检查
  │   └─ 关联 [[futex]]、CAS
  └─ [[用户态与内核态切换]] → CPU特权级切换过程
      └─ 关联 [[futex]]、[[线程阻塞]]

[高性能并发设计]
  ├─ [[LongAdder]] → 分段累加的高性能计数器
  │   └─ 关联 [[分段锁思想]]、[[缓存行伪共享]]
  ├─ [[分段锁思想]] → 分而治之降低竞争的设计模式
  │   └─ 关联 [[LongAdder]]、ConcurrentHashMap
  └─ [[缓存行伪共享]] → 多核CPU缓存失效导致的性能问题
      └─ 关联 [[LongAdder]]、MESI协议

[线程池与资源管理]
  ├─ [[线程池]] → 线程复用与管理机制
  │   └─ 关联 [[拒绝策略]]、阻塞队列
  └─ [[拒绝策略]] → 线程池过载时的任务处理策略
      └─ 关联 [[线程池]]、熔断器模式

## 关键缺口（待补充）
- [ ] CAS原子笔记（AtomicLong/AtomicReference底层）
- [ ] 线程状态转换完整图谱
- [ ] ObjectMonitor与synchronized深度解析
- [ ] CompletableFuture异步编排
- [ ] AQS框架核心设计
- [ ] ReentrantLock与Condition
- [ ] 读写锁StampedLock
- [ ] 并发容器（ConcurrentHashMap/CopyOnWriteArrayList）

## 项目实战
| 项目 | 状态 | 关联笔记 |
|------|------|---------|
| [[project-并发-test]] | ✅ 完成 | [[futex]], [[LongAdder]], [[线程池]] |

## 关联领域
- [[EMRG-NIO网络编程]] — 线程池风格接口设计、CompletableFuture异步编程
- [[EMRG-Cache]] — Ring-Buffer无锁队列、缓存一致性
- [[EMRG-Redis]] — 秒杀超卖与库存一致性、Pipeline批量操作

---

## 🤖 AI 工作区（以下由 Dataview 自动维护，请勿手动编辑）

### 核心成员
```dataviewjs
const emrgId = dv.current().id;
dv.table(
  ["笔记", "mastery", "验证状态"],
  dv.pages()
    .where(p => p.related_emrg && p.related_emrg.includes(emrgId))
    .sort(p => p.mastery, 'desc')
    .map(p => [
      p.file.link,
      p.mastery ?? ' ',
      (p.mastery >= 60) ? '🟢' : '🟡'
    ])
);
```

### 边界声明

#### 边缘关联（链接但不纳入）
- [[CompletableFuture]] → 归属 [[EMRG-NIO网络编程]] / [[EMRG-Reactive响应式编程]]
- [[线程池风格接口设计]] → 归属 [[EMRG-NIO网络编程]]

#### 跨界枢纽（被多个 EMRG 引用）
- [[分段锁思想]] — 同时被 [[EMRG-并发编程]] 和 [[EMRG-Cache]] 引用（W-TinyLFU的Striped Buffer）
- [[缓存行伪共享]] — 同时被 [[EMRG-并发编程]] 和 [[EMRG-Cache]] 引用（Caffeine的Ring Buffer）

### 涌现历史
- 2026-04-12: 因密度溢出创建（涉及 10+ 篇并发笔记，8 条双向链接）

### 成熟度说明
当前以理论学习为主，完成了futex、线程池、LongAdder等核心概念的初识级笔记，并完成了project-并发-test项目实战。但缺少生产环境验证（如高并发场景下的线程池调优、死锁排查等），因此成熟度标记为 theoretical。待补充AQS、CompletableFuture等中级内容，并完成更多实战后可提升至 verified。

### 检查点
- [ ] 子主题数: 3（超过 7 则触发裂变）
- [ ] 最后更新: 2026-05-16（超过 90 天则触发归档检查）
