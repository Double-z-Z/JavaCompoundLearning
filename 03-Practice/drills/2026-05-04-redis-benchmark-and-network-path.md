---
created: 2026-05-04
topic: Redis性能基准测试与网络路径分析
tags: [redis, benchmark, 网络编程, WSL]
---

# 2026-05-04 Redis性能基准测试与网络路径分析

## 核心结论

### 1. Redis单节点性能基线
- **开发机直连PVE内网**：20W+ QPS
- 瓶颈主要在CPU主频和内存带宽

### 2. WSL性能表现
- WSL本地测试：~3.5W QPS
- **瓶颈分析**：WSL虚拟机本身限制，非网络问题
  - 受Windows宿主机调度影响
  - Redis单线程极度依赖CPU主频
  - WSL无法独占CPU资源

### 3. 网络路径对比
| 路径 | 实测QPS | 说明 |
|------|---------|------|
| 开发机 → PVE直连 | 20W+ | 真实内网性能 |
| WSL → Windows → 路由器 → PVE | 3W | 多层转发 |

### 4. 关键认知
- **WSL只适合功能验证**，不适合做性能基准测试
- wrk在WSL里是原生Linux二进制，测试结果比Windows工具更准确
- 真正性能测试以开发机直连结果为准

## 待验证
- [ ] 应用层WRK测试（PVE上的Redis HTTP接口）
- [ ] Lua脚本扣减库存的正确性验证
- [ ] 多节点Redis Cluster线性扩展验证

## 关联知识
- [[Redis单线程事件循环模型]]
- [[Redis Cluster水平扩展]]
- [[hot-content-counter设计]]