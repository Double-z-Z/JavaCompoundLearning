---
type: atomic-note
id: CONCEPT-tlb-cache-hierarchy
created: 2026-05-29
updated: 2026-05-29
tags: [操作系统, CPU]
status: 🌿
mastery: 45
related_goal: [GOAL-Java核心深化]
---

# TLB 与 CPU 缓存层级

## 一句话定义
CPU 内部有两条独立的缓存线：翻译线（TLB：虚拟→物理地址）和数据线（L1/L2/L3 Cache：缓存物理地址的数据）。只有 CPU 有 TLB，Cache 和主存用的是物理地址。

## 核心理解

### 翻译线和数据线的交汇
```
虚拟地址 → [L1 TLB] → 物理地址 → [L1 Cache] → [L2 Cache] → [L3 Cache] → 主存
              ↓miss                   ↓miss         ↓miss         ↓miss
            查页表                  查 L2        查 L3          读 DRAM
```
改数据时只改 Cache 行的数据 + MESI 状态，**不改 TLB**（映射没变）。

### TLB 结构
- L1 TLB：64 entry × 4KB = 覆盖 256KB（1-2 周期命中）
- L2 TLB：1536 entry × 4KB = 覆盖 6MB
- 每个 CPU 核有独立 TLB，进程切换时全刷（CR3 变化）

**大页（2MB/1GB）可以大幅减少 TLB miss**：128MB 数组用 4KB 页 = 32768 个 entry，用 2MB 大页 = 64 个 entry。

### 三级 Cache 与包含策略
| 层级 | 大小 | 延迟 | 归属 |
|------|------|------|------|
| L1 (I+D) | 各 32KB | ~1ns / 4 周期 | 每核独占 |
| L2 | 256-512KB | ~4ns / 12 周期 | 每核独占 |
| L3 | 几-十几 MB | ~12ns / 40 周期 | 所有核共享 |

包含策略：Intel 常用 Inclusive（L3 包 L2 包 L1），AMD 常用 Exclusive（互不重叠），ARM 用 Non-Inclusive。

### 缓存行 = 64 字节 + MESI 状态
```
[Tag 48bit] [64B 数据块] [MESI Flag]
M = 已修改（内存过期）
E = 独占且干净
S = 共享且干净
I = 失效
```
`volatile` 写 → 缓存行标 M → 广播使其他核的同行标 I。`LongAdder` 用分散到多个 Cell（不同缓存行）来避免 false sharing。

### 对你 Java 的影响
```java
// false sharing：两个 volatile 挤在同一缓存行 → 乒乓
// 解决：@Contended 注解（Java 8+）或填充 64B
```

## 关键关联
- [[虚拟内存与物理内存]] - TLB 缓存的是页表的翻译结果，页表是翻译的源头
- [[缓存行伪共享]] - false sharing 的根因就是两个 volatile 落在同一 64B 缓存行
- [[futex]] - futex 的用户态自旋 + 内核态阻塞，依赖 MESI 的缓存行一致性

## 深入思考
💡 为什么 `@Contended` 要填充到 64B？两个 `volatile long` 各 8B，相邻的话在同一缓存行，一个核写、另一个核读 → 缓存行在两核间乒乓 → 性能崩塌。

## 来源
- 对话：2026-05-29 从 VIRT 延伸出的 CPU 架构讨论

---
## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿 理解
- 更新记录：
  - 2026-05-29: mastery=45（理解 TLB/Cache 两条线、三级缓存、MESI 基本状态；纠正了"Cache 设备也有 TLB"的误区）
