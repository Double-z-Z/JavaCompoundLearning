# 阶段 3: 可靠上下文传递

## 目标

让 LLM 在每个 stage 都能稳定地：
1. 读到上游产物的**实际内容**（不是只传路径）
2. 在失败时**自动重试**而不直接挂死
3. 长时间无响应时**有超时保护**
4. 用户能看到**清晰的失败原因**

## 现状

### 1. Prompt 只传路径

```python
# 当前 exec_backend 的 Prompt 构造
prompt = agent["prompt"].format(
    user_requirements=run.user_requirements,
    context=json.dumps(ctx_rel, ensure_ascii=False),
    design_path=design_rel,   # 相对路径字符串
    code_path=code_rel,       # 相对路径字符串
    ...
)
```

LLM 只知道"产物在 `./docs/design.md`"，但需要自己读。**实际** LLM 时常因路径错误、编码问题读不到内容，导致后续产物偏离设计。

### 2. 无重试

```python
# pipeline_task 当前 except 分支
except BaseException as e:
    sys.stderr.write(f"[pipeline_task] CRASH: ...")
    run.status = "failed"
    append_history({"action": "pipeline_error", "detail": str(e)})
    raise
```

API 错误一次就挂，用户只能手动重试。

### 3. 超时已实现但粗糙

`STAGE_TIMEOUT_S = 600`（10 分钟）硬编码，对简单 stage 太长，对长 stage 可能不够。

## 设计方案

### 改动范围

| # | 改动 | 文件 | 内容 |
|---|------|------|------|
| 1 | 产物内容注入 Prompt（前 8000 字符） | `sidecar.py` `exec_backend` | 读取上游产物，截断后注入 `{design_content}` 等模板变量 |
| 2 | Prompt 模板支持新变量 | `sidecar.py` | `AGENT_CONFIG[*]["prompt"]` 增加 `{design_content}` `{code_content}` 等占位符 |
| 3 | 错误重试（指数退避） | `sidecar.py` `pipeline_task` | catch 异常后判断是否可重试（API 错误/超时）→ 重试 2 次，间隔 2s/4s |
| 4 | 每 stage 独立超时配置 | `sidecar.py` | `WORKFLOW stages[*]` 新增 `timeout_s` 字段，覆盖默认 `STAGE_TIMEOUT_S` |
| 5 | 失败降级 UI | `ui.html` | 阶段失败时显示"人工介入"按钮 + 错误详情（从 `pipeline_error` 事件读取） |
| 6 | 阶段可重入（reject 路径） | `sidecar.py` | 已在 stage 配置中支持 `on_reject`，但需要 UX 改进：显示上轮产物，LLM 收到 reject 理由 |

### 关键技术点

#### 1. 产物内容注入

```python
# exec_backend 产物内容注入逻辑
def inject_artifact_content(prompt: str, ctx: dict, workspace: str) -> str:
    """读取上游产物内容（前 8000 字符）注入 Prompt"""
    injections = {}
    for key in ("design", "code", "review"):
        path = ctx.get(key)
        if not path or path == "failed":
            continue
        full_path = os.path.join(workspace, path) if not os.path.isabs(path) else path
        if not os.path.exists(full_path):
            continue
        try:
            with open(full_path, encoding="utf-8") as f:
                content = f.read(8000)
            if len(content) == 8000:
                content += "\n\n... [truncated, see full file at " + path + "]"
            injections[f"{key}_content"] = content
        except Exception as e:
            injections[f"{key}_content"] = f"[Error reading {path}: {e}]"
    return prompt.format(**injections)
```

#### 2. 重试逻辑

```python
async def exec_with_retry(run, stage, max_retries=2):
    backoff = 2  # seconds
    for attempt in range(max_retries + 1):
        try:
            await exec_backend(run, stage)
            return  # 成功
        except RuntimeError as e:
            if attempt == max_retries:
                raise  # 最后一次也失败，传播
            # 记录重试事件
            append_history(run, {
                "action": "retry",
                "stage": stage["id"],
                "attempt": attempt + 1,
                "next_retry_in": backoff,
                "error": str(e),
            })
            await asyncio.sleep(backoff)
            backoff *= 2  # 2s → 4s
```

#### 3. 每 stage 超时配置

```python
# WORKFLOW stages
{"id": "coder", "mode": "backend", "output": "src/feature.py", "key": "code", "timeout_s": 300}
{"id": "reviewer", "mode": "backend", "output": "docs/review.md", "key": "review", "timeout_s": 180}
```

## 工期估计

**4h**

| 子任务 | 工时 |
|--------|------|
| 产物内容注入 | 1.5h |
| 错误重试逻辑 | 1h |
| 每 stage 超时配置 | 30min |
| 失败降级 UI | 1h |

## 验收标准

- coder 阶段 prompt 包含 `design.md` 实际内容（前 8000 字符）
- LLM 看到内容后生成的代码与设计强相关（人工可对比验证）
- API 错误时自动重试 2 次（间隔 2s/4s），UI 日志显示 `retry` 事件
- 长任务（>5min）被超时强制终止并显示原因
- Reject 路径中 LLM 收到上轮 reject 理由，改进后产物

## 关联阶段

- **阶段 2.5 多文件产物**：注入策略要适配目录结构
- **阶段 4 完整闭环**：每个 backend stage 都跑 `exec_with_retry` 而非 `exec_backend`
- **阶段 5 编排层外化**：YAML 中要支持 `timeout_s` / `max_retries` 字段
