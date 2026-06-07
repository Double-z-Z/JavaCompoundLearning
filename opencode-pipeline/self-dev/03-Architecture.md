# OpenCode Pipeline Orchestrator — 架构设计文档

## 1. 总体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      用户层 (Windows 笔记本)                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │  控制面板     │  │  交互终端     │  │  监控面板     │         │
│  │  (左 Pane)    │  │  (中 Pane)    │  │  (右 Pane)    │         │
│  │  • 流程图     │  │  • xterm.js   │  │  • 后台进度   │         │
│  │  • Gate 审批  │  │  • PTY 转发   │  │  • 日志流     │         │
│  │  • 快速命令   │  │  • 实时对话   │  │  • 历史决策   │         │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘         │
└─────────┼─────────────────┼─────────────────┼─────────────────┘
          │                 │                 │
          └─────────────────┴─────────────────┘
                            │ HTTP / WebSocket (局域网)
┌───────────────────────────▼─────────────────────────────────────┐
│                   Sidecar 编排引擎 (PVE Fedora)                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  FastAPI HTTP Server (Port 8080)                           │ │
│  │  ├── /api/start           启动工作流                         │ │
│  │  ├── /api/runs/{rid}      查询状态                           │ │
│  │  ├── /api/runs/{rid}/gates/{gid}/action  Gate 审批           │ │
│  │  ├── /api/files           文件代理                           │ │
│  │  └── /ws/interactive/{rid}  WebSocket 交互终端 (V0.2)      │ │
│  └─────────────────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  Pipeline Engine (状态机)                                    │ │
│  │  ├── Run 实例管理 (rid, idx, status, ctx)                    │ │
│  │  ├── Stage 执行器 (backend / interactive)                    │ │
│  │  ├── Gate 控制器 (human / auto)                             │ │
│  │  ├── 上下文总线 (ctx[key] = artifact_path)                   │ │
│  │  └── 循环控制 (max_iterations, 防死锁)                       │ │
│  └─────────────────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  持久化层                                                    │ │
│  │  ├── state.json          工作流状态 (JSON)                  │ │
│  │  ├── secrets.json        API Keys (600 权限)                │ │
│  │  └── decision-log/       审批历史 (按 run 分目录)            │ │
│  └─────────────────────────────────────────────────────────────┘ │
└───────────────────────────┬─────────────────────────────────────┘
                            │ subprocess / CLI
┌───────────────────────────▼─────────────────────────────────────┐
│              OpenCode 实例 (多会话)                               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐               │
│  │ Session A   │  │ Session B   │  │ Session C   │               │
│  │ Architect   │  │ Coder       │  │ Reviewer    │               │
│  │ (deepseek-  │  │ (deepseek-  │  │ (kimi-      │               │
│  │  reasoner)  │  │  v4-flash)  │  │  coding)    │               │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘               │
│         │                │                │                     │
│         └────────────────┴────────────────┘                     │
│                          │                                      │
│  ┌───────────────────────▼───────────────────────┐              │
│  │  OpenCode HTTP Server (localhost:3000)         │              │
│  │  • 后台会话管理                                 │              │
│  │  • LSP 桥接                                    │              │
│  │  • Git 快照                                    │              │
│  └───────────────────────────────────────────────┘              │
└─────────────────────────────────────────────────────────────────┘
                            │ API
┌───────────────────────────▼─────────────────────────────────────┐
│                     模型提供商                                   │
│  DeepSeek  │  MiniMax  │  Kimi  │  Ollama (本地)                │
└─────────────────────────────────────────────────────────────────┘
```

## 2. 核心模块设计

### 2.1 Pipeline Engine (状态机)

```python
class Run:
    rid: str           # 唯一标识
    idx: int           # 当前阶段索引
    status: str        # running / waiting_gate / interactive / completed / failed
    ctx: dict          # 上下文总线 {design: path, code: path, review: path}
    history: list      # 事件日志
    events: dict       # asyncio.Event 用于暂停/恢复
    _task: asyncio.Task  # 后台协程
```

**状态流转**:
```
[start] ──► architect ──► direction-gate ──► coder ──► review-gate ──► reviewer ──► merge-gate ──► [end]
                ▲                │                      │                      │
                └────────────────┴──────────────────────┴──────────────────────┘
                                      (reject 回退)
```

### 2.2 Stage 执行器

| 模式 | 实现 | 人类可见性 | 适用阶段 |
|------|------|-----------|---------|
| backend | `asyncio.create_subprocess_exec(opencode run ...)` | 监控面板看进度日志 | coder |
| interactive | PTY 进程 + WebSocket 转发 | 中 Pane 实时对话 | architect, reviewer |
| auto-gate | 本地评估脚本 | 无 | review-gate |
| human-gate | asyncio.Event 暂停 + UI 审批 | 左 Pane 审批按钮 | direction-gate, merge-gate |

### 2.3 上下文总线

```
~/workspace/JavaLearning/opencode-pipeline/
├── .agent-workspace/          # OpenCode 隔离工作空间
│   ├── docs/
│   │   └── design.md          # architect 产物
│   └── src/
│       └── feature.py         # coder 产物
├── docs/
│   └── design.md              # 复制到标准路径 (UI 读取)
├── src/
│   └── feature.py             # 复制到标准路径
├── .opencode-pipeline/
│   ├── state.json             # 状态机持久化
│   ├── secrets.json           # API Keys
│   └── decision-log/          # 审批历史
└── sidecar.py                 # 编排引擎
```

**上下文注入策略**:
- 路径注入：Prompt 中传入 `{design_path}`
- 内容注入（V0.2）：读取文件前 8000 字符注入 Prompt
- 决策注入：Gate 审批理由注入下游 Agent

### 2.4 Gate 控制器

```yaml
Gate 类型:
  human:
    触发: Sidecar 设置 status=waiting_gate, 创建 asyncio.Event
    通知: 系统通知 + UI 高亮
    恢复: 人类点击 [Approve/Reject/Rework], Event.set()
    记录: {decision, note, timestamp, user} → decision-log/

  auto:
    触发: Sidecar 执行评估脚本
    评估器: 文件存在检查 / 测试通过率 / Lint 分数
    回退: 未通过则 idx = on_reject
    记录: {metrics, pass/fail, timestamp} → decision-log/
```

### 2.5 模型路由

```json
{
  "architect":  {"model": "deepseek/deepseek-reasoner", "provider": "deepseek"},
  "coder":      {"model": "deepseek/deepseek-v4-flash", "provider": "deepseek"},
  "reviewer":   {"model": "kimi-coding/kimi-coding-chat", "provider": "kimi-coding"}
}
```

**动态路由规则（V0.2）**:
- 探索性任务 → 本地 Ollama (零成本)
- 架构决策 → DeepSeek Reasoner (高质量)
- 编码实现 → DeepSeek Flash (平衡)
- 审查 → Kimi Coding (长上下文)

## 3. 数据流设计

### 3.1 一次完整迭代

```
1. 人类点击"启动工作流" (Tauri/浏览器)
   └── POST /api/start
       └── Sidecar 创建 Run, 状态机启动

2. Stage: architect (interactive, V0.2)
   └── Sidecar 启动 PTY + OpenCode TUI
   └── 人类在中 Pane 与 DeepSeek Reasoner 对话
   └── 人类说 "/done", Agent 写入 docs/design.md
   └── Sidecar 检测到产物, 状态机推进

3. Gate: direction-gate (human)
   └── Sidecar 暂停, 推送系统通知
   └── 人类在左 Pane 预览 design.md, 点击 [Approve]
   └── 状态机推进

4. Stage: coder (backend)
   └── Sidecar 启动 subprocess: opencode run --model deepseek-v4-flash
   └── 实时流式日志推送到右 Pane (SSE)
   └── Agent 读取 design.md, 写入 src/feature.py
   └── Sidecar 检测产物, 状态机推进

5. Gate: review-gate (auto)
   └── Sidecar 执行评估: 文件存在? 语法正确?
   └── 通过, 自动推进

6. Stage: reviewer (backend/interactive)
   └── 同 coder, 产出 docs/review.md

7. Gate: merge-gate (human)
   └── 人类预览 diff, 点击 [Merge]
   └── Sidecar 执行 git merge, 流程结束

8. Optional: knowledge-capture (backend)
   └── 提取决策日志, 写入 Obsidian Inbox
```

### 3.2 错误处理流

```
Agent 失败 (API 错误 / 超时)
   └── 重试 1 次 (指数退避)
   └── 仍失败 → status=failed, 人类介入

Gate 驳回 (Reject)
   └── idx = on_reject (如 merge-gate → coder)
   └── 上下文注入驳回理由
   └── 重新执行目标 Stage

迭代超限 (max_iterations=3)
   └── 状态机暂停, 提示人类升级决策
```

## 4. 接口设计

### 4.1 Sidecar HTTP API

| 端点 | 方法 | 描述 |
|------|------|------|
| `/` | GET | 返回 UI HTML |
| `/api/start` | POST | 启动新工作流, 返回 Run snapshot |
| `/api/runs/{rid}` | GET | 查询 Run 状态 |
| `/api/runs/{rid}/stream` | GET (SSE) | 实时流式状态推送 |
| `/api/runs/{rid}/gates/{gid}/action` | POST | Gate 审批 (action=approve/reject/rework) |
| `/api/files?path=...` | GET | 文件内容代理 (越界防护) |
| `/ws/interactive/{rid}` | WebSocket | 交互式终端 PTY 转发 (V0.2) |

### 4.2 Sidecar → OpenCode 调用

```bash
# Backend 模式
opencode run --model deepseek/deepseek-v4-flash "<prompt>"

# Interactive 模式 (V0.2)
opencode --model deepseek/deepseek-reasoner  # 启动 TUI
```

### 4.3 OpenCode 配置契约

`~/.config/opencode/opencode.jsonc`:
```jsonc
{
  "$schema": "https://opencode.ai/config.json",
  "model": "deepseek/deepseek-chat",
  "provider": {
    "deepseek": {
      "npm": "@ai-sdk/openai-compatible",
      "options": {
        "baseURL": "https://api.deepseek.com",
        "apiKey": "{env:DEEPSEEK_API_KEY}"
      }
    }
  }
}
```

## 5. 安全设计

| 层面 | 措施 |
|------|------|
| API Key | secrets.json, chmod 600, 环境变量注入子进程 |
| 文件越界 | `/api/files` 路径规范化, 禁止 `../` |
| 代码执行 | OpenCode 自带沙箱, Sidecar 不直接执行 LLM 生成的代码 |
| 网络 | 仅监听局域网 (0.0.0.0:8080), 无公网暴露 |
| 审计 | 所有 Gate 审批 + Agent 操作写入 decision-log |

---
版本: 2026-06-06
