---
type: atomic-note
id: CONCEPT-linux-network-tools
created: 2026-05-29
updated: 2026-05-29
tags: [Linux, 网络]
status: 🌿
mastery: 35
related_emrg: [EMRG-Linux]
related_goal: [GOAL-Linux系统管理]
---

# Linux 网络工具

## 一句话定义
`s` 查看 socket 连接状态，`lsof -i` 按端口/进程追踪网络占用。

## 核心理解

### ss — socket statistics（替代 netstat）
```bash
ss -tlnp           # TCP 监听端口 + 进程名
ss -s              # 汇总统计
ss -tan            # 所有 TCP 连接（含 ESTABLISHED）
```

**输出列：**
```
State   Recv-Q  Send-Q  Local Address:Port   Peer Address:Port
LISTEN  0       128     0.0.0.0:22            0.0.0.0:*
```

| 列 | 含义 |
|----|------|
| State | LISTEN(监听) / ESTAB(已建立) / TIME-WAIT(等待关闭) |
| Recv-Q / Send-Q | 接收/发送缓冲区积压（非 0 = 处理不过来） |
| 0.0.0.0 | 监听所有网卡；127.0.0.1 只监听本机 |

### lsof — 文件→进程映射
```bash
lsof -i :22        # 谁在用 22 端口
lsof -p 1234       # PID 1234 打开了哪些文件/端口
lsof /var/log/syslog  # 哪个进程在写这个文件
```
Linux 一切皆文件，lsof 就是"文件→进程"的字典。

## 关键关联
- [[Linux进程监控]] - ss/lsof 定位端口→进程后，用 ps 看该进程的指标
- [[Linux-IO监控]] - 网络 IO 瓶颈也会反映在进程的 D 状态上

## 代码与实践
```bash
# 最常用组合
ss -tlnp                              # 谁在监听
ss -tan | grep ESTAB | wc -l          # 活跃连接数
sudo lsof -i :8080                    # 8080 端口被谁占
```

## 来源
- 对话：2026-05-29 W1 Linux 冲刺网络工具

---
## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿 理解
- 更新记录：
  - 2026-05-29: mastery=35（完成 ss/lsof 实操，能定位端口归属）
