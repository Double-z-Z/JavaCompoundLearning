# 并发编程学习路线

> 基于你已掌握的JMM和多线程知识，通过测试项目巩固并深化理解

## 前置知识检查清单

- [x] Java内存模型（JMM）- happens-before、内存屏障、volatile
- [x] Java多线程编程 - Thread、Runnable、线程池、锁机制
- [x] 并发工具类 - CountDownLatch、CyclicBarrier、Semaphore、CompletableFuture
- [x] 并发集合 - ConcurrentHashMap、CopyOnWriteArrayList、BlockingQueue

***

## 测试项目1：高并发计数器系统

### 目标

验证你对volatile、原子类、锁的理解，以及CAS原理

### 需求

1. 实现线程安全的计数器，支持递增、获取、重置
2. 性能要求：10个线程各递增100万次，总时间 < 1秒
3. 对比不同实现方案的性能差异

### 实现方案对比

#### 方案1：synchronized

```java
public class SynchronizedCounter {
    private long count = 0;
    
    public synchronized void increment() {
        count++;
    }
    
    public synchronized long getCount() {
        return count;
    }
}
```

**问题**：锁粒度大，性能较差

#### 方案2：AtomicLong（推荐）

```java
public class AtomicCounter {
    private AtomicLong count = new AtomicLong(0);
    
    public void increment() {
        count.incrementAndGet();
    }
    
    public long getCount() {
        return count.get();
    }
}
```

**优势**：CAS无锁，性能高

#### 方案3：分段计数器（进阶）

```java
public class StripedCounter {
    private final int stripes;
    private final AtomicLong[] counters;
    
    // 类似ConcurrentHashMap的分段锁思想
    // 每个线程操作不同的counter，最后汇总
}
```

### 知识点深挖

1. **CAS原理**：Unsafe.compareAndSwapLong
2. **ABA问题**：AtomicStampedReference解决方案
3. **伪共享**：@Contended注解

***

## 测试项目2：生产者消费者队列

### 目标

验证你对BlockingQueue、线程池的理解

### 需求

1. 3个生产者线程，每秒生产10个任务
2. 5个消费者线程，处理任务（模拟耗时100ms）
3. 任务队列大小限制为100
4. 支持优雅关闭

### 核心代码框架

```java
public class TaskScheduler {
    private final BlockingQueue<Task> queue;
    private final ExecutorService producerPool;
    private final ExecutorService consumerPool;
    private volatile boolean running = true;
    
    public TaskScheduler(int queueCapacity, int producerCount, int consumerCount) {
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.producerPool = Executors.newFixedThreadPool(producerCount);
        this.consumerPool = Executors.newFixedThreadPool(consumerCount);
    }
    
    public void start() {
        // 启动生产者和消费者
    }
    
    public void shutdown() {
        // 优雅关闭
        running = false;
        producerPool.shutdown();
        consumerPool.shutdown();
    }
}
```

### 进阶挑战

1. **优先级队列**：PriorityBlockingQueue
2. **动态调整**：根据队列积压情况动态增减消费者
3. **监控指标**：队列大小、处理速率、延迟

***

## 测试项目3：简易线程池实现

### 目标

深入理解线程池核心参数和工作原理

### 需求

1. 支持核心线程数和最大线程数配置
2. 有界任务队列
3. 拒绝策略（抛出异常/丢弃/调用者运行）
4. 线程空闲回收机制

### 核心架构

```java
public class SimpleThreadPool {
    // 核心参数
    private final int corePoolSize;
    private final int maximumPoolSize;
    private final long keepAliveTime;
    private final BlockingQueue<Runnable> workQueue;
    private final RejectPolicy rejectPolicy;
    
    // 工作线程集合
    private final Set<Worker> workers = new HashSet<>();
    
    // 线程池状态
    private volatile int state = RUNNING;
    
    private class Worker implements Runnable {
        private Thread thread;
        private Runnable firstTask;
        
        public void run() {
            // 循环获取任务并执行
        }
    }
}
```

### 执行流程

```
submit(task) 
  ↓
当前线程数 < corePoolSize ? 
  ↓ 是
创建核心线程执行任务
  ↓ 否
任务队列未满 ?
  ↓ 是
加入任务队列
  ↓ 否
当前线程数 < maximumPoolSize ?
  ↓ 是
创建非核心线程执行任务
  ↓ 否
执行拒绝策略
```

### 进阶挑战

1. **定时任务**：支持延迟执行和周期执行
2. **状态监控**：活跃线程数、队列大小、已完成任务数
3. **优雅关闭**：shutdown() vs shutdownNow()

***

## 知识关联图

```
并发编程
├── JMM
│   ├── happens-before → volatile/synchronized的内存语义
│   └── 内存屏障 → CAS的底层实现
├── 锁机制
│   ├── synchronized → 对象头、Monitor
│   ├── Lock → AQS、CLH队列
│   └── 读写锁 → 并发读性能优化
├── 原子类
│   ├── CAS → Unsafe、自旋
│   ├── 原子引用 → ABA问题
│   └── LongAdder → 分段累加
├── 线程池
│   ├── 参数调优 → 根据场景配置
│   ├── 拒绝策略 → 自定义处理
│   └── 监控 → 动态调整
└── 并发集合
    ├── ConcurrentHashMap → 分段锁→CAS
    ├── BlockingQueue → 生产者消费者
    └── CopyOnWriteArrayList → 读多写少
```

***

## 学习产出

完成测试后，整理以下可复用组件到 `src/reusable/`：

1. **ConcurrentCounter.java** - 高并发计数器（支持多种实现）
2. **TaskScheduler.java** - 任务调度器（支持优先级和动态调整）
3. **SimpleThreadPool.java** - 简易线程池（带监控功能）

***

## 测试通过标准

1. ✅ 功能正确：所有测试用例通过，无并发问题
2. ✅ 性能达标：满足各项目的性能要求
3. ✅ 代码质量：良好的注释、异常处理、资源释放
4. ✅ 知识讲解：能清晰解释实现原理和设计选择

***

## 下一步

完成并发测试后，进入 [NIO学习路线](../nio/nio-learning-path.md)
