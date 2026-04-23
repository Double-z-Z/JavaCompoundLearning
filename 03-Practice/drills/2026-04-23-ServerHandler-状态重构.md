---
created: 2026-04-23
tags: [drill, nio, 状态机, 重构]
difficulty: 🌿
related_concepts:
  - [[状态机模式-服务生命周期]]
  - [[线程池风格接口设计]]
  - [[优雅关闭-Graceful-Shutdown]]
---

# ServerHandler 状态重构练习

> 🎯 目标：将 ServerHandler 从布尔标志改为状态机模式，统一接口命名为线程池风格


## 练习内容

### 题目/需求
1. 将 `isRunning` 布尔标志改为 `State` 枚举（NEW, RUNNING, SHUTTING_DOWN, STOPPED）
2. 修改循环退出条件：从 `isRunning` 改为状态判断
3. 统一接口命名：
   - `shutdown()` - 触发关闭（非阻塞）
   - `awaitTermination(timeout, unit)` - 等待完成（阻塞）
4. 同步修改 `ClientManager` 和 `ChatServer` 保持接口一致


### 我的实现

#### 1. ServerHandler 状态机定义
```java
public enum State {
    NEW,           // 新建状态
    RUNNING,       // 运行中（处理 I/O 和消息）
    SHUTTING_DOWN, // 正在关闭（不再接受新消息，处理队列剩余消息）
    STOPPED        // 已停止
}

private volatile State state = State.NEW;
```

#### 2. 循环条件重构
```java
// 旧代码
while (isRunning) { ... }

// 新代码 - 关键改进
while (state == State.RUNNING || 
       (state == State.SHUTTING_DOWN && !messageQueue.isEmpty())) {
    // 处理 I/O 和消息...
}
```

#### 3. 接口重构
```java
// 旧接口
public boolean shutdown(long timeoutMs);  // 既触发又等待

// 新接口 - 线程池风格
public void shutdown();  // 触发关闭
public boolean awaitTermination(long timeout, TimeUnit unit);  // 等待完成
public boolean awaitTermination(long timeoutMs);  // 便捷方法
```

#### 4. shutdown 实现
```java
public void shutdown() {
    if (state != State.RUNNING) return;
    
    state = State.SHUTTING_DOWN;   // 进入关闭状态
    enqueueShutdownNotice();        // 放入关闭通知
    selector.wakeup();              // 唤醒处理
}
```


### 遇到的困难
- 循环条件的设计：需要表达"正常运行 或 正在关闭但队列未空"
- 层级调用关系：`ChatServer` → `ClientManager` → `ServerHandler` 需要统一修改


## 验证与测试

### 测试方案
1. 编译检查：`mvn compile`
2. 测试编译：`mvn test-compile`

### 测试结果
- ✅ 主代码编译通过
- ✅ 测试代码编译通过
- ✅ `ChatServer.stop()` 无参方法保留向后兼容


## 复盘总结

### 学到的
1. 状态机比布尔标志更清晰，能表达复杂状态（如 SHUTTING_DOWN）
2. 线程池风格的接口分离（触发 vs 等待）更灵活
3. 层级设计时，上层接口应该与下层保持一致

### 关联知识
- [[状态机模式-服务生命周期]] - 应用场景：ServerHandler 生命周期管理
- [[线程池风格接口设计]] - 应用场景：统一关闭接口命名

### 待深化
- `shutdownNow()` 的实现（立即关闭，中断正在执行的任务）
- 状态机的线程安全性（volatile 是否足够？）


---

## 🤖 AI评价

### 完成质量
- 功能实现：完整
- 代码质量：良好
- 概念应用：正确

### 对掌握度的影响
- [[状态机模式-服务生命周期]]: +20分 (正确应用，完成重构)
- [[线程池风格接口设计]]: +20分 (理解设计优势，完成接口统一)

### 建议
1. 对比 Java ThreadPoolExecutor 的完整状态机实现
2. 思考 `isShutdown()`、`isTerminated()` 查询方法的价值

---

## 📊 相关练习统计

| 日期 | 练习 | 难度 | 相关概念 |
|------|------|------|----------|
| 2026-04-23 | ServerHandler 状态重构 | 🌿 | 状态机、接口设计 |
