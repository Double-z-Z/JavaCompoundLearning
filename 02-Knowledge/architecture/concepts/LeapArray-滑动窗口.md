---
type: atomic-note
id: CONCEPT-leaparray
created: 2026-05-11
tags:
  - sentinel
  - 数据结构
  - 滑动窗口
mastery: 60
source: "[[03-Practice/reflections/2026-05-11-Sentinel-对话.md]]"
related_emrg: [[EMRG-Sentinel]]
related_goal: []
---

# LeapArray 滑动窗口

## 一句话定义
LeapArray 是 Sentinel 的核心数据结构，基于环形数组实现滑动时间窗口，通过数学映射（取模）将连续时间离散化为固定数量的可复用槽位，解决固定窗口边界突发问题。


## 核心数据结构

```
LeapArray<T>
  ├── array: AtomicReferenceArray<WindowWrap<T>>  ← 环形数组
  ├── windowLengthInMs: int                        ← 单个格子长度（如500ms）
  ├── sampleCount: int                             ← 格子数量（如2个）
  └── intervalInMs: int                            ← 总窗口跨度（如1000ms）

WindowWrap<T>
  ├── windowStart: long                            ← 窗口起始时间戳（唯一标识）
  ├── value: T                                     ← MetricBucket（统计数据）
  └── length: long                                 ← 窗口长度
```


## 核心算法：时间映射

### 计算数组索引
```java
long timeId = timeMillis / windowLengthInMs;   // 大周期取商（第几个格子周期）
long idx = timeId % sampleCount;               // 小范围取模（映射到数组下标）
```

**示例**：
```
windowLengthInMs = 500, sampleCount = 2

time=999ms:   timeId=1, idx=1%2=1 → array[1]
time=1001ms:  timeId=2, idx=2%2=0 → array[0]  ← 索引变化！
time=1499ms:  timeId=2, idx=2%2=0 → array[0]
time=1501ms:  timeId=3, idx=3%2=1 → array[1]  ← 又变了
```

### 窗口判断四态
```java
public WindowWrap<T> currentWindow(long timeMillis) {
    long idx = calculateTimeIdx(timeMillis);
    WindowWrap<T> old = array.get(idx);
    
    if (old == null) {
        return createEmptyWindow(timeMillis, idx);     // Case A: 空槽，新建
    }
    if (timeMillis - old.windowStart() > windowLengthInMs) {
        return resetWindowTo(old, timeMillis);          // Case B: 过期，复用重置
    }
    if (timeMillis < old.windowStart()) {
        return handleTimeWrap(old, timeMillis);          // Case C: 时钟回拨
    }
    return old;                                          // Case D: 有效，直接复用
}
```


## 固定窗口 vs 滑动窗口

| 特性 | 固定窗口 | 滑动窗口（LeapArray） |
|------|---------|---------------------|
| 边界处理 | 窗口切换时可能 2 倍突发 | 平滑过渡，无突发 |
| 内存占用 | 1 个计数器 | sampleCount 个计数器 |
| 实现复杂度 | 简单 | 中等 |
| 精度 | 低（有边界效应） | 高 |

**滑动窗口本质**：固定窗口的退化情况是 `slide_interval == window_size`，通用情况 `window_size > slide_interval`


## Cache Line 优化

### 问题：MetricBucket 内多个计数器的伪共享
```java
class MetricBucket {
    long passCount;      // offset +0  ┐
    long blockCount;     // offset +8  │ 同一 Cache Line!
    long successCount;   // offset +16 │ 多线程写时互相干扰
    long errorCount;     // offset +24 ┘
}
```

### 解决方案对比
| 方案 | 内存开销 | 安全性 | Sentinel 选择 |
|------|---------|--------|--------------|
| 无 padding | 0 | ❌ 高风险 | |
| @Contended (128B) | 大 | ✅ 最安全 | 部分使用 |
| 数组间隔法 | 中 | ✅ 可控 | **主要方案** |
| LongAdder 分段 | 可变 | ✅ 写分散 | 冷路径使用 |

### 双轨制策略
- **热路径**（限流判断）：AtomicLong → 快速读写
- **冷路径**（统计展示）：LongAdder → 写入分散，定时 sum()


## 时钟回退处理

### Sentinel 的选择：接受不完美
```java
if (timeMillis < old.windowStart()) {
    return old;  // 直接返回旧窗口，假装没发生回拨
}
```

**哲学**：宁可要"模糊但稳定"的结果，不要"精确但脆弱"的系统。

**影响**：回拨期间统计数据的时间跨度变大，但不丢数据、不崩溃。


## 关联知识

- [[缓存行伪共享]] - 关联原因：LeapArray 性能优化的核心问题，@Contended vs 手动 padding 的选择
- [[Ring-Buffer]] - 关联原因：LeapArray 底层使用环形数组结构，相同的循环复用思想
- [[LongAdder]] - 关联原因：MetricBucket 冷路径计数器方案，sum() 遍历代价 vs 写分散收益
- [[Sentinel-核心架构]] - 关联原因：LeapArray 是 Sentinel 实现滑动窗口计数的核心数据结构


## 来源
- 对话：[[2026-05-11-Sentinel-对话]]


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿 理解
- 更新记录：
  - 2026-05-11: mastery=60 (理解时间映射算法、窗口复用机制、Cache Line 优化)

---

## 来源