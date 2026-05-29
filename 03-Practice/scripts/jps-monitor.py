#!/usr/bin/env python3
"""Java process monitor — Python 接管参数解析和排序，bash 只负责 ps 管道."""

import argparse
import subprocess
import sys


def get_java_procs(cmd: str = "java") -> list[dict]:
    """调用 ps，返回结构化数据。"""
    proc = subprocess.run(
        ["ps", "-C", cmd, "-o", "pid=,pcpu=,pmem=,etime=,args="],
        capture_output=True, text=True
    )
    if proc.returncode != 0 or not proc.stdout.strip():
        return []

    procs = []
    for line in proc.stdout.strip().splitlines():
        parts = line.split(maxsplit=4)
        if len(parts) < 5:
            continue
        procs.append({
            "pid": parts[0],
            "cpu": float(parts[1]),
            "mem": float(parts[2]),
            "etime": parts[3],
            "cmd": parts[4],
        })
    return procs


def etime_to_sec(etime: str) -> int:
    """dd-hh:mm:ss → 总秒数."""
    parts = etime.split("-", 1)
    if len(parts) == 2:
        days = int(parts[0])
        hms = parts[1]
    else:
        days = 0
        hms = parts[0]
    h, m, s = map(int, hms.split(":"))
    return days * 86400 + h * 3600 + m * 60 + s


def main():
    parser = argparse.ArgumentParser(description="Java process monitor")
    parser.add_argument("-s", "--sort", default="pid",
                        choices=["pid", "cpu", "mem", "etime"])
    parser.add_argument("-n", "--top", type=int, default=0)
    parser.add_argument("-c", "--cmd", default="java")
    args = parser.parse_args()

    procs = get_java_procs(args.cmd)
    if not procs:
        print("No processes found.")
        sys.exit(0)

    key = args.sort if args.sort != "etime" else "etime_sec"
    for p in procs:
        p["etime_sec"] = etime_to_sec(p["etime"])
    procs.sort(key=lambda p: p[key], reverse=True)

    if args.top > 0:
        procs = procs[: args.top]

    print(f"{'PID':<8} {'CPU%':<5} {'MEM%':<5} {'ELAPSED':<10} COMMAND")
    for p in procs:
        print(f"{p['pid']:<8} {p['cpu']:<5} {p['mem']:<5} {p['etime']:<10} {p['cmd']}")


if __name__ == "__main__":
    main()
