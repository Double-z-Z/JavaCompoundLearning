---
type: goal
status: active
driver: promotion
urgency: high
deadline: 2026-06-29
review_date: 2026-05-29
incident_ref: [[goal-java简历]]
exit_conditions:
  - 能够徒手实现高性能线程池
  - 理解NIO epoll机制并能解释Select/epoll区别
  - 掌握Java内存模型与volatile/CAS底层实现
  - 完成至少2个项目实战
gap_analysis:
  - EMRG现状: 并发编程(mastery=80)已掌握，NIO/网络(mastery=70)应用级
  - GOAL目标: Java核心全面精通，达到L3水平
  - 缺口: epoll机制、线程池原理、反射与注解、Netty深入
related_emrg:
  - EMRG-并发编程
  - EMRG-NIO网络编程
created: 2026-05-06
updated: 2026-05-29
---

# GOAL: Java核心深化

## 驱动信息

| 字段 | 值 |
|------|-----|
| driver | promotion（晋升层） |
| urgency | high |
| deadline | 2026-08-06 |
| incident_ref | [[goal-java简历]] - 简历要求"深入掌握Java核心语法、面向对象编程、多线程与并发、IO/NIO" |

### 驱动来源

简历明确要求：
> 深入掌握 Java 核心语法、面向对象编程、多线程与并发、IO/NIO、反射与注解等关键技术领域。

当前水平与简历要求存在差距，需要系统深化。

## 退出条件

- [ ] 能够徒手实现高性能线程池
- [ ] 理解NIO epoll机制并能解释Select/epoll区别
- [ ] 掌握Java内存模型与volatile/CAS底层实现
- [ ] 完成至少2个项目实战
- [ ] mastery总分达到350以上

## 缺口矩阵

| GOAL要求 | EMRG现状 | 差距 | 学习策略 |
|---------|---------|------|---------|
| 多线程与并发 | mastery=80（L3） | 已掌握 | 巩固+深入 |
| IO/NIO | mastery=70（🍎应用） | 需深入epoll | 项目驱动 |
| 反射与注解 | mastery=30（🌱初识） | 完全空白 | 框架原理切入 |
| Netty深入 | mastery=65（🍎应用） | 事件循环不清晰 | 源码阅读 |

## 目标技能

| 技能 | 当前水平 | 目标水平 |
|------|---------|---------|
| 多线程与并发 | 🍎 80 | 🌳 90 |
| IO/NIO | 🍎 70 | 🌳 85 |
| 反射与注解 | 🌱 30 | 🌿 60 |
| Netty深入 | 🍎 65 | 🌳 85 |

## 学习路径

```
阶段1: 并发深化
├── futex机制 → 理解内核态/用户态切换
├── JUC工具类 → 手写线程池
└── 内存模型 → volatile/CAS原理

阶段2: NIO深化
├── epoll机制 → 理解多路复用本质
├── ByteBuf → 内存管理优化
└── 粘包拆包 → 协议设计

阶段3: 反射与注解
├── 反射机制 → 框架原理
└── 注解原理 → 自定义注解

阶段4: Netty深入
├── EventLoop线程模型
├── Pipeline源码
└── ByteBuf内存管理
```

## 进度追踪

- [x] NIO三大组件（Buffer/Channel/Selector）
- [ ] epoll机制深入理解
- [ ] 手写线程池
- [ ] volatile/CAS底层
- [ ] 反射机制实战
- [ ] 注解自定义
- [ ] Netty源码阅读

## 关联

### EMRG
- [[EMRG-并发编程]]
- [[EMRG-NIO网络编程]]

### 项目
- [[bio-chatroom]]
- [[nio-chatroom]]
- [[project-concurrency-test]]

---

## 更新记录

| 日期 | 更新内容 | 操作者 |
|------|---------|--------|
| 2026-05-06 | 重建为工程化GOAL，添加驱动信息/缺口矩阵 | AI |
