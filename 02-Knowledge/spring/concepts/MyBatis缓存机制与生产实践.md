---
type: concept
created: 2026-05-28
updated: 2026-05-31
tags: [mybatis, 缓存, 性能, 架构]
related_emrg: [EMRG-ORM与持久层, EMRG-Cache]
related_goal: [GOAL-ORM与缓存]
mastery: 55
---

# MyBatis缓存机制与生产实践

> 一句话：MyBatis 原生二级缓存的设计哲学是"简单可用"而非"高并发高性能"，生产环境高并发场景应在 Service 层引入 Redis/Caffeine 做显式缓存。

## 一、核心瓶颈：SynchronizedCache 全局锁

MyBatis 二级缓存通过装饰器链实现，其中 `SynchronizedCache` 对 `getObject`/`putObject` 等方法加了 `synchronized` 同步：

```java
public synchronized void putObject(Object key, Object object) { ... }
public synchronized Object getObject(Object key) { ... }
```

**问题**：
- **全局串行化**：同一 Mapper 的二级缓存读写全局互斥，无论 key 是否相同
- **高并发吞吐塌陷**：QPS 升高时，缓存访问从"内存操作"退化为"串行排队"
- **事务提交延迟**：数据在 `SqlSession.commit()` 后才刷入缓存，flush 过程同样受锁保护

## 二、性能数据参考

| 数据来源 | 结论 |
|---|---|
| MyBatis 动态 SQL 高并发测试 | MyBatis 注解动态 SQL 的 QPS 约为 80，替换为原生 JDBC 后 QPS 提升至 460 |
| Redisson PRO 对比 | 本地缓存模式对 MyBatis Cache 的读操作性能提升可达 **45 倍** |
| 阿里云等云厂商建议 | **不推荐使用 MyBatis 原生二级缓存**，原因包括数据不一致、内存占用不可控、缺乏自动同步机制 |

## 三、生产环境建议

| 场景 | 原生 MyBatis 缓存表现 | 建议 |
|---|---|---|
| **单机、低并发、读多写少** | 可用，但收益有限 | 可开启，配置 `readOnly="true"` 和 `LRU` 淘汰 |
| **高并发（>1000 QPS）** | 锁竞争严重，延迟高，吞吐低 | **关闭原生二级缓存**，改用外部缓存 |
| **分布式/集群部署** | 各节点缓存不一致，数据漂移 | 必须禁用，改用 Redis 等分布式缓存 |
| **微服务/云原生** | 内存不可控，无监控手段 | 禁用，使用 Spring Cache + Caffeine/Redis 分层 |

## 四、推荐替代架构

```
Service 层
  ├─ Spring Cache (@Cacheable) ──→ Caffeine (本地L1，纳秒级)
  └─ Redis (分布式L2，微秒级)
MyBatis 层
  └─ 关闭二级缓存，仅保留一级缓存（会话级）
```

**优势**：
- **一级缓存**：会话内重复查询去重，不跨线程，无锁竞争
- **Spring Cache + Redis**：业务层控制缓存 key、过期策略和失效逻辑，避免 MyBatis 自动 flush 的不可预测性

## 五、核心结论

| 指标 | 原生 MyBatis 缓存 | 生产替代方案 |
|---|---|---|
| **内存占用** | 一级缓存可控；二级缓存无上限，有 OOM 风险 | Redis / Caffeine 有容量限制和淘汰策略 |
| **并发吞吐** | 二级缓存 `synchronized` 全局锁，高并发下吞吐塌陷 | Caffeine 分段锁，Redis 单线程事件循环 |
| **延迟** | 锁竞争导致 P99 飙升 | 本地 ~1μs，Redis ~1ms，延迟稳定 |

## 关联知识

- [[MyBatis核心机制]] — 一/二级缓存完整机制（归属 EMRG-ORM与持久层）
- [[多级缓存一致性]] — Service 层缓存架构设计（归属 EMRG-Cache）
- [[缓存失效策略]] — 缓存过期与淘汰策略（归属 EMRG-Cache）

## 来源

- 本次对话整理自 Kimi 对话记录（2026-05-28）
