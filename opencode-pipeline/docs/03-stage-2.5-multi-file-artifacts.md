# 阶段 2.5: 多文件产物结构

## 目标

将每个 stage 的单文件产物升级为目录结构（如 `docs/design/` 包含多份 .md），并提供树状浏览 UI，匹配真实项目的多文件形态。

## 现状

```python
# WORKFLOW stages - 当前
{"id": "architect", "output": "docs/design.md", "key": "design"}    # 单文件
{"id": "coder",     "output": "src/feature.py", "key": "code"}      # 单文件
{"id": "reviewer",  "output": "docs/review.md", "key": "review"}      # 单文件
```

`exec_backend` 产物检测仅支持单文件：

```python
if os.path.exists(workspace_out):
    out_file = workspace_out
```

UI 中产物按钮区是平铺的：

```
[📐 设计]  [💻 代码]  [🔍 审查]
```

## 设计方案

### 核心：output 字段支持 `type: "file"` / `type: "dir"`

```python
# 新格式
{"id": "architect", "output": "docs/design/", "key": "design", "type": "dir"}
{"id": "coder",     "output": "src/feature/", "key": "code",   "type": "dir"}
{"id": "reviewer",  "output": "docs/review/",  "key": "review", "type": "dir"}
```

向后兼容：省略 `type` 字段时默认 `"file"`（保持现有行为）。

### 改动范围

| # | 改动 | 文件 | 内容 |
|---|------|------|------|
| 1 | `output` 字段支持 `type: "dir"` | `sidecar.py` | `WORKFLOW stages` 新增 `type` 字段 |
| 2 | 产物检测支持目录 | `sidecar.py` | `exec_backend` 末尾：`os.path.isdir(workspace_out)` + 检查非空 + 扫描 `os.listdir()` |
| 3 | `Run.ctx` / `snapshot().artifacts` 支持目录 | `sidecar.py` | 文件场景存单路径字符串；目录场景存路径列表 |
| 4 | `/api/files` 支持目录路径返回清单 | `sidecar.py` | `?path=docs/design/` → `{"type":"dir", "files":["docs/design/arch.md", ...]}` |
| 5 | Prompt 告知 LLM 产出目录 | `sidecar.py` | `AGENT_CONFIG["architect"]["prompt"]` 改写为"请将设计拆分为多个文件" |
| 6 | UI 产物按钮树状 | `ui.html` | 目录节点 → 点击展开子文件列表（`renderArtifacts` 改为递归渲染） |
| 7 | Prompt 注入目录结构感知 | `sidecar.py` | `ctx_rel` 中目录路径展开为文件清单，方便下游 stage 引用具体文件 |

### 关键技术点

#### 1. 目录检测（防 LLM 写出空目录）

```python
if type == "dir":
    if os.path.isdir(workspace_out) and os.listdir(workspace_out):
        out_dir = workspace_out
        out_files = sorted(os.listdir(workspace_out))
    else:
        out_dir = None
```

#### 2. 文件 vs 目录的统一快照接口

```python
# snapshot().artifacts 现在可能是：
#   {"design": "docs/design.md"}            # 单文件
#   {"design": ["docs/design/a.md", ...]}  # 目录（文件列表）
# UI 端根据类型渲染不同形态
```

#### 3. 下游 stage prompt 注入

```python
# coder 阶段读取 architect 产物时
if isinstance(run.ctx.get("design"), list):
    design_files = run.ctx["design"]   # 多个文件路径
    design_content = "\n\n".join(
        f"--- {p} ---\n{open(os.path.join(OPEN_CODE_WORKSPACE, p)).read()[:4000]}"
        for p in design_files
    )
else:
    design_content = open(...).read()[:8000]  # 单文件
```

## 工期估计

**4h**

| 子任务 | 工时 |
|--------|------|
| 数据模型 + 产物检测扩展 | 1h |
| `/api/files` 目录支持 | 30min |
| Prompt 改造（architect 拆多文件） | 1h |
| UI 树状渲染 | 1h |
| 测试 + 端到端验证 | 30min |

## 验收标准

- 启动 architect 阶段，LLM 产出 `docs/design/{architecture,api,data-flow,exception}.md` 多个文件
- UI 中 "📐 设计" 按钮点击后展开子文件列表
- 点击子文件 → 渲染对应 Markdown
- coder 阶段能正确读取 architect 的多文件内容
- 单文件 stage（兼容旧配置）继续工作正常

## 关联阶段

- **阶段 2 交互式架构**：interactive 模式下用户能看到"现在在写哪个文件"
- **阶段 3 上下文传递**：coder 阶段需要更智能的"读哪些文件、读前多少字"
- **阶段 5 编排层外化**：YAML 配置中需要为每个 stage 指定文件/目录类型
