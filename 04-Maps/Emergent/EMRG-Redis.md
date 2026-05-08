---
created: 2026-04-28
updated: 2026-05-04
---

# Redis 知识图谱

## 核心概念 (Concepts)

- [[redis-data-structures]] - Redis 数据结构概览
- [[redis-benchmark]] - 性能基准测试 ⭐ NEW
- [[Redis单线程事件循环模型]]
- [[Redis Cluster水平扩展]]

## 深度文档 (Deep Dives)

- [[Redis数据结构详解]]
- [[Redis持久化机制]]

## 项目实战

- [[redis-counter-service]] - 秒杀库存扣减服务 ⭐ IN-PROGRESS
  - Lua 脚本原子扣减
  - 性能测试：单节点 20W+ QPS
- [[hot-content-counter-备用设计]] - 热内容计数服务（暂停）

## 练习记录

- [[2026-05-02-redis-data-structures]] - 数据结构练习
- [[2026-05-02-hot-content-counter-phase1]] - 热内容计数 Phase1
- [[2026-05-02-redis-benchmark-and-network-path]] - 性能基准测试 ⭐ NEW

## 里程碑

- [x] Redis 数据结构基础（2026-05-02）
- [x] Lua 脚本原子操作（2026-05-03）
- [ ] 应用层 WRK 测试（2026-05-05）
- [ ] Redis Cluster 水平扩展验证