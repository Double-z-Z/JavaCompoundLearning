# 项目分阶段计划 Roadmap

> 截至 2026-06-07
> 已完成阶段 1（可观测基础）；本计划涵盖阶段 2 → 阶段 5

## 阶段总览

| 阶段 | 名称 | 状态 | 工期 | 优先级 | 文档 |
|------|------|------|------|--------|------|
| 1 | 可观测基础 | ✅ 已完成（含多次迭代） | — | — | 保留在 session 历史中 |
| **2** | **交互式架构设计** | 🔲 未开始 | 6h / 30min | **P0** | [02-stage-2-interactive-architect.md](02-stage-2-interactive-architect.md) |
| **2.5** | **多文件产物结构** | 🔲 未开始 | 4h | P1 | [03-stage-2.5-multi-file-artifacts.md](03-stage-2.5-multi-file-artifacts.md) |
| **3** | **可靠上下文传递** | 🔲 未开始 | 4h | P1 | [04-stage-3-reliable-context.md](04-stage-3-reliable-context.md) |
| **4** | **完整闭环** | 🔲 未开始 | 6h | P1 | [05-stage-4-end-to-end.md](05-stage-4-end-to-end.md) |
| **5** | **YAML 编排层外化** | 🔲 未开始 | 6h | P2 | [06-stage-5-yaml-orchestration.md](06-stage-5-yaml-orchestration.md) |

**剩余总工时**：约 26h（若阶段 2 选 A 方案；选 B 方案则 20.5h）

## 推荐执行顺序

按依赖关系自上而下：

```
阶段 2 (交互式)  ──→  阶段 2.5 (多文件)  ──→  阶段 3 (可靠上下文)  ──→  阶段 4 (完整闭环)  ──→  阶段 5 (YAML)
     P0                    P1                      P1                       P1                    P2
   6h / 30min            4h                       4h                       6h                   6h
```

**关键依赖**：
- 阶段 2.5 依赖阶段 2（interact 模式产生多文件）
- 阶段 3 依赖阶段 2.5（内容注入需支持多文件读取）
- 阶段 4 依赖前 3 阶段全部完成
- 阶段 5 是收尾（YAML schema 涵盖所有 stage 行为）

## 各阶段一句话总结

### 阶段 2: 交互式架构设计
PTY + WebSocket 双向转发，让用户在浏览器中与架构师 Agent 实时对话。备选 B 方案 30min 验证需求后再升级 A 方案 6h 完整体验。

### 阶段 2.5: 多文件产物结构
`output` 字段支持 `type: "dir"`；UI 产物按钮改树状（目录→子文件）；Prompt 告知 LLM 拆分为多文件。

### 阶段 3: 可靠上下文传递
产物内容（前 8000 字符）注入 Prompt；错误重试（指数退避 2 次）；每 stage 独立超时；失败降级 UI。

### 阶段 4: 完整闭环
Gate 审批时显示产物预览 + diff；merge-gate 通过后产物回写到项目根；reject 路径 + 理由注入；多轮 reject 循环。

### 阶段 5: YAML 编排层外化
`WORKFLOW` 和 `AGENT_CONFIG` 抽到 `config/workflows/*.yaml` 和 `config/agents/*.yaml`；模型路由；`POST /api/reload` 热加载；多项目隔离。

## 风险与缓解

| 风险 | 阶段 | 缓解 |
|------|------|------|
| PTY 跨平台兼容 | 2 | Linux 优先，macOS/Windows 后续适配 |
| 多文件目录检测（防空目录） | 2.5 | 检测非空 + 至少含 1 个匹配后缀的文件 |
| 错误重试掩盖真实问题 | 3 | 重试事件写 history，UI 可见；3 次仍失败抛异常 |
| Gate 审批 UX 复杂 | 4 | 分阶段交付，先基础版（仅产物预览），再 diff |
| YAML schema 频繁变更 | 5 | 加 schema 版本号；reload 失败时保留旧配置 |

## 验证策略

每阶段交付时：
1. 单元测试（如有 Python 工具函数）
2. 端到端 Playwright 测试（`test/test_sidecar.py` 模板可复用）
3. 真实 LLM 跑通完整流程（确认产物质量）
4. 截图 + 录屏作为交付证据

## 进度记录

| 日期 | 阶段 | 事件 |
|------|------|------|
| 2026-06-06 | 1 | 完成可观测基础（含多次迭代修复） |
| 2026-06-07 | 1.5 | 完成 `INSPECTING` / `continue` / `delete` UX 重构 + append_history 无限递归修复 + opencode `--dir` 隔离 + 多道 OOM/僵尸防御 |
| 2026-06-07 | 计划 | 阶段 2~5 写入 `docs/` |

## 关联文件

- `sidecar.py`（945 行）：FastAPI 编排引擎
- `ui.html`（~700 行）：单文件控制塔 UI
- `run.sh`：启动脚本
- `.agent-workspace/`：opencode `--dir` 隔离工作空间
- `test/test_sidecar.py`：Playwright 端到端测试
- `docs/`：本计划目录
- `self-dev/`：项目级规划文档（PRD、市场分析、架构等）
