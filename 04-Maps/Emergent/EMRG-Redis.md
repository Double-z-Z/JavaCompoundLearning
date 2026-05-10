---
created: 2026-04-28
updated: 2026-05-09
---

# Redis 知识图谱

## 核心概念 (Concepts)

- [[redis-data-structures]] - Redis 数据结构概览
- [[redis-benchmark]] - 性能基准测试 ⭐ NEW
- [[Redis单线程事件循环模型]]
- [[Redis Cluster水平扩展]]
- **[[数据倾斜解决方案]]** ⭐ NEW (2026-05-09) - 热点Key单点击穿问题与分层防御

## 深度文档 (Deep Dives)

- [[Redis数据结构详解]]
- [[Redis持久化机制]]

## 项目实战

- [[redis-counter-service-webflux]] - 秒杀库存扣减服务（WebFlux版本）⭐ IN-PROGRESS
  - Lua 脚本原子扣减
  - Caffeine 本地缓存集成
  - Saga 补偿模式（多SKU订单）
  - 数据倾斜测试用例（T-050~T-055）⭐ NEW
- [[redis-counter-service]] - 秒杀库存扣减服务（传统版本）
  - Lua 脚本原子扣减
  - 性能测试：单节点 20W+ QPS
- [[hot-content-counter-备用设计]] - 热内容计数服务（暂停）

## 关联领域

- **[[WebFlux响应式编程]]** - 响应式编程在 Redis 场景的应用
- **[[Flux核心概念]]** - Reactor 操作符在 Redis 服务中的使用
- **[[响应式生命周期信号]]** - doOn* 操作符的正确使用
- **[[Saga模式]]** - 多SKU订单的补偿事务

## 练习记录

- [[2026-05-02-redis-data-structures]] - 数据结构练习
- [[2026-05-02-hot-content-counter-phase1]] - 热内容计数 Phase1
- [[2026-05-02-redis-benchmark-and-network-path]] - 性能基准测试 ⭐ NEW
- **[[2026-05-09-webflux-operators-practice]]** - Reactor 操作符实战练习 ⭐ NEW
- **[[2026-05-09-data-skew-solution-dialogue]]** - 数据倾斜解决方案讨论 ⭐ NEW

## 里程碑

- [x] Redis 数据结构基础（2026-05-02）
- [x] Lua 脚本原子操作（2026-05-03）
- [x] WebFlux 响应式改造（2026-05-07）
- [x] Caffeine 本地缓存集成（2026-05-09）
- [x] 数据倾斜解决方案设计（2026-05-09）⭐ NEW
- [ ] 应用层 WRK 测试（2026-05-05）
- [ ] Redis Cluster 水平扩展验证
- [ ] **秒杀系统自适应架构实现** ⭐ NEXT