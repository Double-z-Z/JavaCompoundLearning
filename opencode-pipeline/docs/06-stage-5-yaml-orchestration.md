# 阶段 5: 多 LLM 分角色编排（YAML 外化）

## 目标

将目前硬编码在 `sidecar.py` 中的 `WORKFLOW` 和 `AGENT_CONFIG` 外化到 YAML 文件，让用户可以：
1. 不用改 Python 代码就能定义新工作流
2. 切换不同 LLM 角色（reasoner / flash / coding / 本地 ollama）
3. 热加载配置（无需重启 sidecar）
4. 多项目隔离（不同项目用不同工作流配置）

## 现状

```python
# sidecar.py 中硬编码
WORKFLOW = {
    "name": "feature-dev",
    "max_iterations": 3,
    "stages": [
        {"id": "architect", "mode": "backend", "output": "docs/design.md", "key": "design"},
        {"id": "direction-gate", "type": "gate", "mode": "human", "on_reject": 0},
        # ...
    ],
}

AGENT_CONFIG = {
    "architect": {"model": "minimax-cn/MiniMax-M3", "prompt": "..."},
    "coder":     {"model": "minimax-cn/MiniMax-M3", "prompt": "..."},
    "reviewer":  {"model": "minimax-cn/MiniMax-M3", "prompt": "..."},
}
```

## 设计方案

### 配置目录结构

```
/home/dz-fedora/workspace/JavaLearning/opencode-pipeline/
├── sidecar.py
├── config/                          # 外化配置目录
│   ├── workflows/
│   │   ├── default.yaml            # 默认工作流
│   │   ├── research-paper.yaml      # 论文研究流程
│   │   └── bug-fix.yaml             # Bug 修复流程
│   ├── agents/
│   │   ├── default.yaml             # 默认 agent 配置
│   │   └── heavy-tasks.yaml         # 重活专用（reasoner 模型）
│   └── active.yaml                  # 软链接/引用，指向当前激活的工作流
```

### 改动范围

| # | 改动 | 文件 | 内容 |
|---|------|------|------|
| 1 | 引入 `PyYAML` 依赖 | `sidecar.py` / `requirements.txt` | `pip install pyyaml` |
| 2 | YAML 加载函数 | `sidecar.py` | `load_workflow(path)` / `load_agents(path)` |
| 3 | `WORKFLOW` 改为运行时变量 | `sidecar.py` | 移除硬编码 dict，从 `config/active.yaml` 加载 |
| 4 | `AGENT_CONFIG` 改为运行时变量 | `sidecar.py` | 同上 |
| 5 | `POST /api/reload` 端点 | `sidecar.py` | 热加载（不影响正在运行的 Run） |
| 6 | `GET /api/config` 端点 | `sidecar.py` | 返回当前激活的工作流 + agent 配置（UI 显示） |
| 7 | 模型路由策略 | `sidecar.py` | 简单路由表：探索性任务→ollama / 架构→reasoner / 编码→flash / 审查→kimi |
| 8 | 多项目隔离 | `sidecar.py` | 工作流 YAML 中可指定 `project_root`，sidecar 在该目录下创建 `.agent-workspace/` |
| 9 | UI 工作流选择器 | `ui.html` | 启动前可下拉选择工作流模板 |

### YAML 配置示例

#### `config/workflows/default.yaml`

```yaml
name: feature-dev
max_iterations: 3
stages:
  - id: architect
    mode: interactive    # 阶段 2: 交互式
    type: dir           # 阶段 2.5: 多文件
    output: docs/design/
    key: design
    timeout_s: 600
  - id: direction-gate
    type: gate
    mode: human
    on_reject: 0        # 回到 architect
  - id: coder
    mode: backend
    type: file
    output: src/feature.py
    key: code
    timeout_s: 600
    max_retries: 2      # 阶段 3
  - id: review-gate
    type: gate
    mode: auto
    condition: file_exists
  - id: reviewer
    mode: backend
    type: file
    output: docs/review.md
    key: review
    timeout_s: 300
  - id: merge-gate
    type: gate
    mode: human
    on_reject: 2        # 回到 coder
    on_approve_promote: true   # 阶段 4: 审批通过后产物回写项目根
```

#### `config/agents/default.yaml`

```yaml
architect:
  model: deepseek/deepseek-reasoner   # 阶段 5: 模型路由
  role: reasoning
  prompt: |
    你是资深架构师...
  inject_artifacts: ["design"]        # 注入上游产物
  inject_max_chars: 8000

coder:
  model: deepseek/deepseek-v4-flash
  role: coding
  prompt: |
    你是资深 Python/FastAPI 开发...
  inject_artifacts: ["design"]
  inject_max_chars: 12000

reviewer:
  model: kimi/kimi-coding
  role: review
  prompt: |
    你是严格的代码审查员...
  inject_artifacts: ["design", "code"]

# 模型路由规则
routing:
  explorer: ollama/qwen2.5-coder        # 零成本探索
  reasoner: deepseek/deepseek-reasoner  # 高质量架构
  fast: deepseek/deepseek-v4-flash      # 平衡速度
  longctx: kimi/kimi-coding             # 长上下文审查
```

### 关键技术点

#### 1. 热加载

```python
@app.post("/api/reload")
async def reload_config():
    """热加载工作流和 agent 配置（不影响正在运行的 Run）"""
    global WORKFLOW, AGENT_CONFIG
    WORKFLOW = load_workflow("config/active.yaml")
    AGENT_CONFIG = load_agents("config/agents/active.yaml")
    return {"ok": True, "workflow": WORKFLOW["name"]}
```

#### 2. 动态路由

```python
# 根据任务类型选择模型
def pick_model(agent_config: dict, routing: dict, run_ctx: dict) -> str:
    """根据 stage / 上下文选择模型。优先级：
    1. agent_config["model"] 显式指定
    2. agent_config["role"] 对应 routing[role]
    3. routing["default"] 兜底
    """
    if "model" in agent_config:
        return agent_config["model"]
    role = agent_config.get("role", "fast")
    return routing.get(role, routing.get("default"))
```

#### 3. 多项目隔离

```yaml
# config/workflows/project-acme.yaml
project_root: /home/user/projects/acme
name: acme-feature-dev
stages:
  - id: architect
    mode: interactive
    output: docs/design/   # 相对于 project_root
    key: design
```

```python
# sidecar 加载时根据 project_root 重新计算：
PROJECT_PATH = workflow["project_root"]
OPEN_CODE_WORKSPACE = os.path.join(PROJECT_PATH, ".agent-workspace")
```

## 工期估计

**6h**

| 子任务 | 工时 |
|--------|------|
| YAML 加载 + 解析 | 1.5h |
| 移除硬编码 + 改用动态配置 | 1h |
| `/api/reload` + `/api/config` 端点 | 1h |
| 模型路由 | 1h |
| 多项目隔离 | 1h |
| UI 工作流选择器 | 30min |

## 验收标准

- 启动 sidecar → 加载 `config/active.yaml`（默认 feature-dev）
- `POST /api/reload` → 切换到 `research-paper.yaml` → UI 工作流名称更新
- 修改 `config/agents/default.yaml` 中某个 stage 的 model → reload → 下次 Run 用新模型
- 不同项目用不同 `project_root` → `.agent-workspace/` 隔离
- 旧硬编码 `WORKFLOW` / `AGENT_CONFIG` 完全移除
- `sidecar.py` 长度因配置外化而**减少**而非增加

## 关联阶段

- 本阶段是收尾阶段，前面所有阶段的产出（产物类型、stage 配置）都需要反映到 YAML schema
- YAML schema 是阶段 2~4 的统一抽象层
