---
type: emrg
id: EMRG-Sentinel-高级特性与生态
title: Sentinel高级特性与生态集成
maturity: emerging
created: 2026-05-14
updated: 2026-05-14
related_goals:
  - GOAL-SpringCloud微服务
  - GOAL-Java核心深化
subtopics:
  - Sentinel-响应式集成
  - "@SentinelResource 注解"
  - 授权规则
  - 集群限流细节
---

# EMRG-Sentinel-高级特性与生态集成

> 成熟度: 🟡 emerging

## 一句话定义

Sentinel 高级特性与生态集成涵盖框架层扩展能力：通过注解简化接入、通过响应式集成适配 Reactor/WebFlux、通过集群限流突破单机瓶颈，以及通过授权规则实现黑白名单控制。

## 知识拓扑

[Sentinel 高级特性与生态]
  ├─ [[Sentinel-Entry生命周期与WebFlux集成]]
  ├─ @SentinelResource 注解（待创建）
  ├─ 授权规则（待创建）
  └─ 集群限流（待创建）

## 下一步（按优先级）

1. **响应式集成补全** — 将 `Sentinel-Entry生命周期与WebFlux集成` 中的通用原理提取为原子笔记 `Sentinel-异步Entry模式`
2. **@SentinelResource 深入** — 对比同步 AOP 与异步流事件绑定两种模式的适用场景
3. **集群限流** — 理解 Token Server 架构与单机限流的协同策略

## 涌现历史

- **2026-05-14**: 从 [[EMRG-Sentinel]] 分裂而出，聚焦框架集成与分布式扩展能力

## 成熟度说明

3/3 篇笔记 mastery ≥ 60（verified），响应式集成已深入理解。待创建笔记：@SentinelResource 注解、授权规则、集群限流。

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
- [[Sentinel-核心架构]] — 归属 [[EMRG-Sentinel-核心机制]]
- [[LeapArray-滑动窗口]] — 归属 [[EMRG-Sentinel-核心机制]]
- [[WebFlux-生命周期与多线程时序]] — 归属 [[EMRG-NIO网络编程]]

#### 跨界枢纽（被多个 EMRG 引用）
- 暂无

### 检查点

- [ ] 子主题数: 4（健康）
- [ ] 待创建原子笔记: 3 篇（@SentinelResource、授权规则、集群限流）
- [ ] 最后更新: 2026-05-14（超过 90 天则触发归档检查）
