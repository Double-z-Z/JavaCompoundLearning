# LongAdder 设计原理

> **标签**：#并发编程 #原子类 #分段锁 #CAS #性能优化  
> **难度**：中级  
> **前置知识**：[[CAS 原理]]、[[volatile 关键字]]、[[哈希函数]]  
> **关联知识**：[[AtomicLong]]、[[ConcurrentHashMap]]、[[线程阻塞机制]]

---

## 📖 核心概念

### **什么是 LongAdder？**

LongAdder 是 JDK8 引入的高并发计数器，通过**分段思想**将单个计数器拆分为多个分段，不同线程操作不同分段，从而减少 CAS 冲突，提高并发性能。

**适用场景**：
- ✅ 高并发写入（1000+ 线程）
- ✅ 写多读少（increment 多，get 少）
- ❌ 低并发场景（AtomicLong 更优）
- ❌ 需要精确实时值（LongAdder 允许短暂不一致）

---

## 🏗️ 设计思想

### **核心原则：分而治之**

**问题**：
```
AtomicLong：单 CAS 变量
    ↓
高并发下，所有线程竞争同一个 CAS
    ↓
大量 CAS 失败，自旋重试
    ↓
性能急剧下降
```

**解决方案**：
```
LongAdder：分段 CAS 数组
    ↓
线程哈希到不同分段
    ↓
各线程操作不同变量
    ↓
CAS 冲突概率大幅降低
```

**类比**：
> 银行柜台：
> - AtomicLong = 1 个窗口，所有人排一队
> - LongAdder = 多个窗口，客户分散办理

---

### **空间换时间的权衡**

| 维度 | AtomicLong | LongAdder | 权衡分析 |
|------|-----------|-----------|----------|
| **内存占用** | 1 个 long | n 个 long | LongAdder 占用更多 |
| **写性能** | 低并发优，高并发差 | 高并发优 | 分段减少冲突 |
| **读性能** | 直接返回 | 遍历累加 | LongAdder 稍慢 |
| **实时性** | 精确 | 允许短暂不一致 | LongAdder 非强一致 |

**设计哲学**：
> "用空间（内存）换时间（吞吐量）"
> "用一致性（短暂不一致）换性能"

---

## 🧩 核心组件

### **1. base 字段（低竞争路径）**

```java
public class LongAdder extends Striped64 {
    transient volatile long base;  // 基础计数器
    transient volatile Cell[] cells;  // 分段数组
}
```

**作用**：
- 低并发时直接使用，无需创建 Cell 数组
- 类似 AtomicLong，单 CAS 操作
- 减少内存开销和初始化成本

**写操作逻辑**：
```java
void add(long x) {
    Cell[] as; long b, v; int m; Cell a;
    // 第一次尝试：直接 CAS 更新 base
    if ((as = cells) != null || !casBase(b = base, b + x)) {
        // CAS 失败或 cells 已存在 → 进入 Cell 更新
        localAdd(x);
    }
}
```

---

### **2. Cell 数组（高竞争路径）**

```java
@sun.misc.Contended  // 关键！避免缓存行伪共享
static final class Cell {
    volatile long value;  // 分段计数值
    
    Cell(long x) { value = x; }
    
    final boolean cas(long cmp, long val) {
        return UNSAFE.compareAndSwapLong(this, valueOffset, cmp, val);
    }
}
```

**关键设计**：
- `@Contended` 注解：每个 Cell 独占缓存行（64 字节）
- 避免**缓存行伪共享**（False Sharing）
- 提高多核 CPU 下的性能

**什么是缓存行伪共享？**
```
无 @Contended：
CPU0: Cell[0].value ←┐
CPU1: Cell[1].value ←┤ 同一缓存行
    ↓
CPU0 修改 Cell[0].value
    ↓
整个缓存行失效
    ↓
CPU1 的 Cell[1].value 也要重新加载（性能下降）

有 @Contended：
CPU0: [Cell[0].value + 填充]  ← 独占缓存行
CPU1: [Cell[1].value + 填充]  ← 独占缓存行
    ↓
互不干扰
```

---

### **3. 哈希机制**

#### **JDK LongAdder 的实现**

```java
// 使用 Thread 的 probe 字段
int index = getProbe() & (cells.length - 1);
```

**关键特性**：
- `getProbe()`：返回线程的伪随机探针值
- `& (n-1)`：位运算取模（要求 n 是 2 的幂）
- 分布均匀，避免线程集中

#### **你的实现对比**

```java
// 你的实现（LongAdderCounter.java）
long segmentIndex = thread.getId() % segmentCount;
```

**问题分析**：
1. **线程 ID 连续性**：线程 ID 通常连续（1, 2, 3, 4...）
2. **分布不均**：如果 `segmentCount = 4`
   - 线程 1, 5, 9 → 索引 1
   - 线程 2, 6, 10 → 索引 2
   - 导致某些分段过载
3. **取模运算慢**：除法指令比位运算慢 10 倍+

**优化建议**：
```java
// 改进方案 1：使用扰动函数（类似 HashMap）
int hash = threadId ^ (threadId >>> 16);
int index = hash & (segmentCount - 1);

// 改进方案 2：使用 ThreadLocalRandom
int index = ThreadLocalRandom.current().nextInt(segmentCount);

// 改进方案 3：添加 @Contended 注解
@sun.misc.Contended
static final class Cell {
    volatile long value;
}
```

---

## 📊 性能对比

### **预期性能特征**

| 场景 | AtomicLong | LongAdder | 原因分析 |
|------|-----------|-----------|----------|
| **低并发（10 线程）** | ⭐⭐⭐⭐ | ⭐⭐⭐ | AtomicLong 无分段开销 |
| **高并发（1000 线程）** | ⭐⭐ | ⭐⭐⭐⭐⭐ | LongAdder 分散冲突 |
| **写多读少（100:1）** | ⭐⭐ | ⭐⭐⭐⭐⭐ | CAS 冲突被分段缓解 |
| **写少读多（1:100）** | ⭐⭐⭐⭐ | ⭐⭐⭐ | LongAdder 读需遍历 |

### **性能测试代码框架**

```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class CounterBenchmark {
    
    @Param({"10", "100", "1000"})
    private int threadCount;
    
    private AtomicLong atomicCounter;
    private LongAdder longAdder;
    
    @Setup
    public void setup() {
        atomicCounter = new AtomicLong();
        longAdder = new LongAdder();
    }
    
    @Benchmark
    public void testAtomicLong() {
        atomicCounter.incrementAndGet();
    }
    
    @Benchmark
    public void testLongAdder() {
        longAdder.increment();
    }
}
```

---

## ⚠️ 常见误区

### **误区 1：LongAdder 不用 CAS**

❌ **错误**：
> "LongAdder 的 Cell 直接 value++，无需 CAS"

✅ **正确**：
```java
// LongAdder 源码
final boolean cas(long cmp, long val) {
    return UNSAFE.compareAndSwapLong(this, valueOffset, cmp, val);
}

// add 方法调用
if (!a.cas(v = a.value, v + x)) {
    longAccumulate(x, null);  // CAS 失败，冲突处理
}

真相：
- LongAdder 仍然使用 CAS！
- 优势是减少 CAS 冲突概率，而非消除 CAS
```

---

### **误区 2：LongAdder 总是更快**

❌ **错误**：
> "任何场景都用 LongAdder 替代 AtomicLong"

✅ **正确**：
```
低并发（<10 线程）：
- AtomicLong 略优（无分段开销）
- 内存占用更小

高并发（>100 线程）：
- LongAdder 显著优（分散冲突）
- 吞吐量提升 5-10 倍

选择建议：
- 低并发 + 精确值 → AtomicLong
- 高并发 + 写多读少 → LongAdder
```

---

### **误区 3：get() 返回精确值**

❌ **错误**：
> "LongAdder.sum() 返回精确的实时值"

✅ **正确**：
```java
public long sum() {
    long sum = base;
    if (cells != null) {
        for (Cell a : cells) {
            if (a != null)
                sum += a.value;  // 遍历累加
        }
    }
    return sum;
}

问题：
- 遍历过程中，其他线程可能修改 Cell.value
- 返回的值是"某个时间点"的近似值
- 不保证强一致性

影响：
- 计数器场景：通常可接受（统计用途）
- 精确计数场景：不适合（如银行余额）
```

---

## 💡 实践建议

### **1. 场景选择**

**适合 LongAdder**：
```java
// 场景 1：高并发统计
LongAdder requestCount = new LongAdder();
// 每秒 10 万次 increment，10 次 get
requestCount.increment();

// 场景 2：监控指标
LongAdder totalLatency = new LongAdder();
totalLatency.add(latency);  // 累加延迟
```

**适合 AtomicLong**：
```java
// 场景 1：低并发精确计数
AtomicLong sequence = new AtomicLong();
long id = sequence.incrementAndGet();  // 需要精确 ID

// 场景 2：读多写少
AtomicLong cacheHits = new AtomicLong();
long hits = cacheHits.get();  // 频繁读取
```

---

### **2. 分段数量选择**

**JDK LongAdder**：
- 动态扩容：初始无 Cell，冲突时创建
- 扩容策略：2 的幂（2, 4, 8, 16...）
- 最大分段：CPU 核心数 * 2

**你的实现优化**：
```java
public LongAdderCounter(int segmentCount) {
    // 建议：设置为 CPU 核心数的倍数
    int cpuCount = Runtime.getRuntime().availableProcessors();
    this.segmentCount = Integer.highestOneBit(cpuCount * 2);
    this.counters = new AtomicLong[(int) segmentCount];
    // ...
}
```

---

### **3. 避免滥用**

**不适合的场景**：
```java
// ❌ 场景 1：需要精确值
LongAdder balance = new LongAdder();  // 银行账户余额
balance.add(amount);
// 问题：sum() 可能不是精确余额

// ✅ 替代方案
AtomicLong balance = new AtomicLong();

// ❌ 场景 2：单线程
LongAdder counter = new LongAdder();  // 单线程计数
// 问题：引入不必要的复杂度

// ✅ 替代方案
long counter = 0;  // 普通变量即可
```

---

## 🔗 知识关联

### **上游知识**
- [[CAS 原理]] → AtomicLong 的底层实现
- [[volatile 关键字]] → Cell.value 的可见性保证
- [[哈希函数]] → 线程到分段的映射

### **下游知识**
- [[ConcurrentHashMap]] → 分段锁的类似应用
- [[缓存行伪共享]] → @Contended 注解的作用
- [[线程阻塞机制]] → CAS 失败后的处理

### **横向关联**
- [[AtomicLong]] → 单变量 CAS 计数器
- [[Striped64]] → LongAdder 的父类，分段框架
- [[LongAccumulator]] → 自定义累加逻辑

---

## 📈 演进历史

### **为什么 JDK8 引入 LongAdder？**

**背景**：
```
JDK7 及之前：
- 只有 AtomicLong
- 高并发下性能差（CAS 冲突严重）

问题：
- 多核 CPU 普及
- 并发量提升
- 需要更高效的计数器
```

**解决方案**：
```
JDK8：
- 引入 LongAdder
- 分段思想
- 吞吐量提升 5-10 倍（高并发场景）
```

---

### **与 ConcurrentHashMap 的对比**

| 特性 | LongAdder | ConcurrentHashMap |
|------|-----------|-------------------|
| **分段思想** | Cell 数组 | Segment 数组（JDK7） |
| **锁机制** | CAS | synchronized + CAS |
| **扩容** | 动态扩容 | 动态扩容 |
| **哈希** | getProbe() & (n-1) | hash & (n-1) |
| **设计目标** | 高并发计数 | 高并发 Map |

**共同点**：
- 分段减少冲突
- 动态扩容
- 位运算哈希

---

## 🎯 总结

**核心要点**：
1. LongAdder 通过分段减少 CAS 冲突，而非消除 CAS
2. 适合高并发、写多读少场景
3. 使用 @Contended 避免缓存行伪共享
4. get() 返回近似值，非强一致性
5. 低并发场景 AtomicLong 更优

**设计思想**：
- 分而治之（空间换时间）
- 动态扩容（按需分配）
- 位运算优化（性能优先）

**应用价值**：
- 高并发计数器（如 QPS 统计）
- 监控指标累加（如总延迟）
- 替代 AtomicLong（高并发场景）

---

> **下一步探索**：[[Striped64 源码分析]]、[[缓存行伪共享]]、[[ConcurrentHashMap 分段锁]]
