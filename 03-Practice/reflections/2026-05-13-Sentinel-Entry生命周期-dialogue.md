---
type: reflection
date: 2026-05-13
topic: Sentinel Entry 生命周期与 WebFlux 集成
insights_extracted: true
---

# 对话反思

## 核心收获

通过对话引导，从"Entry 是什么"深入到 Sentinel WebFlux 的源码级实现：

1. **Entry 的本质**：不是异常记录器，而是"门票+手环"，串联一次资源访问的完整生命周期
2. **WebFlux 集成**：通过 `CoreSubscriber` Hook `onComplete`/`onError`，而非简单的 `doFinally`
3. **onCancel 的陷阱**：源码验证发现 `onCancel` 直接调用 `entry.exit()`，不标记异常，导致统计失真
4. **工程决策**：选择 C（监控层补偿）最符合 Sentinel"模糊但稳定"的设计哲学

## 思维跃迁

从"文档阅读"到"源码验证"再到"工程 trade-off 分析"，完成了 L2 -> L3 的关键跨越。

## 关联笔记

- [[Sentinel-核心架构]]
- [[Sentinel-Entry生命周期与WebFlux集成]]
