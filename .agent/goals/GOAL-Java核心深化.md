---
type: goal
description: 深化Java核心能力：多线程与并发、IO/NIO、反射与注解
driver: 晋升层
urgency: high
deadline: 2026-08-06
review_date: 2026-05-13
exit_conditions:
  - 能够徒手实现高性能线程池
  - 理解NIO epoll机制并能解释Select/epoll区别
  - 掌握Java内存模型与volatile/CAS底层实现
  - 完成至少2个项目实战
evidence: 简历要求"深入掌握Java核心语法、面向对象编程、多线程与并发、IO/NIO"，当前NIO/并发已有基础但未达精通
status: in_progress
related_emrg:
  - EMRG-并发编程
  - EMRG-NIO网络编程
---

# GOAL: Java核心深化

## 目标技能

| 技能 | 当前水平 | 目标水平 |
|------|---------|---------|
| 多线程与并发 | 🌿理解 (60) | 🍎应用 (80) |
| IO/NIO | 🍎应用 (70) | 🌳掌握 (85) |
| 反射与注解 | 🌱初识 (30) | 🌿理解 (50) |

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
```

## 进度追踪

- [x] NIO三大组件（Buffer/Channel/Selector）
- [ ] epoll机制深入理解
- [ ] 手写线程池
- [ ] volatile/CAS底层
- [ ] 反射机制实战
- [ ] 注解自定义

## 关联项目

- [[bio-chatroom]] → BIO理解
- [[nio-chatroom]] → NIO实战
- [[project-concurrency-test]] → 并发实战
