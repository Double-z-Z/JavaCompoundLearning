---
type: emrg
id: EMRG-Sentinel-核心机制
title: Sentinel核心机制
maturity: verified
created: 2026-05-14
updated: 2026-05-14
related_goals: [GOAL-Java核心深化]
subtopics:
  - 架构定位与设计哲学
  - Cache Line 性能优化
  - LeapArray 滑动窗口
  - 限流算法
  - 熔断机制
  - 热点参数限流
  - 流量控制效果
  - 系统自适应限流
  - 熔断恢复机制
  - 上下文传播
---

# EMRG-Sentinel-核心机制

> 成熟度: 🟢 verified

## 一句话定义

Sentinel 核心机制涵盖流量判决的完整链路：从 LeapArray 滑动窗口统计，到限流/熔断/自适应三种判决策略，再到热点参数识别与流量整形效果，全部在本地内存完成，实现纳秒级响应。

## 知识拓扑

[Sentinel 核心机制]
  ├─ [[Sentinel-核心架构]]
  ├─ [[LeapArray-滑动窗口]]
  ├─ [[Sentinel-熔断机制]]
  ├─ [[Sentinel-热点参数限流]]
  ├─ [[Sentinel-流量控制效果]]
  ├─ [[Sentinel-自适应限流]]
  └─ [[Sentinel-上下文传播]]

## 涌现历史

- **2026-05-11**: 因 Sentinel 对话密度溢出创建学习地图
- **2026-05-13**: 核心机制已通过 `redis-counter-service-webflux` 与 `spike-protection` 项目实战验证
- **2026-05-14**: 从 [[EMRG-Sentinel]] 分裂而出，聚焦本地流量判决核心机制

## 成熟度说明

4/6 篇笔记 mastery ≥ 60（verified），架构/滑动窗口/限流/熔断/热点/流量效果/自适应/恢复/上下文均已通过项目实战验证。

---

## 🤖 AI 工作区（以下由 Dataview 自动维护，请勿手动编辑）

### 核心成员

```dataviewjs
const emrgId = dv.current().id;
dv.table(
  ["笔记", "mastery", "验证状态"],
  dv.pages()
    .where(p => p.related_emrg && p.related_emrg.includes(emrgId))
    .sort(p => p.mastery, 'desc')
    .map(p => [
      p.file.link,
      p.mastery ?? '-',
      (p.mastery >= 60) ? '🟢' : '🟡'
    ])
);
```

### 边界声明

#### 边缘关联（链接但不纳入）
- `LongAdder` / `AtomicLong` — 归属 [[EMRG-并发编程]]
- `epoll` / `Netty` — 归属 [[EMRG-NIO网络编程]]

#### 跨界枢纽（被多个 EMRG 引用）
- [[Count-Min-Sketch]] — 归属 [[EMRG-Cache]]，被 Sentinel 热点参数限流引用
- [[W-TinyLFU]] — 归属 [[EMRG-Cache]]，被 Sentinel 热点参数限流引用

### 检查点

- [x] 子主题数: 10（≤ 7 略超，但均为紧密耦合的核心机制，暂不二次分裂）
- [ ] 最后更新: 2026-05-14（超过 90 天则触发归档检查）
