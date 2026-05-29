---
created: 2026-05-04
updated: 2026-05-04
tags: [Linux, Shell]
status: 🌿
mastery: 45
related_emrg: [EMRG-Linux]
related_goal: [GOAL-Linux系统管理]
---

# Shell 重定向与管道

## 一句话定义
Shell 通过文件描述符（0/1/2）和重定向操作符（`>`、`>>`、`<`、`<<`）控制输入输出流向。

## 核心理解

### 文件描述符

| 描述符 | 含义 | 默认绑定 |
|-------|------|---------|
| 0 | 标准输入 stdin | 键盘 |
| 1 | 标准输出 stdout | 终端 |
| 2 | 标准错误 stderr | 终端 |

### 输出重定向

| 操作符 | 含义 | 示例 |
|-------|------|------|
| `>` | 覆盖写入（先清空） | `echo "hi" > file.txt` |
| `>>` | 追加写入（末尾添加） | `echo "hi" >> file.txt` |
| `2>` | 重定向标准错误 | `grep "x" f 2> err.log` |
| `&>` | 同时重定向 stdout 和 stderr | `cmd &> all.log` |
| `> file 2>&1` | 等价于 `&>`，顺序重要 | 先1后2 |

### 输入重定向

| 操作符 | 含义 | 示例 |
|-------|------|------|
| `<` | 从文件读取输入 | `wc -l < file.txt` |
| `<<` | Here Document 多行输入 | 见下方 |
| `<>` | 读写同一文件 | `exec 3<> file` |

### Here Document (Heredoc)

```bash
# 原样输出（推荐）- 单引号包围界定符
cat << 'EOF'
$HOME        # 不展开变量，原样输出
"string"
EOF

# 展开变量 - 无引号
cat << EOF
$HOME        # 展开为用户家目录
"string"
EOF
```

### 管道 |

```bash
cmd1 | cmd2   # cmd1 的输出作为 cmd2 的输入
echo "hi" | wc -c   # 输出 6（5字符 + 换行）
```

### /dev/null 黑洞

```bash
cmd > /dev/null 2>&1   # 丢弃所有输出
cmd 2>/dev/null        # 只丢弃错误
```

### 顺序陷阱

```bash
# 正确：stdout → file.txt，然后 stderr → stdout(已指向file)
cmd > file.txt 2>&1

# 错误：stderr 先指向 stdout（终端），再重定向 stdout
cmd 2>&1 > file.txt   # 错误输出仍到终端
```

## 关键关联

- [[Redis-命令行工具]] - redis-cli 的输出重定向
- [[Ansible-Playbook]] -  playbook 日志重定向
- [[WRK压测]] - wrk 输出重定向到文件

## 代码与实践

```bash
# 覆盖写入
echo "hello" > file.txt

# 追加
echo "line2" >> file.txt

# 标准错误重定向
grep "error" log.txt 2> err.log

# 同时重定向
make > build.log 2>&1

# Heredoc 原样输出
cat > ~/workspace/post.lua << 'EOF'
wrk.method = "POST"
EOF

# Heredoc 展开变量
cat > config << EOF
HOME=$HOME
EOF

# 从文件读取
sort < input.txt > output.txt

# 丢弃所有输出
command > /dev/null 2>&1
```

## 来源

- 项目：[[redis-counter-service]]
- 对话：2026-05-04 Shell重定向学习

---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿 理解
- 更新记录：
  - 2026-05-04: mastery=45 (初建笔记，通过实例理解)

### 建议下一步
1. 在实际压测中练习输出重定向
2. 理解管道与 xargs 的配合使用