# 并发计数器实战练习

> 📅 日期：2026-04-11  
> 🎯 目标：理解不同计数器实现的原理与性能差异  
> 🔗 关联知识：[[LongAdder 设计]]、[[CAS 原理]]、[[分段锁]]

---

## 📝 练习内容

### **任务 1：实现三种线程安全计数器**

#### **1.1 AtomicCounter（基于 AtomicLong）**

**实现位置**：
`/01-Projects/project-concurrency-test/src/main/java/com/example/review/counter/AtomicCounter.java`

**核心代码**：
```java
public class AtomicCounter implements Counter {
    private AtomicLong counter;
    
    public AtomicCounter() {
        this.counter = new AtomicLong(0);
    }

    public void increment() {
        counter.incrementAndGet();  // CAS 操作
    }

    public long get() {
        return counter.get();  // volatile 读
    }
}
```

**原理**：
- 基于 CAS（Compare-And-Swap）保证原子性
- 单变量，所有线程竞争同一个 CAS 操作
- 低并发下性能好，高并发下 CAS 冲突严重

---

#### **1.2 LongAdderCounter（分段 AtomicLong）**

**实现位置**：
`/01-Projects/project-concurrency-test/src/main/java/com/example/review/counter/LongAdderCounter.java`

**核心代码**：
```java
public class LongAdderCounter implements Counter {
    private AtomicLong[] counters;
    private long segmentCount;

    public LongAdderCounter(int segmentCount) {
        this.segmentCount = segmentCount;
        this.counters = new AtomicLong[segmentCount];
        for (int i = 0; i < segmentCount; i++) {
            counters[i] = new AtomicLong(0);
        }
    }

    @Override
    public void increment() {
        Thread thread = Thread.currentThread();
        long segmentIndex = thread.getId() % segmentCount;
        counters[(int) segmentIndex].incrementAndGet();
    }

    @Override
    public long get() {
        long result = 0;
        for (AtomicLong counter : counters) {
            result += counter.get();
        }
        return result;
    }
}
```

**原理**：
- 分段思想：将单个计数器拆分为多个分段
- 线程根据 ID 哈希到不同分段，减少冲突
- 读操作需要遍历所有分段累加

**优化空间**（待实现）：
- [ ] 使用 `getProbe()` 替代 `threadId % n`（更好的哈希分布）
- [ ] 添加 `@Contended` 注解（避免缓存行伪共享）
- [ ] 动态扩容分段数组（类似 JDK LongAdder）

---

#### **1.3 SynchronizedCounter（基于 synchronized）**

**实现状态**：⏸️ 未实现（可后续补充）

**思考题**：
- 如果用 `synchronized` 实现计数器，性能会如何？
- 适用场景是什么？

---

## 📊 对比测试结果

### **预期性能特征**

| 场景 | AtomicLong | LongAdder | 原因分析 |
|------|-----------|-----------|----------|
| **低并发（10 线程）** | ⭐⭐⭐⭐ | ⭐⭐⭐ | AtomicLong 无分段开销 |
| **高并发（1000 线程）** | ⭐⭐ | ⭐⭐⭐⭐⭐ | LongAdder 分散冲突 |
| **写多读少（100:1）** | ⭐⭐ | ⭐⭐⭐⭐⭐ | CAS 冲突被分段缓解 |
| **写少读多（1:100）** | ⭐⭐⭐⭐ | ⭐⭐⭐ | 读操作 LongAdder 需遍历 |

### **测试建议**（待执行）

```bash
# 运行性能测试
cd /01-Projects/project-concurrency-test
mvn test -Dtest=CounterPerformanceTest

# 测试参数
- 线程数：10, 100, 1000
- 操作次数：100 万次
- 读写比：100:1, 50:50, 1:100
```

---

## 💡 关键洞察

### **洞察 1：CAS 的局限性**
```
CAS 类似乐观锁：
- 无竞争时：极快（~20 纳秒）
- 高竞争时：大量失败重试，性能急剧下降

LongAdder 的优化：
- 不是消除 CAS，而是减少 CAS 冲突概率
- 通过分段，让不同线程操作不同变量
```

### **洞察 2：分段思想的本质**
```
分段 = 空间换时间

代价：
- 内存占用增加（n 个 AtomicLong）
- 读操作变慢（遍历累加）

收益：
- 写操作冲突减少
- 高并发下吞吐量提升

适用场景：写多读少 + 高并发
```

### **洞察 3：哈希函数的重要性**
```
当前实现：threadId % n
问题：线程 ID 通常连续，分布不均

JDK LongAdder：getProbe() & (n-1)
优势：伪随机值，分布均匀
      位运算替代取模，更快
```

---

## 🔗 知识关联

- LongAdder 设计 → 为什么 JDK 使用 Cell 数组而非 AtomicLong 数组
- CAS 原理 → AtomicLong 的底层实现
- 分段锁思想 → ConcurrentHashMap 的分段设计
- 缓存行伪共享 → @Contended 注解的作用

---

## 📈 下一步练习

- [ ] 实现 SynchronizedCounter 并对比性能
- [ ] 优化 LongAdderCounter 的哈希函数
- [ ] 添加 @Contended 注解测试性能提升
- [ ] 实现动态扩容机制（类似 JDK LongAdder）
- [ ] 编写完整的性能基准测试

---

> 💬 **练习感悟**：通过手写三种计数器，深刻理解了"分段减少冲突"的设计思想。原来 LongAdder 并不是消除了 CAS，而是通过巧妙的分段设计降低了 CAS 冲突的概率。这种"空间换时间"的权衡思维，在并发编程中无处不在。
