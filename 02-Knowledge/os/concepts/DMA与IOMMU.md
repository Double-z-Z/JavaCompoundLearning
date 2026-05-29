---
type: atomic-note
id: CONCEPT-dma-iommu
created: 2026-05-29
updated: 2026-05-29
tags: [操作系统, IO]
status: 🌿
mastery: 40
related_goal: [GOAL-Java核心深化]
---

# DMA 与 IOMMU

## 一句话定义
DMA 让设备直接读写内存绕过 CPU，IOMMU 给 DMA 加一层地址翻译实现设备隔离和虚拟机穿透。

## 核心理解

### PIO vs DMA
```
PIO:  磁盘 → CPU(IN指令逐字节搬) → 内存     CPU 全程占满
DMA:  磁盘 ────DMA控制器────→ 内存         CPU 只发命令，不收数据
```
CPU 告诉 DMA 控制器"搬到物理地址 0x3c000，搬完通知我"，然后去干别的。网卡、磁盘、GPU 全是 DMA。

### DirectByteBuffer 为什么存在
```java
ByteBuffer.allocateDirect(8192);  // 堆外，物理地址固定
channel.read(buf);                // OS 发起 DMA → 网卡直接写那块内存
```
`HeapByteBuffer` 在 JVM 堆里，GC 会移动 → 物理地址变化 → DMA 写错地址。`DirectByteBuffer` 在堆外，物理地址固定，DMA 安全。

### IOMMU：设备的 MMU
```
没有 IOMMU:  设备 DMA 物理地址 0x100000  → 无保护，可能写到内核
有 IOMMU:    设备 DMA IO-虚拟 0x5000   → IOMMU 翻译 → 物理 0x3c000
             设备 DMA IO-虚拟 0x9999   → 没映射 → 拒绝
```
保护设备不能乱写内存，同时让虚拟机直接使用物理设备（VFIO 穿透）。

### MMU vs IOMMU
| | MMU | IOMMU |
|---|---|---|
| 翻译谁的地址 | CPU 虚拟地址 | 设备 IO 虚拟地址 |
| 保护谁 | 进程隔离 | 设备隔离 |
| 额外收益 | — | VM 设备穿透 |

### NIO 零拷贝链路
```
网卡 DMA → IOMMU 翻译 → DirectByteBuffer 物理页
    → selector 通知 readable → 代码读到数据
全程 CPU 没有拷贝过 1 字节
```

## 关键关联
- [[TLB与CPU缓存层级]] - IOMMU 也有自己的 TLB，缓存设备虚拟→物理翻译
- [[虚拟内存与物理内存]] - DMA 要求物理地址连续，虚拟地址连续≠物理连续（跨页后 scatter-gather DMA 解决）
- [[NIO-Buffer]] - DirectByteBuffer 是 DMA 在 Java 层的体现

## 代码与实践
```java
// DirectByteBuffer 是 DMA 安全写入的前提
ByteBuffer buf = ByteBuffer.allocateDirect(4096);
// 底层：posix_memalign → 页对齐 → 物理地址传给 DMA 控制器
```

## 来源
- 对话：2026-05-29 从 TLB 延伸的 DMA/IOMMU 讨论

---
## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿 理解
- 更新记录：
  - 2026-05-29: mastery=40（理解 DMA 绕过 CPU 的原理、IOMMU 的设备隔离作用、与 DirectByteBuffer 的关联）
