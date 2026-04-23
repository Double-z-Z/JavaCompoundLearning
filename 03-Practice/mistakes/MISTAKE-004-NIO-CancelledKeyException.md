---
created: 2026-04-21
tags: [mistake, nio, selector, exception-handling]
error_id: MISTAKE-004
status: resolved
related_concepts:
  - [[NIO-Selector]]
  - [[NIO优雅关闭模式]]
  - [[SelectionKey]]
---

# Selector 循环中操作已取消的 Key 导致 CancelledKeyException

**错误ID**: MISTAKE-004  
**所属主题**: [[MOC-nio]]  
**状态**: 🟢 已解决


## 错误现象

广播测试通过，但在 tearDown 阶段 Worker 线程崩溃，抛出 `CancelledKeyException`：

```
java.nio.channels.CancelledKeyException
    at sun.nio.ch.SelectionKeyImpl.ensureValid(SelectionKeyImpl.java:73)
    at sun.nio.ch.SelectionKeyImpl.readyOps(SelectionKeyImpl.java:87)
    at java.nio.channels.SelectionKey.isWritable(SelectionKey.java:312)
    at com.example.Server.ServerHandler.run(ServerHandler.java:80)
```


## 我的错误理解

> "客户端断开时 closeClient() 已经取消了 key，但 select() 返回的 selectedKeys 里不应该再有这个 key 才对。"

我以为 `key.cancel()` 后，这个 key 会立刻从 `selectedKeys()` 中消失。但实际上 `cancel()` 只是标记为取消，真正的移除发生在下次 `select()` 时。


## 根本原因分析

### `key.cancel()` 的语义

```java
private void closeClient(SocketChannel channel, SelectionKey key) {
    IoUtils.closeQuietly(channel);
    key.cancel();  // ← 只是标记为"待取消"，不是立即生效
    channels.remove(channel);
    clientContextMap.remove(channel);
}
```

`cancel()` 的底层行为：
1. 将 Key 的状态设为 `CANCELLED`
2. 把 Key 加入 Selector 的 `cancelledKeys` 集合
3. **下次 `select()` 时**，Selector 才会从 `keys()` 和 `selectedKeys()` 中真正移除

### 时序问题

```
Worker 线程: select() 返回 2 个就绪 key
             key1 (READ)  → handleRead() → closeClient() → key1.cancel()
             key2 (WRITE) → 
             
             回到循环检查 key2:
             if (key2.isWritable())  // ← 此时 key2 可能已被其他线程取消！
```

在同一个 `select()` 返回的批次中：
1. 先处理 key1（READ），`handleRead()` 发现客户端断开，调用 `closeClient()` 取消 key1
2. 继续处理 key2（可能是同一个 channel 的另一个 key，或其他 channel）
3. 但 `key2.isWritable()` 检查时，如果 key2 对应的 channel 也在此时被关闭，就会抛异常

实际上更常见的情况是：**`key1` 和 `key2` 是同一个 key**，代码中先检查了 `isReadable()` 处理读事件，处理完读事件后 key 被 cancel，然后又检查 `isWritable()`。

### 问题代码

```java
while (iterator.hasNext()) {
    SelectionKey key = iterator.next();
    
    if (key.isReadable()) {      // ← 处理读事件
        handleRead(key);         //     内部可能调用 key.cancel()
    }
    
    if (key.isWritable()) {      // ← 如果 key 已被 cancel，这里抛异常！
        handleWrite(key);
    }
    
    iterator.remove();
}
```


## 正确理解

✅ **`key.cancel()` 是延迟生效的**，真正的移除发生在下次 `select()` 调用时
   - 这意味着当前 `select()` 返回的 `selectedKeys` 中，可能包含已被 cancel 的 key

✅ **任何对 SelectionKey 的操作前，必须先检查 `key.isValid()`**
   - `isValid()` 返回 false 表示 key 已被 cancel 或 channel 已关闭
   - 对 invalid key 调用 `isReadable()` / `isWritable()` / `interestOps()` 都会抛异常

✅ **一个 key 可能同时是 READABLE 和 WRITABLE**，处理完一个事件后状态可能改变


## 纠正过程

1. 查看堆栈跟踪，定位到 `isWritable()` 调用处
2. 分析时序：前一步的 `handleRead()` 调用了 `key.cancel()`
3. 在循环开头添加 `isValid()` 检查
4. 在每个 `isXxx()` 检查前再加一层 `isValid()` 保护

```java
// 修复后
while (iterator.hasNext()) {
    SelectionKey key = iterator.next();
    
    if (!key.isValid()) {  // ← 第一道防线
        iterator.remove();
        continue;
    }
    
    if (key.isReadable()) {
        handleRead(key);
    }
    
    if (key.isValid() && key.isWritable()) {  // ← 第二道防线
        handleWrite(key);
    }
    
    iterator.remove();
}
```


## 关联知识

- [[NIO-Selector]] - 核心概念：`cancel()` 的延迟语义，`selectedKeys()` 的迭代机制
- [[NIO优雅关闭模式]] - 关联：如何安全地关闭 Channel 和取消 Key
- [[Socket-EOF-Semantics]] - 关联：`read() = -1` 触发 `closeClient()`，进而触发 `key.cancel()`
- [[异常处理]] - 关联：NIO 中常见的运行时异常及防御性编程


## 预防措施

- [ ] Selector 循环中，**第一个操作永远是检查 `key.isValid()`**
- [ ] 在 `isReadable()` 和 `isWritable()` 之间，如果可能修改 key 状态，要重新检查 `isValid()`
- [ ] `handleRead()` 内部如果调用了 `key.cancel()`，调用方要知道 key 可能已失效
- [ ] 编写 NIO 代码时，养成"操作 key 前先 isValid()"的条件反射


## 类似错误

- 暂无历史相似错误


---

## 🤖 AI评价

### 错误类型
- 类型：API 语义误解 + 防御性编程缺失
- 严重程度：中（不影响主功能，但导致线程异常退出）
- 复发风险：高（NIO 编程中极易遗漏）

### 对掌握度的影响
- [[NIO-Selector]]: -5分 (发现对 cancel() 延迟语义理解不足)
- [[NIO-Selector]]: +15分 (深刻理解 selectedKeys 的迭代机制和 key 的生命周期)
- [[异常处理]]: +10分 (养成防御性编程习惯)

### 模式识别
- 是否与历史错误相似：否

### 针对性建议
1. 为 Selector 循环编写一个安全的处理模板，确保包含 isValid() 检查
2. 理解 `cancel()` 和 `select()` 的交互：cancel 是"请求"，select 是"执行"
3. 在代码审查清单中添加 NIO Key 有效性检查项

---

## 📊 错误模式统计

```dataview
TABLE status, created, related_concepts
FROM #mistake
SORT created DESC
```
