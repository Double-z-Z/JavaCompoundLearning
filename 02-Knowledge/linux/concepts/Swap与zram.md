---
type: atomic-note
id: CONCEPT-swap-zram
created: 2026-05-29
updated: 2026-05-29
tags: [Linux, 内存]
status: 🌿
mastery: 40
related_emrg: [EMRG-Linux]
related_goal: [GOAL-Linux系统管理]
---

# Swap 与 zram

## 一句话定义
Swap 是匿名页在后备存储，zram 用压缩内存做 swap 后端。换出 ≠ 内存满——是内核 LRU 回收的常规机制。

## 核心理解

### 触发机制：水位线
```
高水位 → 低水位 → 最小水位 → OOM
          ↑ kswapd 在此启动回收
```
不是 free 归零才换出。kswapd 被唤醒后扫描 Inactive(anon) 链表，按 LRU 优先换出最冷页。

### 换出过程
```
1. 选 Inactive(anon) 尾部页
2. 写入 swap 设备 → swpd ↑, so > 0
3. 页表 entry 标为"已换出"
4. 进程后来访问 → 缺页中断 → swap in → si > 0
```

### swappiness (0-100)
- 0：尽量不换 anon，先回收 file 页
- 60：默认值
- 100：狠狠换 anon

### zram：压缩内存做 swap
传统 swap → 磁盘（~ms 延迟）。zram 在内存中分配区域，写入前压缩：
- 典型 Java 堆压缩比 3:1~4:1
- 全零页压缩比可达 50:1（lzo-rle 算法）
- 换入延迟 < μs（vs 磁盘 ms）
- 代价：吃 CPU（压缩/解压）

### 与 Java 的关系
Java 应用不希望 swap——GC 遍历堆时 swap in 回来 → 几百 ms 暂停。生产环境通常 `swappiness=0` 或关 swap，宁可 OOM 重启。

## 关键关联
- [[Linux内存监控]] - free/vmstat 的 swap 列是观测窗口
- [[虚拟内存与物理内存]] - swap 是 anon 页回收的后备存储
- [[Linux进程监控]] - top 中某进程 RES 骤降可能是被换出

## 代码与实践
```bash
cat /proc/sys/vm/swappiness    # 当前倾向
zramctl                        # zram 当前用量和压缩比
vmstat 1 5                     # si/so 持续 > 0 = 在换页
```

## 来源
- 对话：2026-05-29 W1 Linux 冲刺 swap 专题

---
## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿 理解
- 更新记录：
  - 2026-05-29: mastery=40（理解 swap 触发机制、zram 压缩原理、与 Java GC 的关系）
