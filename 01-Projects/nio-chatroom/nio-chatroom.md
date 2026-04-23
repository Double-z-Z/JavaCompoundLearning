---
created: 2026-04-17
updated: 2026-04-23
tags: [project, nio, network, testing]
status: active
---

# NIO聊天室项目

> 项目目标：基于NIO（非阻塞IO）实现多人聊天室，对比BIO实现，深入理解NIO的优势和编程模型  
> 项目类型：学习验证型 / 性能对比型

---

## 📊 项目进展一览

| 阶段 | 状态 | 完成度 | 关键成果 |
|------|------|--------|----------|
| **Phase 1** - NIO基础框架 | ✅ 完成 | 100% | Selector多路复用、非阻塞Channel |
| **Phase 2** - 消息广播 | ✅ 完成 | 95% | 跨Worker广播、写事件处理、优雅关闭 |
| **Phase 3** - 测试完善 | 🔄 进行中 | 60% | [[test-strategy]]、[[test-coverage]] |
| **Phase 4** - 性能优化 | ⏳ 待开始 | 0% | Buffer池化、主从Reactor |

**当前重点**：完善测试覆盖，特别是[[TODO#测试覆盖缺失|协议层单元测试]]

---

## 🧪 测试覆盖状态

详见：[[test-coverage]]、[[test-strategy]]

### 已覆盖 ✅
| 测试类型 | 测试类 | 测试数 | 核心场景 |
|----------|--------|--------|----------|
| 生命周期 | [[ChatServerLifecycleTest]] | 10 | 启动/停止、同步性、超时、并发启动 |
| 广播功能 | [[BroadcastTest]] | 1 | 基础广播（1发多收） |
| 优雅关闭 | [[GracefulShutdownTest]] | 6 | SHUTDOWN_NOTICE、强制关闭 |
| 广播性能 | [[BroadcastPerformanceTest]] | 2 | 吞吐量~2142条/秒 |
| 注册性能 | [[RegisterPerformanceTest]] | 1 | 客户端注册耗时 |

### 缺失场景 ⏳
| 优先级 | 缺失测试 | 场景 |
|--------|----------|------|
| **P0** | `ChatMessageEncoderTest` | 消息编码单元测试 |
| **P0** | `ChatMessageDecoderTest` | 消息解码单元测试 |
| **P0** | `ProtocolEdgeCaseTest` | 粘包/拆包边界测试 |
| **P1** | `ChatClientIntegrationTest` | 客户端独立集成测试 |
| **P2** | C10K并发测试 | 1000+连接压力测试 |

---

## 🏗️ 架构设计

### 项目结构
```
nio-chatroom/
├── src/main/java/com/example/
│   ├── Client/
│   │   ├── ChatClient.java          # NIO客户端
│   │   └── ClientHandler.java       # 客户端处理器
│   ├── Server/
│   │   ├── ChatServer.java          # 服务端封装（阻塞式API）
│   │   ├── ClientManager.java       # 客户端管理（Boss线程）
│   │   ├── ClientContext.java       # 客户端上下文
│   │   └── ServerHandler.java       # Worker处理器
│   └── message/
│       ├── ChatMessage.java         # 消息实体
│       ├── ChatMessageEncoder.java  # 消息编码器
│       ├── ChatMessageDecoder.java  # 消息解码器
│       ├── MessageType.java         # 消息类型枚举
│       └── BroadcastMessage.java    # 广播消息封装
├── src/test/java/com/example/
│   ├── integration/                 # 集成测试
│   ├── performance/                 # 性能测试
│   └── unit/                        # 单元测试（待创建）
└── docs/
    ├── test-strategy.md             # 测试策略
    └── test-coverage.md             # 测试覆盖矩阵
```

### 线程模型
```
┌─────────────────────────────────────────────────────────────┐
│                        ChatServer                           │
├─────────────────────────────────────────────────────────────┤
│  Boss Thread (ClientManager)                                │
│  ├── Selector.select()  ← 监听ACCEPT事件                    │
│  ├── 接受连接 → 创建ClientContext                           │
│  └── 轮询分配给Worker                                       │
├─────────────────────────────────────────────────────────────┤
│  Worker Threads (ServerHandler × N)                         │
│  ├── 每个Worker独立Selector                                  │
│  ├── 处理READ/WRITE事件                                      │
│  ├── 消息队列：ConcurrentLinkedQueue                         │
│  └── 广播：跨Worker转发（ClientManager协调）                 │
└─────────────────────────────────────────────────────────────┘
```

### 消息协议
```
┌─────────────────┬──────────────────────────────────────────┐
│  长度前缀(4字节)  │              JSON消息体                   │
│   (大端序)       │                                          │
└─────────────────┴──────────────────────────────────────────┘

示例：
长度前缀: 0x00 0x00 0x00 0x45 (69字节)
消息体:   {"type":"CHAT","sender":"Alice","content":"Hello","timestamp":1713876543210}
```

---

## 📈 性能数据

### 广播性能（2026-04-21）
| 指标 | 数值 |
|------|------|
| 测试配置 | 10客户端 × 50消息 |
| 理论广播数 | 4500条（N×M×(N-1)） |
| 实际到达 | 4500条（100%到达率） |
| 吞吐量 | ~2142条/秒 |
| 内存使用 | <50MB |

### 关闭性能（2026-04-22）
| 优化项 | 优化前 | 优化后 |
|--------|--------|--------|
| 关闭耗时 | 5006ms | 2ms |
| 优化手段 | - | selector.wakeup() + 异步关闭 |

---

## 🎯 涉及知识点

| 知识点 | 在项目中的角色 | 掌握状态 | 相关笔记 |
|--------|---------------|----------|----------|
| [[NIO-Buffer]] | 核心机制 - 数据读写容器 | 🍎 应用 (70) | [[2026-04-17-nio-chatroom-phase1]] |
| [[NIO-Channel]] | 核心机制 - 网络通信通道 | 🌿 理解 (50) | - |
| [[NIO-Selector]] | 核心机制 - 多路复用 | 🍎 应用 (70) | [[NIO-Selector]] |
| [[Boss-Worker模型]] | 架构模式 - 多线程处理 | 🌿 理解 (55) | [[Boss-Worker模型]] |
| [[长度前缀协议]] | 协议设计 - 解决粘包 | 🍎 应用 (60) | [[粘包拆包]] |
| [[CompletableFuture]] | 异步编程 - 同步封装 | 🌿 理解 (45) | [[CompletableFuture]] |
| [[Maven-Surefire-Plugin]] | 测试工具 - 测试执行 | 🌿 理解 (40) | [[Maven-Surefire-Plugin]] |
| [[测试设计]] | 测试方法 - 测试策略 | 🌿 理解 (55) | [[测试设计]] |

---

## 💡 关键设计决策

### 决策1：单Boss + 多Worker线程模型
- **选择**：Boss处理ACCEPT，Worker处理READ/WRITE
- **原因**：这让我想起你之前学的[[Boss-Worker模型]]——Netty的经典设计
- **优势**：线程数不随连接数增长，支持C10K

### 决策2：长度前缀协议
- **选择**：4字节长度前缀 + JSON消息体
- **原因**：这让我想起你之前学的[[粘包拆包]]——NIO必须处理的问题
- **优势**：简单、可读、易调试

### 决策3：每个Channel独立发送队列
- **选择**：LinkedList<ByteBuffer> per Channel
- **原因**：避免共享队列的锁竞争
- **权衡**：内存占用 vs 并发性能

### 决策4：ChatServer阻塞式封装
- **选择**：CompletableFuture封装异步启动
- **原因**：这让我想起你之前学的[[CompletableFuture]]——优雅的异步转同步
- **优势**：测试友好，API简洁

---

## 🐛 项目特有的坑与解决方案

### 坑1：OP_WRITE事件处理
**现象**: 注册OP_WRITE后，Selector不断返回WRITE就绪，导致CPU 100%  
**原因**: 只要发送缓冲区有空闲，WRITE就一直就绪（水平触发）  
**解决**: 只在需要写数据时注册OP_WRITE，写完立即取消  
**关联**: [[MISTAKE-004]]

### 坑2：Buffer复用导致数据混乱
**现象**: 消息内容错乱，出现"粘包"  
**原因**: 多个Channel共享同一个Buffer，或者Buffer没有正确clear()  
**解决**: 每个Channel有自己的Buffer，或使用ThreadLocal  
**关联**: [[MISTAKE-003]]

### 坑3：客户端异常断开处理
**现象**: 客户端强制关闭，服务端没有感知，仍在发送数据  
**原因**: 没有正确处理read()=-1或IOException  
**解决**: 捕获异常，cancel key，关闭Channel  
**关联**: [[MISTAKE-001]]

### 坑4：竞态条件（客户端注册 vs 广播）
**现象**: 新客户端连接后收不到广播消息  
**原因**: 客户端注册到Selector和广播消息存在时间窗口  
**解决**: 同步机制 + 等待注册完成  
**关联**: [[MISTAKE-005]]

### 坑5：shutdown()阻塞
**现象**: 服务器关闭耗时5000ms+  
**原因**: selector.select()阻塞，无法响应关闭信号  
**解决**: selector.wakeup()唤醒 + 异步关闭流程  
**关联**: [[资源管理]]

---

## 📋 待办事项

详见：[[TODO]]

**本周重点（P0）**：
- [ ] 实现 `ChatMessageEncoderTest`
- [ ] 实现 `ChatMessageDecoderTest`
- [ ] 实现 `ProtocolEdgeCaseTest`

---

## 🔗 相关链接

| 类型 | 链接 |
|------|------|
| 项目待办 | [[TODO]] |
| 测试策略 | [[test-strategy]] |
| 测试覆盖 | [[test-coverage]] |
| 主题地图 | [[MOC-nio]] |
| BIO对比 | [[bio-chatroom]] |
| 学习路线 | [[LEARNING-ROADMAP]] |

---

📊 **项目完成度**: 65%  
🎯 **核心收获**: NIO多路复用、Boss-Worker模型、长度前缀协议、优雅关闭  
🔗 **关联练习数**: 5  
📈 **涉及知识点**: 8个（平均掌握度58分）
