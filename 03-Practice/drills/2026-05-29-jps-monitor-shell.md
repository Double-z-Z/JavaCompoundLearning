---
created: 2026-05-29
type: drill
tags: [Linux, Shell]
difficulty: 🌿
related_concepts:
  - [[Shell管道与工具链]]
  - [[Linux进程监控]]
---

# jps-monitor.sh 脚本开发

> 目标：从零构建 Java 进程监控脚本，体会 bash 管道的正确边界

## 练习内容

### 需求
用 bash 实现 Java 进程列表 + 汇总统计，单条管道完成。

### 演进过程
- **V1**：`pgrep -a java`，变量赋值 + 条件判断
- **V2**：`ps -C java -o` 自定义列格式
- **V3**：`sort` + `awk` 汇总行（总进程数、总内存、CPU Top）
- **V4**：全 awk 接管格式化+汇总，一条管道串到底

### 最终版本（V4）
```bash
#!/bin/bash
ps -C "${1:-java}" -o pid=,pcpu=,pmem=,etime=,args= 2>/dev/null \
| awk 'BEGIN { printf "%-8s %-5s %-5s %-10s %s\n", "PID", "CPU%", "MEM%", "ELAPSED", "COMMAND" }
{ pid=$1; cpu=$2; mem=$3; etime=$4; $1=$2=$3=$4=""; gsub(/^[[:space:]]+/, "", $0)
  printf "%-8s %-5s %-5s %-10s %s\n", pid, cpu, mem, etime, $0 }
END { if (NR == 0) print "No processes found." }'
```

## 复盘总结

### 学到的
- bash 的最佳形态是零分支零循环的单管道，出现 getopts/declare -A → 换 Python
- `awk` 的 BEGIN/BODY/END 模型足够处理格式化+汇总，不需要 sort|head|wc 多进程
- `ps -o` 自定义输出是脚本化进程监控的标准入口

### 关联知识
- [[Shell管道与工具链]] - 本练习是该概念的直接实践
- [[Linux进程监控]] - ps 是核心数据源

---
## 🤖 AI评价

### 完成质量
- 功能实现：完整
- 代码质量：良好（V4 零分支纯管道，符合 bash 最佳实践）
- 概念应用：正确

### 对掌握度的影响
- [[Shell管道与工具链]]: +10分
- [[Linux进程监控]]: +5分
