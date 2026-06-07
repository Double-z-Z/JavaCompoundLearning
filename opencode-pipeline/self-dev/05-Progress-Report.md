# OpenCode Pipeline Orchestrator — 当前进度报告

**报告日期**: 2026-06-06 02:52
**项目状态**: V0.1 MVP 验证阶段
**已投入时间**: ~6 小时 (超出预期 30 分钟原型目标)

---

## 1. 已完成工作

### 1.1 环境搭建 (100%)

| 任务 | 状态 | 备注 |
|------|------|------|
| PVE Fedora 虚拟机准备 | ✅ | 已有 JavaLearning 工作空间 |
| OpenCode 安装与配置 | ✅ | `~/.config/opencode/opencode.jsonc` 生效 |
| 多模型接入验证 | ✅ | DeepSeek / MiniMax / Kimi 共 15+ 模型可用 |
| API Key 管理 | ✅ | `~/.opencode-pipeline/secrets.json` + 环境变量注入 |
| FastAPI + Uvicorn 环境 | ✅ | Python 3.14, 用户级 pip 安装 |

### 1.2 编排引擎核心 (90%)

| 任务 | 状态 | 验证结果 |
|------|------|---------|
| Run 状态机 | ✅ | `run-{hex}` 创建，idx/status/ctx/history 完整 |
| 五阶段工作流定义 | ✅ | architect → direction-gate → coder → review-gate → reviewer → merge-gate |
| Gate 人类审批 | ✅ | 左 Pane 按钮 [Approve/Reject]，asyncio.Event 恢复 |
| Gate 自动判断 | ✅ | review-gate 检查文件存在性 |
| 回退机制 | ✅ | reject 设置 idx=on_reject，循环执行 |
| 状态持久化 | ✅ | `~/.opencode-pipeline/state.json` 跨重启保留 |
| 文件系统产物监控 | ✅ | `.agent-workspace/` 生成后复制到标准路径 |

### 1.3 Agent 执行层 (70%)

| 任务 | 状态 | 验证结果 |
|------|------|---------|
| OpenCode CLI 调用 | ✅ | `opencode run --model <model> "<prompt>"` 正常工作 |
| 产物写入检测 | ✅ | `docs/design.md` 成功生成 (FastAPI 路由设计) |
| 实时流式日志 | ⚠️ | 能捕获 stdout/stderr，但 ANSI 码未清理，标签混乱 |
| 上下文路径注入 | ✅ | Prompt 中 `{design_path}` 等变量替换正常 |
| 多模型路由 | ✅ | architect=reasoner, coder=v4-flash, reviewer=kimi-coding |

### 1.4 UI 层 (60%)

| 任务 | 状态 | 验证结果 |
|------|------|---------|
| 单页 HTML 控制塔 | ✅ | 左(控制)/中(预览)/右(日志) 三栏布局 |
| 启动工作流 | ✅ | POST /api/start，Run 创建成功 |
| 状态轮询 | ✅ | 800ms 轮询 `/api/runs/{rid}`，状态更新正常 |
| Gate 审批按钮 | ✅ | Approve/Reject 调用 API，状态机恢复 |
| 文件预览 | ✅ | `/api/files?path=docs/design.md` 返回内容 |
| 日志显示 | ⚠️ | 能显示，但格式混乱 (ANSI 码、时间戳、err/out 标签错误) |
| 预览按钮 | ❌ | 硬编码路径，大小写/后缀不匹配 (Feature.java vs feature.py) |

---

## 2. 待完成工作 (阻塞项)

### P0 阻塞 (无法完成一次完整闭环)

| 阻塞项 | 描述 | 影响 | 预计解决 |
|--------|------|------|---------|
| **交互式 Stage 缺失** | architect/reviewer 仍是 backend 模式，人类无法对话补充需求 | 核心需求未满足，无法"协作开发" | 需 PTY 或暂停指引方案 |
| **产物内容注入** | Prompt 只传路径，LLM 可能读取失败 | coder 看不到 design.md 内容，代码可能偏离架构 | 2h |
| **错误处理空白** | OpenCode 失败无重试，流程挂死 | 一次 API 错误就失败 | 2h |
| **coder 产物为空** | 实际测试中 coder 阶段产物未生成 | 无法进入 review 阶段 | 待验证 (可能是路径/检测问题) |

### P1 重要 (影响体验)

| 阻塞项 | 描述 | 影响 |
|--------|------|------|
| 日志格式混乱 | ANSI 码、时间戳、err/out 标签混杂 | 无法判断 Agent 正在做什么 |
| UI 预览按钮硬编码 | 路径写死，与产物实际路径不一致 | 404 错误 |
| 无 SSE 实时推送 | 依赖 800ms 轮询 | 延迟感明显 |
| 无超时机制 | Agent 可能无限运行 | 资源浪费 |

### P2 优化 (V0.2)

| 阻塞项 | 描述 |
|--------|------|
| 评估器框架 | review-gate 只有 file_exists，无 Lint/Test |
| 动态模型路由 | 未按任务类型自动切换模型 |
| 知识沉淀 | 无决策日志提取到 Obsidian |
| Tauri 控制塔 | 仍为 HTML，无系统通知/内嵌终端 |

---

## 3. 已知问题清单

### 3.1 已解决

| 问题 | 解决方式 | 验证 |
|------|---------|------|
| OpenCode 配置被覆盖 | 使用 `opencode.jsonc` + 只读权限 | ✅ |
| `Authorization Required` | 环境变量注入 + `secrets.json` | ✅ |
| 产物写入外部目录被拒 | 使用 `.agent-workspace/` 隔离工作空间 | ✅ |
| 模型列表加载 | 修正 `provider` 单数键 + `models` 嵌套 | ✅ |
| 文件路径大小写 | 统一使用小写 `feature.py` | ✅ |

### 3.2 待解决

| 问题 ID | 问题描述 | 优先级 | 临时绕过 |
|---------|---------|--------|---------|
| ISS-001 | 交互式 Stage 无法输入 | P0 | 手动在终端运行 `opencode` 后点击完成 |
| ISS-002 | 日志 ANSI 码未清理 | P1 | 无 |
| ISS-003 | coder 产物检测失败 | P0 | 手动检查 `.agent-workspace/src/` |
| ISS-004 | 预览按钮硬编码路径 | P1 | 手动修改 URL 参数 |
| ISS-005 | 无错误重试 | P0 | 重启 Sidecar |
| ISS-006 | 上下文仅路径注入 | P1 | 无 |
| ISS-007 | 无 SSE 推送 | P1 | 轮询 |
| ISS-008 | OpenCode CLI 输出格式不稳定 | P1 | 正则解析 |

---

## 4. 下一步行动建议

### 方案 A: 立即交付给其他 Agent (推荐)

将本文档 + 代码仓库交给新 Agent，明确任务边界：

**任务 1**: 修复 P0 阻塞 (交互式 Stage 方案选型 + 产物内容注入 + 错误重试)
**任务 2**: 清理日志格式 (ANSI 清理 + 分类着色)
**任务 3**: 实现 SSE 实时推送 (替代轮询)
**交付物**: V0.2 可用版本，能完成一次 architect(交互) → coder → reviewer 完整闭环

### 方案 B: 继续自行推进

聚焦最小修复：
1. 今晚: 产物内容注入 + 错误重试 (4h)
2. 明天: 交互式 Stage 暂停指引方案 (2h)
3. 周末: SSE + 日志清理 (4h)

### 方案 C: 降低目标

接受当前为"后台批处理"模式：
- architect 阶段：人类手动在终端运行 `opencode`，产物保存后点击"完成"
- 后续阶段：自动执行
- 价值：验证 Gate 审批 + 回退机制，但非真正的"协作开发"

---

## 5. 代码资产清单

| 文件 | 路径 | 状态 | 行数 |
|------|------|------|------|
| sidecar.py | `~/workspace/JavaLearning/opencode-pipeline/sidecar.py` | 可运行 | ~350 |
| ui.html | `~/workspace/JavaLearning/opencode-pipeline/ui.html` | 可运行 | ~150 |
| run.sh | `~/workspace/JavaLearning/opencode-pipeline/run.sh` | 可运行 | ~10 |
| secrets.json | `~/.opencode-pipeline/secrets.json` | 配置完成 | — |
| opencode.jsonc | `~/.config/opencode/opencode.jsonc` | 配置完成 | ~40 |

---

## 6. 关键决策待确认

| 决策 | 选项 | 建议 |
|------|------|------|
| 交互式 Stage 方案 | A. PTY 内嵌 (6h) / B. 暂停指引 (30min) | **先 B 验证需求，再 A 提升体验** |
| 产物注入策略 | A. 全文注入 (可能超限) / B. 摘要注入 (可能丢失细节) | **A 先试试，超限再 B** |
| 日志输出源 | A. 清理 stderr / B. 丢弃日志只看文件 | **A，日志是调试关键** |
| 下一步开发主体 | A. 本 Agent 继续 / B. 移交新 Agent | **B，原型已超期** |

---

**结论**: V0.1 MVP 核心机制已验证，但存在 P0 阻塞（交互式输入、错误处理）。建议将完整上下文移交新 Agent，目标在 2-3 天内完成 V0.2 可用版本。

---
版本: 2026-06-06
