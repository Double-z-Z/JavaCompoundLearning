# 阶段 4: 完整闭环

## 目标

把前 3 个阶段的成果（交互式架构、多文件产物、可靠上下文）整合成**端到端的完整流程**，并提供：
- 阶段间衔接的 UX 体验
- Gate 审批时产物预览
- Diff 显示
- 最终产物回写到项目根

## 现状

阶段 1.5 已实现 workflow 串联：

```
[architect] → [direction-gate:human] → [coder] → [review-gate:auto] → [reviewer] → [merge-gate:human]
```

但有以下 UX 缺陷：
- Gate 审批时只能看到 stage 名称，看不到产物
- Coder/Reveiwer 都是后端模式，UI 只能看日志，没法"看代码"
- Merge gate 审批通过后没有任何动作（产物还留在 `.agent-workspace/`）

## 设计方案

### 整体流程

```
┌─────────────────────────────────────────────────────────────────┐
│  TUI/IDE: 用户输入需求 → 启动工作流                              │
└─────────────────────────────┬───────────────────────────────────┘
                              ▼
                    [interactive architect]  ← 阶段 2
                              │ 产物: docs/design/*
                              ▼
                    [direction-gate: human]  ← 用户审批
                              │ reject → 回到 architect
                              │ approve
                              ▼
                    [backend coder]  ← 阶段 3 (retry + 内容注入)
                              │ 产物: src/feature.py
                              ▼
                    [review-gate: auto]  ← file_exists 检查
                              │ pass
                              ▼
                    [backend reviewer]  ← 阶段 3
                              │ 产物: docs/review.md
                              ▼
                    [merge-gate: human]  ← 用户审批
                              │ reject → 回到 coder
                              │ approve → 产物回写到项目根
                              ▼
                          [done]
```

### 改动范围

| # | 改动 | 文件 | 内容 |
|---|------|------|------|
| 1 | Gate 审批时显示产物预览 | `ui.html` | `gatebox` 组件显示当前 stage 的产物列表（点击展开 Markdown/代码） |
| 2 | Diff 显示 | `ui.html` | merge-gate 时显示"项目根 vs workspace" 的 diff（用简单的文本 diff 库或自实现） |
| 3 | 产物回写到项目根 | `sidecar.py` | merge-gate 审批通过后，`shutil.copy2(workspace_out, project_out)`（之前已实现但被回退，重新启用 + 优化） |
| 4 | Stage 实时进度 | `ui.html` | 后端阶段（coder/reviewer）执行时，stages-list 中对应 stage 高亮"▶" |
| 5 | Reject 后回到对应 stage + 显示上轮 reject 理由 | `ui.html` + `sidecar.py` | `on_reject: <stage_idx>` + `run.ctx["reject_note"]` 注入下轮 prompt |
| 6 | 工作流完成时汇总 | `ui.html` | 完成后显示 "✓ 全部完成" 横幅 + 列出所有产物路径 |
| 7 | 阶段间 SSE 流畅衔接 | `sidecar.py` | 上一阶段 `stage_done` 事件后立即推进到下一阶段，避免 SSE 中断 |

### 关键技术点

#### 1. Gate 审批 UI 增强

```html
<div id="gatebox">
    <h3>🔒 Gate 审批: <span id="gname"></span></h3>
    <p>本阶段产物：</p>
    <div id="gate-artifacts">
        <!-- 自动列出当前 stage 产物，支持点击展开 -->
        <button onclick="preview('docs/design.md')">📄 design.md</button>
        <button onclick="preview('docs/design/arch.md')">📄 arch.md</button>
    </div>
    <div id="gate-diff" style="display:none">
        <h4>与上版 diff：</h4>
        <pre id="diff-content"></pre>
    </div>
    <button onclick="act('approve')">✓ 通过</button>
    <button class="reject" onclick="act('reject')">✗ 驳回</button>
    <textarea id="reject-note" placeholder="驳回理由（可选）"></textarea>
</div>
```

#### 2. 产物回写（merge gate 通过时）

```python
# merge-gate handler 中
if decision == "approve" and artifact:
    # 回写到项目根
    for f in (artifact if isinstance(artifact, list) else [artifact]):
        src = os.path.join(OPEN_CODE_WORKSPACE, f)
        dst = os.path.join(PROJECT_PATH, f)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy2(src, dst)
        append_history({
            "action": "artifact_promoted",
            "from": src,
            "to": dst,
        })
```

#### 3. Reject 路径 + 理由传递

```python
# handle_gate 中
if dec == "reject":
    run.ctx[f"reject_note:{gid}"] = note  # 存入 ctx
    # 回到 on_reject 阶段
    run.idx = stage.get("on_reject", 0)
    # 下次 exec_backend 时，prompt 注入 reject_note
```

下轮 prompt 模板示例：

```python
# architect prompt
prompt = """
...
{reject_note}

请基于上述驳回理由重新设计。
"""
```

## 工期估计

**6h**

| 子任务 | 工时 |
|--------|------|
| Gate 审批 UI 增强（含产物预览） | 1.5h |
| Diff 显示组件 | 1h |
| 产物回写到项目根 | 1h |
| Reject 路径 + 理由注入 | 1h |
| 完成时汇总横幅 | 30min |
| 端到端测试（多轮 reject 循环） | 1h |

## 验收标准

- 启动工作流 → interactive architect（阶段 2）→ 输入需求 → `/done` → 看到 direction-gate 审批界面，**含产物预览**
- 审批通过 → 自动进入 coder（后端）→ 实时显示 `building / thinking / writing` 进度
- Coder 完成后 → auto-gate 通过 → reviewer 阶段
- Reviewer 完成后 → merge-gate 审批，**含 diff 显示**
- merge-gate 审批通过 → 产物从 `.agent-workspace/` 复制到项目根 → 显示"全部完成"横幅
- 任一阶段 reject → 显示驳回理由 → 自动回到 `on_reject` 阶段 → prompt 包含理由
- 多次 reject 循环：architect → gate reject → architect（看到上轮 reject 理由）→ 重新设计

## 关联阶段

- **阶段 2 交互式架构**：architect 是 interactive 模式的核心
- **阶段 2.5 多文件产物**：所有 stage 产物可能是目录
- **阶段 3 可靠上下文**：每个 stage 走 `exec_with_retry`
- **阶段 5 编排层外化**：YAML 中定义完整工作流的 gate reject 路径
