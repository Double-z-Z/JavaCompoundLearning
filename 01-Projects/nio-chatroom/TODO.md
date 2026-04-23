# NIO聊天室项目 - 优化待办列表

> 创建时间：2026-04-17  
> 最后更新：2026-04-23  
> 项目阶段：Phase 3 - 测试完善与性能优化

---

## 测试覆盖缺失 ⭐ 新增

详见：[[test-coverage]]、[[test-strategy]]

### P0 - 协议层测试（本周必做）
- [ ] `ChatMessageEncoderTest` - 消息编码单元测试
  - 测试场景：正常消息、空消息、超长消息、特殊字符
- [ ] `ChatMessageDecoderTest` - 消息解码单元测试
  - 测试场景：正常解码、非法长度、截断数据、空数据
- [ ] `ProtocolEdgeCaseTest` - 协议边界测试
  - 测试场景：粘包处理、拆包处理、半包累积

### P1 - 功能完整性测试（下周）
- [ ] `ChatClientIntegrationTest` - 客户端独立集成测试
  - 测试场景：连接超时、重连机制、心跳保活
- [ ] 异常断开处理测试
  - 测试场景：网络分区、客户端崩溃、服务器强制关闭
- [ ] 并发安全测试
  - 测试场景：多线程消息发送、竞态条件验证

### P2 - 性能与压力测试（后续）
- [ ] C10K并发连接测试（1000+连接）
- [ ] 长时间稳定性测试（内存泄漏检测）
- [ ] GC压力测试

---

## 高优先级

### 1. 消息协议设计
- [x] 确定消息格式（长度前缀 + JSON）
- [x] 实现消息编解码器（Encoder/Decoder）
- [ ] ~~处理粘包/拆包问题~~ ➜ 移至P0测试任务
- [x] 定义消息类型枚举（CHAT、SYSTEM、SHUTDOWN_NOTICE）

**参考方案**：
```
长度前缀：4字节（大端序）
消息体：JSON格式 {"type":"CHAT","sender":"Alice","content":"Hello","timestamp":...}
```

---

### 2. 消息队列解耦 ✅ 已完成
- [x] 引入消息队列（ConcurrentLinkedQueue）
- [x] 分离IO线程和业务处理线程
- [x] 实现广播消息的异步处理
- [ ] ~~设计队列满时的背压策略~~ ➜ 移至低优先级

**架构设计**：
```
Worker线程 ──► 读取消息 ──► 消息队列 ──► 业务线程 ──► 广播队列 ──► Worker发送
```

---

### 3. 广播功能完善 ✅ 已完成
- [x] 实现ServerHandler.broadcast()方法
- [x] 处理写半包（注册OP_WRITE）
- [x] 处理发送失败（客户端断开）
- [ ] ~~支持私聊功能~~ ➜ 移至功能扩展
- [x] 测试广播功能正确性（BroadcastTest通过）
- [x] 测试广播性能（BroadcastPerformanceTest通过）

**性能数据**（2026-04-21）：
- 10客户端 × 50消息 = 4500条广播消息
- 吞吐量：~2142条/秒
- 消息到达率：100%

---

## 中优先级

### 4. Worker选择算法优化
- [ ] 当前随机选择改为轮询（原子递增）
- [ ] 考虑Worker负载均衡
- [ ] 客户端重连时保持绑定同一Worker

**当前代码**：
```java
int workerIndex = (int) Math.random() * workerCount;  // 随机，可能不均
```

**优化目标**：
```java
int workerIndex = counter.getAndIncrement() % workerCount;  // 轮询
```

---

### 5. 资源清理完善 ✅ 已完成
- [x] 完善shutdown()方法（优雅关闭）
- [x] 处理stop()中的异常（使用IoUtils.closeQuietly）
- [x] 实现awaitTermination()
- [x] 确保Channel和Selector正确关闭

**已完成**：
- 使用java-io-utils库的IoUtils.closeQuietly()
- 实现selector.wakeup()唤醒机制
- 关闭耗时：5006ms → 2ms（优化后）

---

### 6. 异常处理
- [ ] 统一异常处理策略
- [ ] 记录关键日志
- [ ] 客户端异常断开处理
- [ ] 网络超时处理

---

## 低优先级

### 7. 性能优化
- [ ] Buffer池化（避免频繁创建）
- [ ] 使用DirectBuffer（零拷贝）
- [ ] 批量发送优化
- [ ] ~~压力测试和性能基准~~ ➜ 已移至P2测试任务

**性能测试已创建**：
- BroadcastPerformanceTest.java
- RegisterPerformanceTest.java

---

### 8. 功能扩展
- [ ] 用户认证
- [ ] 房间/群组功能
- [ ] 心跳检测
- [ ] 消息持久化（可选）

---

## 技术决策记录

### 已决策
| 决策 | 选择 | 原因 |
|------|------|------|
| 线程模型 | 单Boss + 多Worker | 简单，足够用 |
| Worker分配 | 轮询（待实现） | 均衡，无锁 |
| 队列类型 | ConcurrentLinkedQueue | 非阻塞，适合NIO |
| 资源关闭 | IoUtils.closeQuietly() | 统一处理，避免异常干扰 |
| 广播方案 | 简单方案（每个Channel独立队列） | 快速实现，后续可优化 |
| 测试框架 | JUnit 4 + Maven Surefire | 成熟稳定 |

### 测试相关决策（2026-04-23 新增）
| 决策 | 选择 | 原因 |
|------|------|------|
| 测试分类 | unit / integration / performance / e2e | 层次分明 |
| 执行方式 | 顺序执行 + 独立JVM | 避免NIO端口冲突 |
| 冗余处理 | 删除ClientManagerShutdownTest等 | 避免重复 |

---

## 历史完成记录

### 2026-04-23 - 测试整理
1. **删除冗余测试**
   - 删除 `ClientManagerShutdownTest`（功能被生命周期测试覆盖）
   - 删除 `GracefulShutdownDebugTest`（调试专用）
2. **建立测试文档**
   - 创建 [[test-strategy]] - 测试策略与目录规划
   - 创建 [[test-coverage]] - 测试覆盖矩阵
3. **规划缺失测试**
   - 识别P0/P1/P2优先级测试任务
   - 记录到测试覆盖文档

### 2026-04-22 - ChatServer接口改造
1. **创建ChatServer封装类**
   - 提供阻塞式start()和优雅stop()
   - 支持重复启停
2. **测试结构重组**
   - 功能测试与性能测试分离（performance/目录）
3. **Maven配置优化**
   - Surefire顺序执行、独立JVM、Profile隔离
4. **依赖升级**
   - logback 1.2.12 → 1.3.14

### 2026-04-21 - 广播功能修复
1. **修复4个连锁Bug**
   - 竞态条件（客户端注册与广播）
   - Buffer模式错误
   - CancelledKeyException
   - 并发集合问题
2. **实现ChatClient和测试客户端**
   - 长度前缀协议
   - SHUTDOWN_NOTICE处理
3. **性能测试通过**
   - 4500条广播0丢失
   - 吞吐量2142条/秒

### 2026-04-19 - 广播功能基础实现
1. **Worker内消息队列**
   - ConcurrentLinkedQueue
   - 每个Channel独立待发送队列
2. **跨Worker广播**
   - ClientManager转发机制
3. **写事件处理**
   - OP_WRITE注册/取消
   - 不回传来源客户端

---

## 下一步计划

### 短期（下次学习）
1. 实现P0优先级测试（协议层单元测试）
2. 验证粘包/拆包处理逻辑

### 中期
1. 实现P1优先级测试（客户端集成、异常处理）
2. Worker轮询分配算法

### 长期
1. P2性能测试（C10K）
2. 用户认证和房间功能

---

## 关联知识
- [[NIO-Buffer]] - Buffer使用优化
- [[NIO-Channel]] - Channel管理
- [[NIO-Selector]] - Selector线程模型
- [[test-strategy]] - 测试策略
- [[test-coverage]] - 测试覆盖详情
