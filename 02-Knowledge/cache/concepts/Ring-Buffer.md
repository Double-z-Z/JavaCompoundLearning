---
type: atomic-note
id: CONCEPT-ring-buffer
created: 2026-05-02
tags: [cache, 并发, lock-free]
mastery: 50
source: "[[03-Practice/reflections/2026-05-02-本地缓存-Caffeine-对话.md]]"
related_emrg: [EMRG-Cache]
related_goal: [GOAL-ORM与缓存]
---

# Ring Buffer 无锁批量处理

## 一句话定义
Ring Buffer 是一种环形队列结构，通过 CAS 实现多生产者无锁写入，单消费者批量读取，用于解耦高频写操作与低频处理操作。


## 核心理解

### 结构
```
┌─────────────────────────────────────┐
│  0    │  1    │  2    │  3    │  4   │  ...  │  N   │
│ empty │ key:A │ key:B │ empty │ key:C│       │      │
└─────────────────────────────────────┘
         ↑                    ↑
       writeCursor         readCursor
```

### 并发模型
- **多生产者**：多线程 CAS 竞争 writeCursor
- **单消费者**：后台线程顺序读取，无竞争

### 批量处理
```
后台线程：
    1. 读取 readCursor 到 writeCursor 之间的所有 key
    2. 去重（同一 key 多次访问只计一次）
    3. 批量更新 TinyLFU 计数器
    4. 移动 readCursor
```

### 背压处理
| 场景 | 策略 | 原因 |
|-----|------|------|
| 写入 > 读取 | 丢弃新记录 | 频率统计是近似值，丢失可接受 |
| 持续高压力 | 动态扩容 Stripe | 增加 Ring Buffer 数量和处理线程 |


## 关键关联

- [[NIO-Selector]] - 关联原因：同样的解耦思想，Selector 批量检测就绪事件，Ring Buffer 批量处理访问记录
- [[LongAdder]] - 关联原因：都是分段降低竞争，LongAdder 分段计数，Ring Buffer 分段队列
- [[任务队列]] - 关联原因：NIO 聊天室的任务队列也是生产者-消费者模型，但 Ring Buffer 强调无锁和批量


## 我的误区与疑问

- ❌ 误区：曾表述为"单生产者"，实际上多线程写入需要 CAS，是多生产者
- ❌ 误区：曾认为"单消费不需要屏障"，实际上需要 volatile 保证可见性
- ❓ 疑问：批量去重的具体实现？是简单的 HashSet 还是有更高效的方案？


## 代码与实践

```java
// Caffeine 内部实现示意（简化）
class RingBuffer {
    private final Object[] buffer;
    private volatile long writeCursor;
    private long readCursor;
    
    // 多生产者写入
    boolean offer(Object key) {
        long slot = casWriteCursor(+1);
        if (slot >= buffer.length) {
            return false; // 满，丢弃
        }
        buffer[(int)(slot % buffer.length)] = key;
        return true;
    }
    
    // 单消费者批量读取
    void drain() {
        long write = writeCursor;
        while (readCursor < write) {
            Object key = buffer[(int)(readCursor % buffer.length)];
            process(key);
            readCursor++;
        }
    }
}
```


## 深入思考

💡 Ring Buffer 的批量大小（一次处理多少条）如何确定？太大延迟高，太小吞吐量低。

💡 如果消费者处理速度长期跟不上生产者，除了丢弃，是否有更优雅的降级策略？


## 来源
- 项目：[[netty-chatroom]]（任务队列设计可借鉴 Ring Buffer 思想）
- 对话：[[2026-05-02-本地缓存-Caffeine-对话]]


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-05-02: mastery=50 (理解无锁设计、批量处理、背压策略，但具体实现细节需验证)

### 建议下一步
1. 阅读 Caffeine 的 BoundedLocalCache 源码
2. 实现一个简单的无锁 Ring Buffer 验证 CAS 行为
3. 对比 Disruptor 的 Ring Buffer 实现差异

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #cache OR #并发 OR #lock-free
SORT mastery DESC
```
