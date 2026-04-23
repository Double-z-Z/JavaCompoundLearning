---
created: 2026-04-19
tags: [drill, NIO, 广播, 聊天室]
difficulty: 🌿
related_concepts:
  - [[NIO-Selector]]
  - [[ConcurrentLinkedQueue]]
  - [[OP_WRITE事件处理]]
  - [[跨线程通信]]
---

# NIO聊天室广播功能实现

> 🎯 目标：实现聊天室的消息广播功能，支持跨Worker的消息转发


## 练习内容

### 需求
实现NIO聊天室的消息广播功能：
1. 一个客户端发送的消息能被所有其他客户端收到
2. 支持跨Worker的消息转发
3. 处理写缓冲区满的情况
4. 不回传消息给发送者


### 我的实现

#### 核心设计
```
消息流转路径：
Client A (Worker 1) 发送消息
    ↓
Worker 1 读取消息
    ↓
Worker 1.messageQueue.offer()  ← 入队自己的队列
    ↓
ClientManager.broadcast() 转发给其他Worker
    ↓
Worker 2, 3 收到消息并入队
    ↓
所有Worker的 processBroadcastMessages() 消费队列
    ↓
broadcastToAll() 发送给管理的所有Channel（排除来源）
```

#### 关键代码

**ServerHandler - 消息队列处理**
```java
public class ServerHandler {
    // Worker内消息队列
    private ConcurrentLinkedQueue<ChatMessage> messageQueue = new ConcurrentLinkedQueue<>();
    
    // 每个Channel的待发送队列
    private Map<SocketChannel, ClientContext> clientContextMap = new ConcurrentHashMap<>();
    
    // 处理广播消息队列
    private void processBroadcastMessages() {
        ChatMessage message;
        while ((message = messageQueue.poll()) != null) {
            broadcastToAll(message);
        }
    }
    
    // 广播到所有客户端
    private void broadcastToAll(ChatMessage message) {
        byte[] messageBytes = message.getMessage().getBytes(StandardCharsets.UTF_8);
        
        for (SocketChannel channel : channels) {
            // 不回传给来源
            if (channel.equals(message.getSource())) continue;
            
            ClientContext context = clientContextMap.get(channel);
            
            // 尝试直接发送
            if (context.pendingMessages.isEmpty()) {
                int written = channel.write(writeBuffer);
                if (written < messageBytes.length) {
                    // 缓冲区满，剩余入队
                    context.pendingMessages.offer(remaining);
                    // 注册OP_WRITE
                    channel.register(selector, OP_READ | OP_WRITE);
                }
            } else {
                // 有待发送消息，入队
                context.pendingMessages.offer(messageBytes);
            }
        }
    }
}
```

**ClientContext - 客户端上下文**
```java
public class ClientContext {
    // 待发送消息队列（LinkedList支持队首插入）
    public LinkedList<byte[]> pendingMessages = new LinkedList<>();
    public int lastWriteIndex;
}
```

**写事件处理**
```java
private void handleWrite(SelectionKey key) throws IOException {
    SocketChannel channel = (SocketChannel) key.channel();
    ClientContext context = clientContextMap.get(channel);
    
    // 发送待发送队列中的消息
    byte[] message;
    while ((message = context.pendingMessages.peek()) != null) {
        ByteBuffer writeBuffer = ByteBuffer.wrap(message);
        int written = channel.write(writeBuffer);
        
        if (written == message.length) {
            context.pendingMessages.poll();  // 发送完成
        } else if (written > 0) {
            // 部分发送，剩余放回队首
            context.pendingMessages.poll();
            byte[] remaining = new byte[message.length - written];
            System.arraycopy(message, written, remaining, 0, remaining.length);
            context.pendingMessages.offerFirst(remaining);
            break;
        } else {
            break;  // 缓冲区满
        }
    }
    
    // 队列为空，取消OP_WRITE
    if (context.pendingMessages.isEmpty()) {
        key.interestOps(SelectionKey.OP_READ);
    }
}
```


## 验证与测试

### 测试方案
1. **BroadcastTest** - 功能测试
   - testBasicBroadcast: 基础广播功能
   - testConcurrentBroadcast: 并发广播
   - testCrossWorkerBroadcast: 跨Worker广播

2. **BroadcastPerformanceTest** - 性能测试
   - 测试N^2消息复杂度的性能
   - 内存使用监控
   - 吞吐量测量

### 测试结果
- 基础功能测试：✅ 通过（修复4个连锁Bug后）
  - BroadcastTest: 5客户端，广播消息4个非发送方都收到
- 性能测试：✅ 通过
  - 10客户端 × 50消息 = 4500条广播，0丢失
  - 吞吐量：2142条/秒
  - 耗时：2100ms


## 复盘总结

### 学到的
1. **OP_WRITE事件管理**
   - 只在有待发送数据时注册OP_WRITE
   - 发送完成后取消OP_WRITE，避免空轮询
   - 这是NIO编程的关键模式

2. **跨Worker广播设计**
   - 每个Worker维护自己的消息队列
   - ClientManager负责Worker间转发
   - 消息不回传给来源客户端

3. **写半包处理**
   - write()可能只发送部分数据
   - 剩余数据需要缓存并注册OP_WRITE
   - 下次可写时继续发送
   - `wrap()`创建的Buffer已是读模式，不需要flip()

4. **并发集合选择**
   - `channels`用`CopyOnWriteArrayList`（读多写少，遍历安全）
   - `pendingMessages`用`LinkedList`（需要`offerFirst()`）
   - 选择数据结构要考虑**语义+操作+线程安全**

5. **NIO调试实战**
   - 竞态条件通过DEBUG日志定位（时序图分析）
   - `key.cancel()`是延迟生效的，操作前必须`isValid()`
   - `buffer.compact()`保留未读数据，`clear()`会覆盖

### 修复的Bug清单

| Bug | 根因 | 修复 |
|-----|------|------|
| 客户端静默丢失 | `register()`中`isRunning`guard与异步启动竞态 | 移除guard |
| tearDown异常 | `CopyOnWriteArrayList`不支持迭代器remove | 改用`channels.clear()` |
| CancelledKeyException | `handleRead()`cancel key后仍检查`isWritable()` | 添加`isValid()`检查 |
| Buffer数据错位 | `wrap()`后再`flip()`导致limit=0 | 移除多余flip() |
| 消息丢失 | `clear()`覆盖未处理数据 | 改用`compact()` |
| 多连接丢失 | `accept()`只调用一次 | 改为`while`循环 |

### 关联知识
- [[NIO-Selector]] - Selector的select()和wakeup()机制
- [[ConcurrentLinkedQueue]] - 无锁队列的实现原理
- [[OP_WRITE事件处理]] - 写事件的注册和取消时机
- [[跨线程通信]] - 使用队列进行线程间通信

### 待深化
- [ ] 共享队列方案的实现和性能对比
- [ ] 消息协议的序列化/反序列化
- [ ] 背压策略的设计


---

## 🤖 AI评价

### 完成质量
- 功能实现：完整 ✅
- 代码质量：良好
- 概念应用：正确 ✅

### 对掌握度的影响
- [[NIO-Selector]]: +15分 (正确应用OP_WRITE管理)
- [[ConcurrentLinkedQueue]]: +10分 (理解队列选择)
- [[跨线程通信]]: +15分 (实现Worker间广播)

### 建议
1. 完成测试验证，确保功能正确
2. 运行性能测试获取基准数据
3. 实现共享队列方案进行对比


---

```dataview
TABLE difficulty, created, related_concepts
FROM #drill AND #NIO
SORT created DESC
```
