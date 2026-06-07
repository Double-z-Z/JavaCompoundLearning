# 阶段 2: 交互式架构设计

## 目标

将架构设计阶段（architect）从 `backend` 模式（opencode 后台自动跑）升级为 `interactive` 模式——人类可以在中间实时对话、补充需求、调整方向，**像在 IDE 中与 LLM 协作一样**完成设计。

## 现状

```python
# WORKFLOW stages[0] - 当前
{"id": "architect", "mode": "backend", "output": "docs/design.md", "key": "design"}
```

`exec_backend` 通过 `subprocess` 启动 `opencode run --dir ...`，opencode 自主完成所有思考与生成，产物一次性写入。人类只能"看"不能"介入"。

## 设计方案

### 核心：PTY + WebSocket 双向转发

```
┌────────────┐   WebSocket   ┌──────────────┐
│   浏览器    │ ◄──────────► │   Sidecar    │
│ xterm.js UI │  PTY bytes    │  ws 端点     │
└────────────┘               └──────┬───────┘
                                  │ ptyprocess
                                  ▼
                          ┌──────────────┐
                          │   opencode   │
                          │  interactive │
                          │      TUI     │
                          └──────────────┘
```

### 改动范围

| # | 改动 | 文件 | 内容 |
|---|------|------|------|
| 1 | 新增 `mode: "interactive"` 模式 | `sidecar.py` | `WORKFLOW stages.mode` 支持 `backend` / `interactive` / `human-gate` / `auto-gate` |
| 2 | `exec_interactive(run, stage)` 函数 | `sidecar.py` | 使用 `ptyprocess` 启动 `opencode --interactive`，分配 PTY 伪终端 |
| 3 | WebSocket PTY 转发 | `sidecar.py` | 新增 `ws://.../interactive/{rid}` 端点；server-to-client: PTY → xterm.js；client-to-server: xterm.js 输入 → PTY stdin |
| 4 | `/done` 完成信号检测 | `sidecar.py` | 在 PTY 输出流中匹配 `> done` / `/done` / 约定信号，标记 stage 完成并推进状态机到 `direction-gate` |
| 5 | 状态机集成 | `sidecar.py` | `pipeline_task` 遇到 `mode: "interactive"` 时调用 `exec_interactive` 而非 `exec_backend` |
| 6 | xterm.js 终端嵌入 | `ui.html` | 中 Pane `<div id="terminal">` 容器；引入 `xterm.js` + `xterm-addon-fit` |
| 7 | 中 Pane 模式切换 | `ui.html` | 当前 stage 为 interactive 时显示终端，后端模式时显示产物/日志（已有逻辑） |
| 8 | 终端生命周期 | `ui.html` | stage 开始时连接 WS，stage 结束/done 时关闭并切换回普通视图 |

### 备选方案（渐进式交付）

由于 PTY 方案工程量较大，**推荐分两步**：

**B 方案（30 min，先验证需求）**：
- `interactive` 模式 = 后端运行 + 终端提示
- UI 中 Pane 显示："请在外部终端运行：`cd /home/.../.agent-workspace && opencode run ...`"
- 完成后用户回 UI 点击"我已完成，继续 gate"
- 验证"用户确实需要交互模式"后再升级

**A 方案（6h，完整体验）**：
- PTY + WebSocket 完整双向同步
- 用户在浏览器中即可看到 LLM 思考过程、输入补充、确认

建议：**先 B 验证**。

## 工期估计

| 方案 | 工期 | 风险 |
|------|------|------|
| A（PTY 完整版） | 6h | 跨平台 PTY 兼容、opencode 交互模式稳定性 |
| B（暂停指引） | 30min | 用户体验差但能验证需求 |

**建议先 B（30min）→ 收集反馈 → 决定是否升级 A**

## 验收标准

- 启动 architect 阶段后，UI 中 Pane 切换为可交互终端
- 用户在终端中发送消息，opencode 实时响应
- 用户输入约定信号（如 `/done`），architect 阶段自动结束，状态推进到 `direction-gate`
- 关闭 WebSocket 不会产生僵尸 PTY 进程（用 `engine["procs"]` 跟踪）
- 历史 Run 重新 `inspect` interactive 阶段时，能看到 PTY 输出日志（截取为文本）

## 关联阶段

- **阶段 4 完整闭环**：阶段 2 的 interactive 模式实现后，阶段 4 的"interactive architect → human gate → backend coder" 串联才有意义
- **阶段 2.5 多文件产物**：architect 阶段在交互模式下确认产出 `docs/design/` 目录（而非单文件）后，阶段 2.5 的 UI 树状浏览才能在交互结束后立即可见
