# 线程池设计思想

> 基于你已掌握的并发知识，深入理解线程池的设计原理和实践应用

## 核心概念

### 线程池的本质
- **资源池化**：管理和复用线程资源
- **任务调度**：合理分配任务到线程
- **流量控制**：通过队列和拒绝策略控制并发度
- **生命周期管理**：线程的创建、执行和回收

### 核心参数

| 参数 | 含义 | 设计要点 |
|------|------|----------|
| corePoolSize | 核心线程数 | 保持活跃的最小线程数 |
| maximumPoolSize | 最大线程数 | 可扩展的最大线程数 |
| keepAliveTime | 线程存活时间 | 非核心线程的空闲回收时间 |
| workQueue | 工作队列 | 任务缓冲，决定排队策略 |
| threadFactory | 线程工厂 | 自定义线程创建（命名、优先级） |
| handler | 拒绝策略 | 任务队列满时的处理策略 |

## 设计思想

### 1. 线程复用机制

**为什么需要线程复用？**
- 线程创建和销毁的开销大（系统调用）
- 线程上下文切换成本高
- 避免线程数量失控（资源耗尽）

**实现原理**：
```java
// 工作线程的run方法
public void run() {
    Runnable task = firstTask;
    firstTask = null;
    
    // 循环获取任务
    while (task != null || (task = getTask()) != null) {
        runTask(task);
        task = null;
    }
    
    // 线程退出
    processWorkerExit(this, false);
}
```

### 2. 任务执行流程

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

### 3. 拒绝策略

| 策略 | 行为 | 适用场景 |
|------|------|----------|
| AbortPolicy | 抛出RejectedExecutionException | 关键任务，需要明确失败 |
| CallerRunsPolicy | 调用者线程执行 | 低负载，需要保证任务执行 |
| DiscardPolicy | 静默丢弃 | 非关键任务，允许丢失 |
| DiscardOldestPolicy | 丢弃最旧的任务 | 时间敏感任务，优先执行新任务 |

### 4. 线程池状态管理

```
RUNNING: 接受新任务，处理队列任务
SHUTDOWN: 不接受新任务，处理队列任务
STOP: 不接受新任务，不处理队列任务，中断正在执行的任务
TIDYING: 所有任务已终止，工作线程数为0，准备执行terminated()
TERMINATED: 已终止
```

## 关联知识

### 与并发工具的关联

| 工具类 | 在线程池中的应用 |
|--------|------------------|
| BlockingQueue | 作为工作队列，提供线程安全的任务存储 |
| CountDownLatch | 用于等待线程池初始化完成 |
| Semaphore | 用于控制线程池的并发度 |
| CompletableFuture | 与线程池结合实现异步任务链 |

### 与NIO的关联

- **线程池**：处理计算密集型任务
- **Selector**：处理IO密集型任务
- **Reactor模式**：结合两者，主从线程池架构

## 常见误区

### 1. 线程池参数调优

**错误观点**：线程池越大越好

**正确做法**：
- CPU密集型：线程数 ≈ CPU核心数 + 1
- IO密集型：线程数 ≈ CPU核心数 × 2
- 混合任务：根据IO等待时间调整

### 2. 任务队列选择

**错误观点**：队列越大越好

**正确做法**：
- 无界队列：适合任务量稳定，防止任务丢失
- 有界队列：适合流量波动大，防止内存溢出

### 3. 线程池关闭

**错误观点**：直接调用shutdownNow()

**正确做法**：
- shutdown()：优雅关闭，等待任务完成
- shutdownNow()：强制关闭，可能中断正在执行的任务

## 最佳实践

### 1. 自定义线程池

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    corePoolSize,
    maximumPoolSize,
    keepAliveTime,
    TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(queueCapacity),
    new NamedThreadFactory("business-pool"),
    new CustomRejectPolicy()
);
```

### 2. 监控与调优

**关键指标**：
- 活跃线程数
- 队列大小
- 任务执行时间
- 拒绝率

**监控工具**：
- JMX
- Micrometer + Prometheus
- 自定义监控指标

### 3. 线程池隔离

**按业务类型隔离**：
- 核心业务线程池
- 非核心业务线程池
- 定时任务线程池
- IO密集型任务线程池

## 代码实现参考

### SimpleThreadPool核心实现

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
    
    // 执行任务
    public void execute(Runnable task) {
        if (task == null) {
            throw new NullPointerException();
        }
        
        int currentWorkers = workers.size();
        
        // 1. 核心线程数未满，创建核心线程
        if (currentWorkers < corePoolSize) {
            if (addWorker(task, true)) {
                return;
            }
        }
        
        // 2. 核心线程满，尝试入队
        if (state == RUNNING && workQueue.offer(task)) {
            // 入队成功，双重检查
            if (state != RUNNING || currentWorkers == 0) {
                addWorker(null, false);
            }
        } else {
            // 3. 队列已满，尝试创建非核心线程
            if (!addWorker(task, false)) {
                // 4. 线程数达到上限，执行拒绝策略
                rejectPolicy.reject(task, this);
            }
        }
    }
    
    // 工作线程类
    private class Worker implements Runnable {
        private Thread thread;
        private Runnable firstTask;
        
        public Worker(Runnable firstTask) {
            this.firstTask = firstTask;
        }
        
        @Override
        public void run() {
            runWorker(this);
        }
    }
    
    // 运行工作线程
    private void runWorker(Worker worker) {
        Runnable task = worker.firstTask;
        worker.firstTask = null;
        
        while (task != null || (task = getTask()) != null) {
            try {
                task.run();
            } catch (Exception e) {
                // 异常处理
            } finally {
                task = null;
            }
        }
        
        removeWorker(worker);
    }
    
    // 获取任务（支持超时）
    private Runnable getTask() {
        boolean timed = workers.size() > corePoolSize;
        
        try {
            if (timed) {
                return workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS);
            } else {
                return workQueue.take();
            }
        } catch (InterruptedException e) {
            return null;
        }
    }
}
```

## 学习产出

1. **可复用组件**：
   - SimpleThreadPool.java - 带监控的简易线程池
   - ThreadPoolBuilder.java - 线程池构建器
   - ThreadPoolMonitor.java - 线程池监控工具

2. **最佳实践文档**：
   - 线程池参数调优指南
   - 线程池隔离策略
   - 线程池监控方案

## 下一步

- 实践：使用线程池实现一个高并发的Web服务器
- 深入：分析ThreadPoolExecutor源码
- 扩展：学习ForkJoinPool的工作窃取算法