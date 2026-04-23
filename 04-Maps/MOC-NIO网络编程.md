---
created: 2026-04-21
tags:
  - MOC
  - nio
  - network
---

# MOC: NIO 网络编程

> Java NIO 与网络编程知识地图


## 核心概念

| 概念 | 一句话描述 | 掌握状态 |
|-----|-----------|---------|
| [[NIO-Buffer]] | 带状态管理的智能数据容器（position/limit/capacity） | 🍎 |
| [[NIO-Channel]] | 双向非阻塞IO通道，连接Buffer与网络 | 🌿 |
| [[NIO-Selector]] | 多路复用器，单线程管理多连接（epoll实现） | 🍎 |
| [[BIO-BlockingIO]] | 阻塞IO模型，一连接一线程 | 🌿 |
| [[Socket-EOF-Semantics]] | read()=-1 表示流结束，不是消息边界 | 🌿 |
| [[粘包拆包]] | TCP字节流无消息边界，应用层必须自行分割 | 🌿 |
| [[Boss-Worker模型]] | Boss线程accept，Worker线程处理读写 | 🌿 |
| [[OP_WRITE事件处理]] | 只在待发送数据时注册，发送完取消 | 🌿 |
| [[NIO优雅关闭模式]] | 先标志位、再wakeup、最后强制关闭的三阶段模式 | 🌿 |
| [[跨线程通信]] | 使用ConcurrentLinkedQueue + selector.wakeup() | 🌿 |
| [[CopyOnWriteArrayList]] | 读多写少场景的安全遍历集合 | 🌿 |
| [[Race-Condition]] | 多线程时序竞态导致的隐蔽Bug | 🌿 |


## 知识网络

```dataview
table status, file.mtime as "更新时间"
from #nio
sort file.mtime desc
```


## 项目实战

| 项目 | 描述 | 状态 |
|------|------|------|
| [[bio-chatroom]] | BIO阻塞模型聊天室，理解一连接一线程的局限性 | ✅ 完成 |
| [[nio-chatroom]] | NIO非阻塞模型聊天室，实践Selector + Buffer + 多线程架构 | ✅ 完成 |
| NIO文件传输 | 零拷贝 + 内存映射文件实践 | ⏳ 待开始 |


## 我的误区档案

| 编号 | 错误 | 关联概念 |
|------|------|---------|
| [[MISTAKE-001-SocketEOFDeadloop]] | 将 `read()=-1` 误解为消息结束，导致死循环 | [[Socket-EOF-Semantics]] |
| [[MISTAKE-002-NIO-RaceCondition-Register]] | Worker注册竞态条件导致客户端静默丢失 | [[Boss-Worker模型]]、[[Race-Condition]] |
| [[MISTAKE-003-NIO-ByteBuffer-Mode]] | `wrap()`后再`flip()`导致数据错位 | [[NIO-Buffer]]、[[粘包拆包]] |
| [[MISTAKE-004-NIO-CancelledKeyException]] | 操作已取消的Key导致异常 | [[NIO-Selector]]、[[NIO优雅关闭模式]] |


## 对话反思记录

- [[2026-04-17-NIO学习对话]] — NIO三大组件基础概念学习
- [[2026-04-19-nio-partial-write-question]] — NIO部分发送的疑问（待深入研究）
- [[2026-04-21-nio-chatroom-debug]] — NIO聊天室广播调试实录（4个连锁Bug）


## 练习记录

- [[2026-04-19-broadcast-implementation]] — 广播功能实现与Bug修复


## 学习进度

- [x] BIO聊天室项目（理解阻塞模型局限性）
- [x] NIO三大组件基础概念（Buffer、Channel、Selector）
- [x] NIO聊天室项目（实践多线程架构、广播功能）
- [x] 消息协议设计（长度前缀协议）
- [x] 粘包/拆包处理（compact模式）
- [x] 写半包处理（OP_WRITE事件管理）
- [x] 跨Worker广播设计（队列+wakeup）
- [ ] Netty框架学习（事件循环、Pipeline、ByteBuf）
- [ ] 零拷贝技术（mmap、sendfile）
- [ ] 性能优化（背压、流量控制）
- [ ] 知识网络密度检查（每个节点≥2个链接）


## 能力评估

| 维度            | 当前等级       | 趋势  |
| ------------- | ---------- | --- |
| NIO-Buffer    | 🍎 应用 (70) | ↗️  |
| NIO-Selector  | 🍎 应用 (70) | ↗️  |
| NIO-Channel   | 🌿 理解 (50) | →   |
| Boss-Worker模型 | 🌿 理解 (55) | ↗️  |
| 网络编程综合        | 80分        | ↗️  |


---
📊 **网络密度**: 检查中
🎯 **下一步**: Netty框架学习 / 零拷贝技术
