---
title: Sentinel 系统自适应限流
tags: [Sentinel, 自适应, 系统保护]
mastery: 60
related_emrg: [[EMRG-Sentinel]]
related:
  - "[[Sentinel-熔断机制|Sentinel 熔断机制]]"
  - "[[Sentinel-流量控制效果|Sentinel 流量控制效果]]"
---

# Sentinel 系统自适应限流

## 概述

基于系统负载自动调整限流阈值，实现智能化保护。

## 监控指标

### 四大核心指标（OR 关系）

| 指标 | 用途 | 说明 |
|------|------|------|
| **CPU 使用率** | 直接反映计算压力 | 整机 CPU，非进程级 |
| **平均 RT** | 反映 IO/锁竞争/GC 停顿 | 端到端延迟 |
| **并发线程数** | 反映系统 backlog | 排队等待的请求数 |
| **系统 Load** | Linux 系统负载 | 1min/5min/15min 平均值 |

### 为什么不用内存？

限流无法释放内存，内存问题需依赖 JVM 配置（-Xmx）和 GC。

---

## 防震荡机制

### 滞后效应（Hysteresis）

使用高低双阈值避免频繁切换：

```
高阈值 = 85%（开始限流）
低阈值 = 65%（停止限流）
```

**效果**：系统在两个阈值之间平滑过渡，避免震荡。

---

## 架构设计

### 配置示例

```java
SystemRule rule = new SystemRule();
rule.setHighestCpuUsage(0.8);    // CPU > 80% 触发
rule.setAvgRt(500);             // RT > 500ms 触发
rule.setMaxThread(1000);         // 并发 > 1000 触发
rule.setQps(10000);              // QPS > 10000 触发
```

### 决策流程

```
新请求到达
    ↓
检查 CPU > highThreshold? ──Yes──→ 限流
    ↓ No
检查 RT > maxRt? ──Yes──→ 限流
    ↓ No
检查 并发数 > maxThread? ──Yes──→ 限流
    ↓ No
检查 QPS > maxQps? ──Yes──→ 限流
    ↓ No
✅ 放行
```

---

## 策略配置建议

| 服务类型 | CPU | RT | 并发数 |
|---------|-----|----|--------|
| 保守型（支付） | 65% | 200ms | 500 |
| 普通型（查询） | 80% | 1000ms | 2000 |
| 容忍型（日志） | 90% | 5000ms | 5000 |

---

## 知识关联

1. **与熔断机制相关**：慢调用比例策略也基于 RT 监控
2. **与流量控制效果相关**：可配合 Warm Up 使用，实现多层保护
3. **与 Context 相关**：结合来源信息实现差异化限流