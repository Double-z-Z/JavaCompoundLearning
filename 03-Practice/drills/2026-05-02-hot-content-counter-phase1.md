---
created: 2026-05-02
tags: [practice, cache, local-cache, phase1]
project: [[hot-content-counter]]
status: pending
---

# 练习记录: Phase 1 — 本地缓存层（L1）

> 本练习对应 [[hot-content-counter]] 项目 Phase 1


## 目标
实现基于 Guava Cache 的本地缓存层，理解"热点数据本地化"的价值


## 验证方式
- 10万次随机读取，本地缓存命中率 > 70%（热点数据Zipf分布）
- 对比无缓存 vs 有本地缓存的QPS


## 涉及知识点
- [[本地缓存]]
- [[容量规划]]


## 练习内容

### 1. Guava Cache 基础配置
- 固定容量 LRU
- 统计命中率


### 2. 模拟热点数据分布（Zipf）
- 80% 请求集中在 10% 的数据
- 验证命中率是否符合预期


### 3. 并发回源问题（Cache Stampede）
- 模拟热点key失效瞬间
- 实现 singleflight 模式


## 测试记录

| 测试项 | 结果 | 备注 |
|-------|-----|-----|
| 命中率（10万次） | - | - |
| QPS对比（无缓存） | - | - |
| QPS对比（有缓存） | - | - |


## 遇到的问题

<!-- 实施过程中记录 -->


## 关联原子笔记
- [[本地缓存]]: 待创建或更新


---
📊 **练习完成度**: 0%