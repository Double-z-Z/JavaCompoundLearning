---
type: emrg
id: EMRG-Cache
title: 缓存策略与本地缓存
maturity: theoretical
created: 2026-05-15
updated: 2026-05-15
related_goals:
  - GOAL-ORM与缓存
subtopics:
  - 淘汰算法
  - 一致性策略
  - 实战设计
---

# EMRG-Cache

> 成熟度: 🟡 theoretical

## 一句话定义

缓存策略不是简单的"加速手段"，而是一套围绕空间效率（淘汰算法）、时间一致性（失效策略）和架构分层（本地/分布式协同）组成的系统化数据访问优化体系。

## 知识拓扑

[Cache 核心认知]
  ├─ 淘汰算法
  │   ├─ [[W-TinyLFU]]
  │   └─ [[Count-Min-Sketch]]
  ├─ 一致性策略
  │   ├─ [[多级缓存一致性]]
  │   └─ [[缓存失效策略]]
  └─ 实战设计
      └─ [[内容热度分布与冷热分层]]

## 关键缺口（待补充）

- [ ] Caffeine 源码级理解（Window Cache 晋升机制、Ring Buffer 无锁设计）
- [ ] 缓存预热策略系统化文档
- [ ] 缓存穿透/击穿/雪崩的完整防御体系

## 关联领域

- [[EMRG-Redis]] — Redis 作为 L2 分布式缓存层
- [[EMRG-Sentinel-核心机制]] — Sentinel 热点参数限流使用 Count-Min-Sketch 和 W-TinyLFU 进行频率估计

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
- [[Sentinel-热点参数限流]] → 归属 [[EMRG-Sentinel-核心机制]]，使用 Count-Min-Sketch 进行热点识别
- [[Redis-数据类型与编码]] → 归属 [[EMRG-Redis]]，Redis 是 L2 缓存的实现之一

#### 跨界枢纽（被多个 EMRG 引用）
- [[分段锁思想]] — 同时被 [[EMRG-并发编程]] 和 [[EMRG-Cache]] 引用（W-TinyLFU的Striped Buffer设计）
- [[缓存行伪共享]] — 同时被 [[EMRG-并发编程]] 和 [[EMRG-Cache]] 引用（Caffeine Ring Buffer的缓存行填充）

### 涌现历史

- **2026-05-15**: 因引用修正与密度溢出创建。原归属 [[EMRG-Sentinel-核心机制]] 的 Count-Min-Sketch 和 W-TinyLFU、原归属 [[EMRG-Redis]] 的多级缓存一致性/内容热度分布与冷热分层，经确认更符合缓存策略主题，统一归并至此。

### 成熟度说明

5/6 篇笔记 mastery < 60，均为理论认知阶段，缺少独立项目实战验证。W-TinyLFU 和 Count-Min-Sketch 已通过 caffeine 对话建立基础理解，但尚未在项目中独立落地。

### 检查点

- [ ] 子主题数: 3（健康）
- [ ] 最后更新: 2026-05-15（超过 90 天则触发归档检查）
