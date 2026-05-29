---
type: atomic-note
id: CONCEPT-linux-io-monitor
created: 2026-05-29
updated: 2026-05-29
tags: [Linux, 监控]
status: 🌿
mastery: 40
related_emrg: [EMRG-Linux]
related_goal: [GOAL-Linux系统管理]
---

# Linux IO 监控

## 一句话定义
通过 `iostat -x` 获取磁盘的 IOPS、吞吐量、延迟、排队深度和繁忙率，定位 IO 瓶颈。

## 核心理解

### iostat -x 关键列
```
Device   r/s   w/s   rkB/s  wkB/s   %util   r_await  w_await  aqu-sz
sda      0.90  2.51   45     84     0.05    1.76     0.70     0.00
```

| 列 | 含义 | 告警阈值 |
|----|------|---------|
| r/s, w/s | 每秒读写次数 | — |
| rkB/s, wkB/s | 每秒读写吞吐量 | — |
| **%util** | 设备繁忙占比 | HDD > 80%, SSD > 95% |
| **r_await** | 读延迟(ms) | HDD > 20ms, SSD > 5ms |
| **w_await** | 写延迟(ms) | 同上 |
| **aqu-sz** | 平均排队深度 | > 1 持续 → 请求堆积 |

### 合并率 — IO 栈优化
`rrqm%` / `wrqm%` 表示相邻请求被合并的比例。btrfs 写合并率可达 50%+，是好事——IO 调度器在帮你省操作次数。

### DISCARD 列
`d/s` / `dkB/s` 是 SSD TRIM：btrfs 通知 SSD"这些块不用了"，SSD 才能提前擦除。TRIM 频繁是 btrfs 的正常行为。

### 三步定位法回顾
```
1. top → load average 高？%wa 高？
2. free -h → swap 在涨？
3. iostat -x → %util 高？await 大？
```
区分 CPU 瓶颈 / 内存不足 / IO 瓶颈。

## 关键关联
- [[Linux进程监控]] - top 发现 %wa 高 → iostat 确认具体磁盘
- [[Linux内存监控]] - swap 活动会体现在 iostat 的写 IO 上
- [[DMA与IOMMU]] - DMA 是 IO 数据流绕过 CPU 的硬件基础

## 代码与实践
```bash
iostat -x 1 2          # 每秒采样，2 次（第 1 行是开机均值，第 2 行是瞬时）
iostat -x -m 1         # 以 MB 为单位
```

## 来源
- 对话：2026-05-29 W1 Linux 冲刺 IO 监控

---
## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿 理解
- 更新记录：
  - 2026-05-29: mastery=40（完成 iostat -x 实操，能解读关键列和告警阈值）
