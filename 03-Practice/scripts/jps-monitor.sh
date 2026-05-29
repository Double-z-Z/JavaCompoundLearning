#!/bin/bash
# V4-final: bash 做它擅长的事——管道串联，零分支

ps -C "${1:-java}" -o pid=,pcpu=,pmem=,etime=,args= 2>/dev/null \
| awk '
BEGIN { printf "%-8s %-5s %-5s %-10s %s\n", "PID", "CPU%", "MEM%", "ELAPSED", "COMMAND" }
{
    pid=$1; cpu=$2; mem=$3; etime=$4; $1=$2=$3=$4=""
    gsub(/^[[:space:]]+/, "", $0)
    printf "%-8s %-5s %-5s %-10s %s\n", pid, cpu, mem, etime, $0
}
END { if (NR == 0) print "No processes found." }'
