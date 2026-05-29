---
type: atomic-note
id: CONCEPT-linux-process-monitor
created: 2026-05-29
updated: 2026-05-29
tags: [Linux, 监控]
status: 🌿
mastery: 45
related_emrg: [EMRG-Linux]
related_goal: [GOAL-Linux系统管理]
---

# Linux 进程监控

## 一句话定义
通过 `top`、`ps`、`/proc` 三套工具获取进程的 CPU、内存、状态指标，快速定位性能瓶颈。

## 核心理解

### top：实时全景
```
load average: 0.15, 0.10, 0.05   ← 1/5/15min 平均负载，> CPU核数 = 过载
VIRT = 虚拟内存总量（含未分配的 mmap 空间）
RES  = 物理内存占用（真正要看的值）
SHR  = 共享内存（RES - SHR ≈ 进程独占内存）
S    = R(运行) S(睡眠) D(不可中断IO) Z(僵尸)
```

**关键洞察**：load average 高但 %CPU 低 → D 状态进程堆积 → IO 瓶颈。

### ps：脚本快照
```bash
ps -C java -o pid=,pcpu=,pmem=,etime=,args=   # 自定义列，= 取消表头
ps aux --sort=-%mem | head -6                   # 按内存排序 TOP N
```
`ps` 是执行瞬间快照，CPU 值是进程整个生命周期的平均，与 `top` 的瞬时值不同。

### /proc：原始数据源
`/proc/PID/status` 的 VmSize(VIRT)、VmRSS(RES)、VmHWM(RES峰值)、VmData(堆)、VmStk(栈) 是 `top`/`ps` 的数据来源。VmRSS 一直爬升刷新 VmHWM → 可能内存泄漏。

### 三步定位法
```
1. top → load average / %CPU / %wa
2. free -h → available 够吗？swap 在涨吗？
3. iostat -x → %util / await
```
区分 CPU 瓶颈、内存不足、IO 问题。

## 关键关联
- [[Linux内存监控]] - 进程内存指标需要结合系统级内存状态判断
- [[虚拟内存与物理内存]] - VIRT vs RES 的差距根源在 lazy allocation 和 mmap
- [[Shell管道与工具链]] - ps 输出通过管道交给 awk/sort 处理

## 我的误区与疑问
- ❌ 最初以为 VIRT 几十G是内存泄漏 → 实际是 64 位进程正常行为，mmap 预占地址空间

## 代码与实践
```bash
# 一键看 Java 进程内存
ps -C java -o pid=,%cpu=,%mem=,rss=,vsz=,args=

# 按 RES 降序的 TOP 5
ps aux --sort=-rss | head -6
```

## 来源
- 对话：2026-05-29 W1 Linux 冲刺

---
## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿 理解
- 更新记录：
  - 2026-05-29: mastery=45（完成 top/ps//proc 实操，能独立解读指标）
