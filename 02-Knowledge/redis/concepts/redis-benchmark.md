---
created: 2026-05-04
updated: 2026-05-04
topic: Redis性能基准测试
tags: [redis, benchmark, performance]
mastery: 45
---

# Redis性能基准测试

## 核心概念

### 网络路径对性能的影响

| 测试环境 | 路径 | 实测QPS | 瓶颈分析 |
|---------|------|---------|---------|
| 开发机直连 | 路由器 → PVE内网 | 20W+ | CPU主频/内存带宽 |
| WSL | Windows → 路由器 → PVE | 3W | WSL虚拟机限制 |

**关键认知**：
- WSL本地测试Redis，瓶颈是WSL本身而非网络
- WSL受Windows宿主机调度影响，无法独占CPU
- WSL只适合功能验证，不适合性能基准测试

### 性能基准建立原则

1. **真实内网性能** = 开发机直连结果（20W+ QPS）
2. **虚拟机环境** = 参考值，受宿主机影响大
3. **跨网络路径** = 需要考虑多层转发开销

## 关联知识

- [[Redis单线程事件循环模型]] - 为什么单线程也能高效
- [[Redis Cluster水平扩展]] - 多节点线性扩展原理
- [[hot-content-counter设计]] - 缓存架构设计

## 相关项目

- [[redis-counter-service]] - 秒杀库存扣减项目