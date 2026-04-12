# CAS库与高性能原子操作

> 基于你已掌握的并发编程知识，深入了解CAS原理和高性能原子操作库

## 核心概念

### CAS（Compare-And-Swap）原理

**基本思想**：
1. 读取内存位置的值（V）
2. 计算新值（N）
3. 比较内存位置的当前值是否还是V
4. 如果是，将内存位置的值更新为N
5. 如果不是，说明有其他线程修改过，重试整个过程

**底层实现**：
- x86架构：`cmpxchg`指令
- ARM架构：`ldrex/strex`指令
- Java：`sun.misc.Unsafe`类

## JDK标准库

### 1. Atomic系列

#### 核心类

| 类 | 功能 | 适用场景 |
|----|------|----------|
| AtomicBoolean | 原子布尔值 | 开关、标记位 |
| AtomicInteger | 原子整数 | 计数器、ID生成 |
| AtomicLong | 原子长整型 | 大计数器、时间戳 |
| AtomicReference | 原子引用 | 线程安全的对象引用 |
| AtomicStampedReference | 带版本戳的原子引用 | 解决ABA问题 |
| AtomicMarkableReference | 带标记的原子引用 | 简化版本的ABA解决方案 |

#### 使用示例

```java
// 基本用法
AtomicLong counter = new AtomicLong(0);
counter.incrementAndGet(); // 原子递增

// 复杂操作
AtomicReference<User> ref = new AtomicReference<>(new User("张三", 18));

ref.updateAndGet(user -> {
    User newUser = new User(user.getName(), user.getAge() + 1);
    return newUser;
});

// 解决ABA问题
AtomicStampedReference<String> stampedRef = new AtomicStampedReference<>("A", 0);

int[] stampHolder = new int[1];
String current = stampedRef.get(stampHolder);
int stamp = stampHolder[0];

// 只有当前值和版本戳都匹配时才更新
boolean success = stampedRef.compareAndSet(current, "B", stamp, stamp + 1);
```

### 2. LongAdder

**设计思想**：
- 分段累加：将计数器拆分为多个Cell
- 线程本地热点：每个线程操作自己的Cell
- 最终汇总：需要获取值时累加所有Cell

**优势**：
- 高并发下性能优于AtomicLong（减少CAS竞争）
- 写操作吞吐量高

**适用场景**：
- 高并发计数器
- 统计指标
- 性能监控

```java
LongAdder counter = new LongAdder();

// 多线程并发递增
counter.increment();

// 获取最终结果
long result = counter.sum();
```

### 3. LongAccumulator

**功能**：
- 支持自定义累加器函数
- 更灵活的原子操作

**适用场景**：
- 自定义聚合操作
- 复杂的状态更新

```java
// 计算最大值
LongAccumulator maxAccumulator = new LongAccumulator(
    Math::max, // 自定义累加函数
    Long.MIN_VALUE // 初始值
);

maxAccumulator.accumulate(100);
maxAccumulator.accumulate(200);
long max = maxAccumulator.get(); // 200
```

## 生产中常用的CAS库

### 1. JCTools（Java Concurrency Tools）

**特点**：
- 高性能并发数据结构
- 无锁设计
- 针对不同场景的队列实现
- 被许多开源项目采用（如Netty、RxJava）

**核心组件**：

| 组件 | 功能 | 适用场景 |
|------|------|----------|
| SpscArrayQueue | 单生产者单消费者队列 | 线程间通信 |
| MpscArrayQueue | 多生产者单消费者队列 | 事件分发 |
| MpmcArrayQueue | 多生产者多消费者队列 | 工作队列 |
| SpscLinkedQueue | 单生产者单消费者链表队列 | 无界场景 |

**使用示例**：

```java
// 单生产者单消费者队列
Queue<String> queue = new SpscArrayQueue<>(1024);

// 生产者线程
queue.offer("任务1");

// 消费者线程
String task = queue.poll();
```

**性能优势**：
- 无锁设计，减少线程竞争
- 缓存友好的数据结构
- 针对不同生产者/消费者模式优化

### 2. Disruptor

**特点**：
- 高性能环形缓冲区
- 无锁设计
- 可预测的低延迟
- 广泛应用于金融交易系统

**核心概念**：

| 概念 | 说明 |
|------|------|
| Ring Buffer | 环形缓冲区，存储事件 |
| Sequence | 序列号，追踪事件位置 |
| Event | 事件对象 |
| EventHandler | 事件处理器 |
| Producer | 生产者 |
| Consumer | 消费者 |
| Wait Strategy | 等待策略 |

**使用示例**：

```java
// 1. 定义事件
class ValueEvent {
    private long value;
    public void set(long value) { this.value = value; }
    public long get() { return value; }
}

// 2. 创建事件工厂
EventFactory<ValueEvent> eventFactory = ValueEvent::new;

// 3. 设置环形缓冲区大小（必须是2的幂）
int bufferSize = 1024;

// 4. 创建Disruptor
Disruptor<ValueEvent> disruptor = new Disruptor<>(
    eventFactory,
    bufferSize,
    DaemonThreadFactory.INSTANCE
);

// 5. 注册事件处理器
disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
    System.out.println("处理事件: " + event.get());
});

// 6. 启动Disruptor
disruptor.start();

// 7. 获取环形缓冲区
RingBuffer<ValueEvent> ringBuffer = disruptor.getRingBuffer();

// 8. 发布事件
long sequence = ringBuffer.next();
try {
    ValueEvent event = ringBuffer.get(sequence);
    event.set(42);
} finally {
    ringBuffer.publish(sequence);
}

// 9. 关闭Disruptor
disruptor.shutdown();
```

**等待策略**：

| 策略 | 特点 | 适用场景 |
|------|------|----------|
| BlockingWaitStrategy | 阻塞等待，CPU使用率低 | 低延迟要求，高吞吐量 |
| BusySpinWaitStrategy | 自旋等待，CPU使用率高 | 极低延迟要求 |
| YieldingWaitStrategy | 线程让步，平衡CPU和延迟 | 中低延迟要求 |
| SleepingWaitStrategy | 睡眠等待，CPU使用率最低 | 对延迟不敏感 |

**性能优势**：
- 无锁设计，避免上下文切换
- 环形缓冲区，缓存友好
- 批处理机制，减少事件处理开销
- 可预测的内存访问模式

## 选型指南

### 决策树

```
┌─────────────────────────┐
│ 选择CAS库              │
└──────────────┬──────────┘
               ↓
┌─────────────────────────┐
│ 简单原子操作           │
└──────────────┬──────────┘
               ↓
       ┌───────┴───────┐
       ↓               ↓
┌────────────┐    ┌────────────┐
│ 低并发     │    │ 高并发     │
└────┬───────┘    └────┬───────┘
     ↓                 ↓
┌────────────┐    ┌────────────┐
│ Atomic*    │    │ LongAdder  │
└────────────┘    └────────────┘


┌─────────────────────────┐
│ 并发队列需求           │
└──────────────┬──────────┘
               ↓
       ┌───────┴───────┐
       ↓               ↓
┌────────────┐    ┌────────────┐
│ 标准库     │    │ JCTools   │
└────┬───────┘    └────┬───────┘
     ↓                 ↓
┌────────────┐    ┌────────────┐
│ Blocking   │    │ 无锁队列   │
│ Queue      │    │            │
└────────────┘    └────────────┘


┌─────────────────────────┐
│ 低延迟事件处理         │
└──────────────┬──────────┘
               ↓
┌─────────────────────────┐
│ Disruptor              │
└─────────────────────────┘
```

### 性能对比

| 场景 | 推荐方案 | 性能优势 |
|------|----------|----------|
| 低并发计数器 | AtomicLong | 简单易用 |
| 高并发计数器 | LongAdder | 减少CAS竞争 |
| 线程间通信 | JCTools SpscArrayQueue | 无锁设计，高吞吐量 |
| 事件处理 | Disruptor | 极低延迟，可预测性 |
| 自定义聚合 | LongAccumulator | 灵活的函数式接口 |

## 最佳实践

### 1. 避免ABA问题

**问题**：值从A变为B，又变回A，CAS操作无法检测到中间的变化

**解决方案**：
- 使用AtomicStampedReference
- 使用版本号或时间戳

### 2. 减少CAS竞争

**策略**：
- 使用分段技术（如LongAdder）
- 合理设计数据结构，减少热点竞争
- 使用局部变量缓存，减少内存访问

### 3. 内存屏障

**理解**：
- CAS操作会隐式插入内存屏障
- 保证操作的可见性和有序性

**影响**：
- 频繁的CAS操作可能影响性能
- 合理使用批处理减少CAS次数

### 4. 线程安全的单例模式

**使用CAS实现**：

```java
public class Singleton {
    private static final AtomicReference<Singleton> INSTANCE = new AtomicReference<>();
    
    private Singleton() {}
    
    public static Singleton getInstance() {
        while (true) {
            Singleton instance = INSTANCE.get();
            if (instance != null) {
                return instance;
            }
            
            instance = new Singleton();
            if (INSTANCE.compareAndSet(null, instance)) {
                return instance;
            }
        }
    }
}
```

## 学习产出

1. **可复用组件**：
   - HighPerformanceCounter.java - 结合Atomic和LongAdder的高性能计数器
   - ConcurrentEventQueue.java - 基于JCTools的事件队列
   - DisruptorEventBus.java - 基于Disruptor的事件总线

2. **性能测试**：
   - CounterPerformanceTest.java - 不同计数器实现的性能对比
   - QueuePerformanceTest.java - 不同队列实现的性能对比

3. **最佳实践文档**：
   - CAS使用指南
   - 无锁数据结构设计
   - 高并发场景优化策略

## 下一步

- 实践：使用JCTools实现一个高性能的消息队列
- 深入：分析Disruptor的源码实现
- 扩展：学习AQS（AbstractQueuedSynchronizer）的设计原理