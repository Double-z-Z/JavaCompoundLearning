# NIO聊天室 - 测试策略

> 创建时间：2026-04-23  
> 最后更新：2026-04-23（合并ShutdownNotificationTest到ShutdownTest）  
> 关联：[[nio-chatroom]]、[[测试设计]]

---

## 测试分层原则

按优先级排序：

1. **场景覆盖**（服务质量）- 最优先
   - 用户视角：优雅关闭是否成功？
   
2. **功能覆盖**（服务质量）
   - 系统视角：关闭流程是否正确？
   
3. **边界情况**（工程质量）
   - 开发者视角：无客户端、超时等边界

> 测试应该先确保服务质量，再确保工程质量。底层可以无感知升级。

---

## 测试目录结构

```
src/test/java/com/example/
├── Server/                        # 服务端功能测试
│   ├── ChatServerLifecycleTest.java       # 生命周期（状态机）
│   ├── ChatServerShutdownTest.java        # 关闭行为（场景+功能+边界）
│   ├── ClientConnectionTest.java          # 客户端连接管理
│   ├── BroadcastTest.java                 # 广播功能
│   └── TestChatClient.java                # 测试工具类
├── performance/                   # 性能测试
│   ├── BroadcastPerformanceTest.java
│   └── RegisterPerformanceTest.java
└── unit/                          # 单元测试（暂不实现）
    └── message/
        └── (协议简单，无需单独单元测试)
```

---

## 服务端功能测试（Server/）

### 1. ChatServerLifecycleTest - 生命周期（状态机）

**测试目标**：验证服务器状态转换的正确性，不涉及客户端交互

**状态转换图**：
```
NEW → RUNNING → STOPPED
 ↑______↓
（支持重复启停）
```

| 测试方法 | 场景 | 分类 |
|----------|------|------|
| testStartAndStop | 基本启动停止 | 基本状态转换 |
| testStartIsSynchronous | 启动同步性 | 基本状态转换 |
| testRestart | 重复启停 | 基本状态转换 |
| testDoubleStartThrowsException | 重复启动抛异常 | 边界情况 |
| testStopWithoutStart | 停止未启动的服务 | 边界情况 |
| testDoubleStop | 重复停止安全 | 边界情况 |
| testStartTimeoutMechanism | 启动超时机制 | 超时/异常 |
| testConcurrentStart | 并发启动安全 | 并发安全 |

---

### 2. ChatServerShutdownTest - 关闭行为

**测试目标**：验证服务器关闭时的行为（按优先级组织）

#### 场景覆盖（服务质量）

| 测试方法 | 场景 | 验证点 |
|----------|------|--------|
| testGracefulShutdown_Success | 优雅关闭成功 | 返回true、客户端收到通知、主动断开 |
| testForceShutdown_Timeout | 强制关闭（客户端不配合） | 返回false、超时后强制断开 |

#### 功能覆盖（服务质量）

| 测试方法 | 功能 | 验证点 |
|----------|------|--------|
| testShutdownSendsNotificationToAllClients | 通知发送 | 所有客户端收到SHUTDOWN_NOTICE |
| testShutdownIsSynchronous | 同步性 | stop()返回时服务器已完全停止 |

#### 边界情况（工程质量）

| 测试方法 | 场景 |
|----------|------|
| testShutdownWithoutClients | 无客户端时快速关闭 |
| testShutdownWithTimeout | 带超时的停止接口 |

---

### 3. ClientConnectionTest - 客户端连接管理

| 测试方法 | 场景 |
|----------|------|
| testClientCanConnect | 客户端正常连接 |
| testClientDisconnectHandled | 客户端断开处理 |
| testMultipleClientsConnectAndDisconnect | 多客户端依次连接断开 |

---

### 4. BroadcastTest - 广播功能

| 测试方法 | 场景 |
|----------|------|
| testBasicBroadcast | 基础广播（1发多收） |

---

## 历史变更

### 2026-04-23 - 合并ShutdownNotificationTest到ShutdownTest

**变更原因**：
- 测试分层不清晰，ShutdownTest只测了返回值（底层实现），NotificationTest测了消息（组件）
- 缺少真正的**场景覆盖**：优雅关闭是否成功？
- 按优先级重新组织：场景 > 功能 > 边界

**变更详情**：

| 原测试类 | 原方法 | 迁移目标 |
|----------|--------|----------|
| ShutdownNotificationTest | testServerSendsShutdownNotice | 合并到testShutdownSendsNotificationToAllClients |
| ShutdownNotificationTest | testMultipleClientsReceiveShutdownNotice | 合并到testShutdownSendsNotificationToAllClients |
| ShutdownNotificationTest | testClientDisconnectsAfterShutdownNotice | 合并到testGracefulShutdown_Success |
| ShutdownNotificationTest | testAllClientsDisconnectGracefully | 合并到testGracefulShutdown_Success |
| ChatServerShutdownTest | testGracefulShutdownReturnsTrue | 重构为testGracefulShutdown_Success（增加验证点） |
| ChatServerShutdownTest | testForceShutdownReturnsFalse | 重命名为testForceShutdown_Timeout |
| ChatServerShutdownTest | testStopIsSynchronous | 重命名为testShutdownIsSynchronous |
| ChatServerShutdownTest | testStopWithoutClient | 重命名为testShutdownWithoutClients |
| ChatServerShutdownTest | testStopWithTimeout | 重命名为testShutdownWithTimeout |

**优化效果**：
- 场景覆盖：`testGracefulShutdown_Success` 真正验证优雅关闭流程
- 职责清晰：一个类按优先级组织所有关闭相关测试
- 可维护性：减少类数量，降低理解成本

---

### 2026-04-23 - 删除ChatServerIntegrationTest

**变更原因**：
- 测试目标与现有测试高度重叠
- 使用旧API（`ClientManager`直接启动）
- 维护成本高，价值低

**变更详情**：

| 原测试方法 | 处理方式 | 说明 |
|------------|----------|------|
| testSingleClientConnection | 删除 | 被`ChatServerLifecycleTest.testStartIsSynchronous`覆盖 |
| testMultipleClientConnections | 删除 | 被`BroadcastTest`和`ShutdownNotificationTest`覆盖 |
| testClientDisconnect | 迁移 | 保留到`ClientConnectionTest.testClientDisconnectHandled` |
| testServerRestart | 删除 | 被`ChatServerLifecycleTest.testRestart`覆盖 |

**新增**：
- `ClientConnectionTest` - 专注于客户端连接生命周期

---

### 2026-04-23 - 生命周期测试重组

**变更前**：
- `ChatServerLifecycleTest`（10个方法）- 包含生命周期和关闭行为
- `GracefulShutdownTest`（6个方法）- 包含关闭行为和通知协议

**问题**：
- 两个类都验证 `stop()` 返回值（冗余）
- `testStopIsSynchronous` 和 `testGracefulShutdownSuccess` 重叠
- `testStopWithTimeout` 和 `testForceShutdownOnTimeout` 重叠

**变更后**：
- `ChatServerLifecycleTest`（8个方法）- 纯服务器状态机
- `ChatServerShutdownTest`（6个方法）- 关闭行为（场景+功能+边界）

**删除**：
- `GracefulShutdownTest` - 功能被拆分合并到新类
- `ShutdownNotificationTest` - 合并到ShutdownTest

---

## 测试演进机制

### 定期审视清单（每2周或每个Phase结束）

| 检查项 | 问题 | 行动 |
|--------|------|------|
| 新功能是否有场景级测试？ | 否 | 编写场景测试 |
| 场景测试是否验证真正业务价值？ | 否 | 重构测试 |
| 多个测试是否验证相同场景？ | 是 | 合并或删除冗余 |
| 测试分层是否清晰？ | 否 | 按场景/功能/边界重组 |
| 测试是否使用最新API？ | 否 | 迁移到最新API |

### 测试质量检查

**场景级测试必须验证**：
- [ ] 业务结果正确（不是仅返回值正确）
- [ ] 涉及的所有组件都正确协作
- [ ] 无异常/错误日志
- [ ] 边界情况有覆盖

---

## 测试执行配置

### Maven Profile

```bash
# 仅运行单元测试（快速）
mvn test -Punit-tests

# 运行集成测试
mvn test -Pintegration-tests

# 运行性能测试
mvn test -Pperformance-tests

# 运行所有测试
mvn test
```

### 当前Surefire配置

- ✅ 顺序执行（避免NIO端口冲突）
- ✅ 独立JVM（测试隔离）
- ✅ 失败重试1次（减少偶发失败）

---

## 缺失场景（P1/P2）

> **注**：协议层单元测试已从计划中删除，当前长度前缀协议简单，已在集成测试中间接验证。

详见：[[TODO#测试覆盖缺失]]

---

## 关联文档
- [[test-coverage]] - 测试覆盖矩阵
- [[TODO]] - 项目待办
- [[测试设计]] - 测试设计知识
