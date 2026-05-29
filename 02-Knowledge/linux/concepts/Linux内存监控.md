---
type: atomic-note
id: CONCEPT-linux-memory-monitor
created: 2026-05-29
updated: 2026-05-29
tags: [Linux, 监控]
status: 🌿
mastery: 45
related_emrg: [EMRG-Linux]
related_goal: [GOAL-Linux系统管理]
---

# Linux 内存监控

## 一句话定义
通过 `free`、`vmstat`、`/proc/meminfo` 获取系统级内存状态，区分真正的可用内存和可回收缓存。

## 核心理解

### free -h：第一眼
```
              total   used   free   buff/cache   available
Mem:          7.7Gi   1.6Gi  4.6Gi  1.8Gi        6.2Gi
```
**free 低不等于内存不够**——Linux 积极使用空闲内存做文件缓存。真正要看的列是 **available**（free + 可回收的 cache）。

### Active / Inactive LRU 双链表
内核维护两个链表：
- **Active**：最近访问过的页（热页）
- **Inactive**：一段时间没碰的页（冷页，回收候选）

```
Active(anon):   进程自己的内存（堆、栈）
Inactive(anon): 冷匿名页 → 回收要先 swap
Active(file):   文件缓存，最近用过
Inactive(file): 文件缓存，冷 → 回收直接丢弃（磁盘有原件）
```

回收时优先丢 file 页——零成本，磁盘上还有。

### vmstat 关键列
```
r  b  swpd  free  buff  cache  si  so  bi  bo
0  0  0     4.6G  2.7M  1.8G   0   0   0   0
```
| 列 | 含义 | 告警 |
|----|------|------|
| swpd | 已换出量(KB) | > 0 说明发生过换出 |
| si | swap in 速率 | > 0 持续 = 在换页 |
| so | swap out 速率 | > 0 连续 = 内存不够 |
| bi/bo | 块 IO 速率 | 飙高 = IO 压力 |
| b | D 状态进程数 | > 0 持续 = IO 瓶颈 |

### Dirty + Writeback
Dirty 页 = 内存里改了但还没写回磁盘。内核后台线程 pdflush 周期性刷盘。Dirty 飙升 = 写入压力大。

## 关键关联
- [[Linux进程监控]] - 进程 RES 指标需要结合系统级 available 判断
- [[Swap与zram]] - swap 是匿名页回收的后备存储
- [[虚拟内存与物理内存]] - Active/Inactive 是虚拟内存系统 LRU 的具体实现

## 代码与实践
```bash
free -h                      # 快速一览
vmstat 1 5                   # 看 si/so 是否活跃
cat /proc/meminfo | head -20 # 最详细
```

## 来源
- 对话：2026-05-29 W1 Linux 冲刺内存监控

---
## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿 理解
- 更新记录：
  - 2026-05-29: mastery=45（完成 free/vmstat//proc/meminfo 实操，理解 Active/Inactive LRU 和 anon vs file 页区别）
