---
created: 2026-04-26
tags: [drill, netty, 聊天室]
difficulty: 🌿
related_concepts:
  - [[Netty]]
  - [[ConcurrentLinkedQueue]]
  - [[消息存储]]
---

# Netty 聊天室 Phase 2 - 消息持久化实现

> 🎯 目标：实现服务端消息持久化功能，支持新用户获取最近100条历史消息


## 练习内容

### 题目/需求
为 Netty 聊天室添加消息持久化功能：
1. 服务端保存最近100条广播消息
2. 新用户连接后可以请求历史消息
3. 优化存储查询性能，减少不必要的复制


### 我的实现

#### 1. 消息存储服务（MessageStore）
```java
public class MessageStore {
    private static final int MAX_SIZE = 100;
    private final ConcurrentLinkedQueue<Message> messages = new ConcurrentLinkedQueue<>();
    
    public void saveMessage(Message message) {
        messages.offer(message);
        while (messages.size() > MAX_SIZE) {
            messages.poll();  // 移除最旧的消息
        }
    }
    
    public List<Message> getRecentMessages(int count) {
        // 优化：只遍历需要的部分，避免全量复制
        int actualCount = Math.min(count, messages.size());
        List<Message> result = new ArrayList<>(actualCount);
        
        int skip = messages.size() - actualCount;
        int index = 0;
        for (Message msg : messages) {
            if (index >= skip) {
                result.add(msg);
            }
            index++;
        }
        
        Collections.reverse(result);  // 从新到旧排序
        return result;
    }
}
```

#### 2. 历史消息协议
```java
// 请求
public class HistoryRequestMessage extends Message {
    private int count;
}

// 响应
public class HistoryResponseMessage extends Message {
    private List<Message> messages;
    private int totalCount;
}
```

#### 3. 服务端集成
- `MessageService`：保存广播消息，处理历史消息请求
- `ChatHandler`：路由历史消息请求到服务层

#### 4. 客户端支持
```java
// ChatClient 新增方法
public void requestHistory(int count) {
    HistoryRequestMessage message = new HistoryRequestMessage(count);
    channel.writeAndFlush(message);
}
```


### 遇到的困难
- **问题1**：`getRecentMessages` 初始实现使用了 `new ArrayList<>(messages)`，导致全量复制
  - 解决：改为遍历跳过前面不需要的元素，只复制需要的部分
  
- **问题2**：测试客户端的统计指标设计争议
  - 结论：客户端统计"进入 Netty 缓冲区的吞吐量"意义不明确，删除统计功能
  - 真实延迟测试应采用多客户端日志交叉分析


## 验证与测试

### 测试方案
1. 单元测试：`MessageStoreTest` 验证存储和查询逻辑
2. 集成测试：多个客户端连接，发送消息，新客户端获取历史消息

### 测试结果
- ✅ `MessageStore` 线程安全测试通过
- ✅ 历史消息请求/响应协议测试通过
- ✅ 边界情况（空存储、请求数量超过存储量）处理正确


## 复盘总结

### 学到的
1. **ConcurrentLinkedQueue 的遍历优化**：避免 `toArray` 和全量复制，使用迭代器跳过不需要的元素
2. **协议设计**：请求/响应模式的历史消息获取
3. **测试客户端的定位**：功能验证 > 性能统计，性能测试应使用专业工具

### 关联知识
- [[ConcurrentLinkedQueue]] - 应用场景：线程安全的循环缓冲区实现
- [[Netty]] - 应用场景：协议编解码、Handler 链设计
- [[消息存储]] - 设计权衡：内存存储 vs 持久化存储

### 待深化
- 消息存储的持久化（嵌入式数据库 H2/SQLite）
- 消息的分页查询（游标机制）


---

## 🤖 AI评价

### 完成质量
- 功能实现：完整 ✅
- 代码质量：良好（经过一轮性能优化）
- 概念应用：正确 ✅

### 对掌握度的影响
- [[ConcurrentLinkedQueue]]: +15分 (正确应用，理解遍历优化)
- [[Netty]]: +10分 (协议设计，Handler 扩展)
- [[消息存储]]: +10分 (理解 FIFO 缓冲区设计)

### 建议
1. 后续可学习嵌入式数据库集成，实现真正的持久化
2. 探索 Netty 的内存池机制，优化 ByteBuf 使用


---

## 📊 相关练习统计

```dataview
TABLE difficulty, created, related_concepts
FROM #drill AND #netty
SORT created DESC
```
