import asyncio
import json
import os
import sys
import re
import uuid
from datetime import datetime
from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import HTMLResponse
from fastapi.responses import StreamingResponse

ANSI_ESCAPE_RE = re.compile(r"\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])")


def strip_ansi(text: str) -> str:
    return ANSI_ESCAPE_RE.sub("", text)


# ========== 时间戳日志 ==========
def _ts() -> str:
    """HH:MM:SS.mmm 格式时间戳。"""
    now = datetime.now()
    return now.strftime("%H:%M:%S.") + f"{now.microsecond // 1000:03d}"


def tlog(rid: str | None, msg: str):
    """带时间戳 + 可选 rid 的 stderr 日志。

    用于 pipeline 相关事件（pipeline_task、exec_backend、handle_gate、persist 等），
    让运维一眼能看出"哪个 run 在哪个阶段"。
    """
    prefix = f"[{_ts()}]"
    if rid:
        prefix += f" {rid}"
    sys.stderr.write(f"{prefix} {msg}\n")
    sys.stderr.flush()

# ========== 自动注入 API Keys ==========
SECRETS_FILE = os.path.expanduser("~/.opencode-pipeline/secrets.json")
if os.path.exists(SECRETS_FILE):
    with open(SECRETS_FILE) as f:
        secrets = json.load(f)
        for key, value in secrets.items():
            os.environ.setdefault(key, value)
            tlog(None, f"[Sidecar] Loaded {key} from secrets.json")

# ========== 配置 ==========
# 当前：在 opencode-pipeline 目录内迭代自身
# 未来：改为 ~/workspace/JavaLearning/01-Projects/xxx
PROJECT_PATH = os.path.dirname(os.path.abspath(__file__))
STATE_FILE = os.path.expanduser("~/.opencode-pipeline/state.json")
os.makedirs(os.path.dirname(STATE_FILE), exist_ok=True)

# OpenCode 工作目录（隔离子目录，避免扫描父目录）
OPEN_CODE_WORKSPACE = os.path.join(PROJECT_PATH, ".agent-workspace")
os.makedirs(OPEN_CODE_WORKSPACE, exist_ok=True)

AGENT_CONFIG = {
    "architect": {
        "model": "minimax-cn/MiniMax-M3",
        "prompt": """你是资深架构师。分析需求并输出设计文档。

        【任务】
        基于用户需求与项目上下文，设计一个模块的架构方案。

        【隔离约束（必须遵守）】
        - 仅使用相对路径（./docs/design.md）访问/写入文件
        - 严禁使用绝对路径（不要用 /home/... 或 /tmp/... 这样的全路径）
        - 不要探索工作目录之外的任何文件
        - 你的工作目录就是项目根，所有路径必须以 ./ 开头

        【用户需求】
        {user_requirements}

        【输出要求】
        1. 输出 Markdown 格式设计文档
        2. 包含：模块划分、接口定义、数据流、异常处理策略
        3. 将完整设计文档内容写入指定文件

        【项目上下文】{context}""",
    },
    "coder": {
        "model": "minimax-cn/MiniMax-M3",
        "prompt": """你是资深 Python/FastAPI 开发。根据设计文档实现代码。

        【任务】
        实现设计文档中定义的接口和功能。

        【隔离约束（必须遵守）】
        - 仅使用相对路径访问/写入文件
        - 严禁使用绝对路径
        - 不要探索工作目录之外的任何文件

        【输入】
        设计文档路径（相对）：{design_path}

        【输出要求】
        1. 输出完整可运行的 Python 代码
        2. 包含必要的错误处理
        3. 将完整代码写入指定文件

        【项目上下文】{context}""",
    },
    "reviewer": {
        "model": "minimax-cn/MiniMax-M3",
        "prompt": """你是严格的代码审查员。

        【任务】
        审查代码质量并输出审查报告。

        【隔离约束（必须遵守）】
        - 仅使用相对路径访问文件
        - 严禁使用绝对路径
        - 不要探索工作目录之外的任何文件

        【输入】
        代码路径（相对）：{code_path}
        设计文档路径（相对）：{design_path}

        【检查项】
        1. 是否符合设计文档
        2. 是否有潜在 Bug、并发问题、空指针
        3. 错误处理是否完善
        4. 代码风格（命名、注释、异常处理）

        【输出】
        输出 Markdown 审查报告，写入指定文件""",
    },
}

app = FastAPI()


@app.on_event("shutdown")
async def on_shutdown():
    """Ctrl+C / SIGTERM 时清理：杀所有子进程、取消协程、save_state"""
    tlog(None, "[Sidecar] shutdown: killing subprocesses...")
    procs = list(engine["procs"])
    for p in procs:
        try:
            if p.returncode is None:
                p.kill()
        except Exception:
            pass
    # 取消所有 pipeline_task 协程
    tasks = list(engine["tasks"])
    for t in tasks:
        t.cancel()
    # 短等待让协程清理退出（已有 try/finally proc.kill() 兜底）
    if tasks:
        try:
            await asyncio.wait_for(
                asyncio.gather(*tasks, return_exceptions=True), timeout=3
            )
        except asyncio.TimeoutError:
            tlog(None, "[Sidecar] shutdown: tasks didn't exit in 3s, force-exiting")
    # save_state 把当前所有 Run 状态持久化
    try:
        save_state()
        tlog(None, f"[Sidecar] shutdown: saved state for {len(engine['runs'])} runs")
    except Exception as e:
        tlog(None, f"[Sidecar] shutdown: save_state failed: {e}")
    tlog(None, "[Sidecar] shutdown: done")

# ========== 工作流定义 ==========
WORKFLOW = {
    "name": "feature-dev",
    "max_iterations": 3,
    "stages": [
        {
            "id": "architect",
            "mode": "backend",
            "output": "docs/design.md",
            "key": "design",
        },
        {"id": "direction-gate", "type": "gate", "mode": "human", "on_reject": 0},
        {"id": "coder", "mode": "backend", "output": "src/feature.py", "key": "code"},
        {
            "id": "review-gate",
            "type": "gate",
            "mode": "auto",
            "condition": "file_exists",
        },
        {
            "id": "reviewer",
            "mode": "backend",
            "output": "docs/review.md",
            "key": "review",
        },
        {"id": "merge-gate", "type": "gate", "mode": "human", "on_reject": 2},
    ],
}


# ========== 引擎 ==========
class Run:
    """工作流 Run 实例。

    内存状态：_state ∈ {running, non_running, exception}
      - running:     pipeline_task 协程正在执行（agent 执行中）
      - non_running: 协程已退出，可继续（gate 等待 / 完成 / 暂停 / 服务器关闭）
      - exception:   真实异常（agent 崩溃/超时/配置错误），需要人工决策

    持久化状态：当前节点 idx（边没有状态）。每次到达节点时 persist。
    """

    def __init__(self, rid: str, user_requirements: str = ""):
        self.rid = rid
        self.idx = 0
        self._state = "running"
        self._error: str | None = None
        self.ctx = {}
        self.history = []
        self.events: dict[str, asyncio.Event] = {}
        self._task: asyncio.Task | None = None
        self._cleanup_handle: asyncio.TimerHandle | None = None
        self.user_requirements = user_requirements or "（用户未提供具体需求，请基于通用最佳实践设计一个简洁的示例模块）"

    def _maybe_cleanup(self):
        """60s 后由事件循环调用。检查是否还有活跃任务 — 有则跳过（防止 pause→continue 竞态把活跃 run 清掉）。"""
        if self._task and not self._task.done():
            return  # 新任务在跑，不清理
        engine["runs"].pop(self.rid, None)
        self._cleanup_handle = None

    @property
    def is_completed(self) -> bool:
        return self._state == "non_running" and self.idx >= len(WORKFLOW["stages"])

    @property
    def is_waiting_gate(self) -> bool:
        if self._state != "non_running":
            return False
        if self.idx >= len(WORKFLOW["stages"]):
            return False
        return WORKFLOW["stages"][self.idx].get("type") == "gate"

    @property
    def can_continue(self) -> bool:
        """non_running + 还没走完 → 可继续。exception 状态需人工判断。"""
        return self._state == "non_running" and self.idx < len(WORKFLOW["stages"])

    def _status_for_ui(self) -> str:
        """UI 兼容字段：把 _state 映射到旧 status 名称。"""
        if self._state == "running":
            return "running"
        if self._state == "exception":
            return "exception"
        # non_running
        if self.is_completed:
            return "completed"
        if self.is_waiting_gate:
            return "waiting_gate"
        return "interrupted"

    def snapshot(self):
        stage = (
            WORKFLOW["stages"][self.idx] if self.idx < len(WORKFLOW["stages"]) else {}
        )
        artifacts = {}
        for k, v in self.ctx.items():
            if k.startswith("decision:") or k.startswith("note:"):
                continue
            if isinstance(v, str) and v and v != "failed":
                try:
                    artifacts[k] = os.path.relpath(v, OPEN_CODE_WORKSPACE)
                except ValueError:
                    artifacts[k] = v
            elif v == "failed":
                artifacts[k] = None
        return {
            "rid": self.rid,
            "_state": self._state,
            "status": self._status_for_ui(),
            "idx": self.idx,
            "stage_id": stage.get("id", "done"),
            "stage_mode": stage.get("mode", "backend"),
            "waiting_gate": stage.get("id") if self.is_waiting_gate else None,
            "artifacts": artifacts,
            "history": self.history[-30:],
            "history_length": len(self.history),
            "user_requirements": self.user_requirements,
            "error": self._error,
        }


engine = {"runs": {}, "tasks": set(), "procs": set()}  # procs: 活跃的子进程句柄

# 单阶段总超时（秒）— agent 挂死时强制退出
STAGE_TIMEOUT_S = 600
# 内存保护：单 Run 最多保留 history 条数
HISTORY_CAP = 300


def cap_history(run: "Run"):
    """trim run.history 防止无限增长导致 OOM。"""
    try:
        if len(run.history) > HISTORY_CAP:
            run.history = run.history[-HISTORY_CAP:]
    except BaseException:
        pass  # 截断失败不阻塞 pipeline


def append_history(run: "Run", entry: dict):
    """append + cap 一步完成，所有 history.append 调用应走这里。

    失败时仅记录错误（不重抛）— 写日志失败不应阻塞 pipeline。
    """
    try:
        run.history.append(entry)
        cap_history(run)
    except BaseException as e:
        sys.stderr.write(f"[{_ts()}] {run.rid} [append_history] FAIL: {type(e).__name__}: {e}\n")
        sys.stderr.flush()


def save_state():
    """同步持久化 — 用于模块级启动/关闭时调用。"""
    with open(STATE_FILE, "w") as f:
        json.dump(
            {rid: r.snapshot() for rid, r in engine["runs"].items()}, f, default=str
        )


async def persist(run: Run | None = None):
    """异步持久化 — 不阻塞事件循环。

    节点到达时调用（边没有状态，所以 persist 只在 idx 变化或 _state 终态时）。
    失败不抛 — 最多下次启动发现 idx 还在上个节点，重跑覆盖产物。
    """
    try:
        await asyncio.to_thread(save_state)
    except Exception as e:
        tlog(run.rid if run else None, f"[persist] failed: {type(e).__name__}: {e}")


async def safe_persist(run: Run | None = None):
    """调用 persist() 并吞掉任何异常 — 用于不希望持久化失败影响主流程的调用点。

    节点的 persist 调用走这个：即使写盘失败，也只是丢失这次状态变更的快照，
    下次 persist 时自然补上。pipeline 不应因此崩溃。
    """
    try:
        await persist(run)
    except Exception as e:
        tlog(run.rid if run else None, f"[safe_persist] swallowed: {type(e).__name__}: {e}")


def load_state():
    """启动时从 state.json 恢复 Run 状态。

    关键点：sidecar 重启必然丢失 pipeline_task 协程 → 所有 run 视作 non_running。
    exception 状态保留（让用户看到错误信息），但用户可继续。
    """
    if not os.path.exists(STATE_FILE):
        return
    try:
        with open(STATE_FILE) as f:
            data = json.load(f)
    except Exception as e:
        tlog(None, f"[Sidecar] state.json load failed: {e}")
        return
    for rid, snap in data.items():
        if not isinstance(snap, dict):
            continue
        run = Run(rid, user_requirements=snap.get("user_requirements", ""))
        run.idx = snap.get("idx", 0)
        run.ctx = {}  # ctx 从 artifacts 反推
        run.history = snap.get("history", [])
        run._error = snap.get("error")

        # 向后兼容：旧 status 字段 → 新 _state
        prev_state = snap.get("_state")
        prev_status = snap.get("status")
        stage_id = snap.get("stage_id", "")
        history = snap.get("history", [])

        if prev_state in ("running", "non_running", "exception"):
            # 新格式
            if prev_state == "running":
                # 协程必然丢失，强制 non_running
                run._state = "non_running"
                _mark_interrupted(run, "sidecar 重启中断 (running → non_running)")
            elif prev_state == "non_running":
                run._state = "non_running"
            else:  # exception
                run._state = "exception"
                # 兜底：exception 但实际在 gate 等过 → 转为 non_running
                # （旧版 CancelledError 被误标为 exception，同步历史）
                if stage_id.endswith("-gate"):
                    had_wait_human = any(
                        h.get("action") == "wait_human" for h in history
                    )
                    if had_wait_human:
                        run._state = "non_running"
                        _mark_interrupted(run, f"sidecar 重启中断 (exception 恢复: {stage_id})")
        else:
            # 旧 status 字段映射
            if prev_status in ("running", "waiting_gate", "interrupted"):
                run._state = "non_running"
                if prev_status == "waiting_gate":
                    _mark_interrupted(run, f"sidecar 重启中断 (gate 等待中: {stage_id})")
            elif prev_status == "completed":
                run._state = "non_running"
            elif prev_status == "failed":
                # 兜底：failed 但在 gate 阶段被中断 → 可继续
                # 旧版 CancelledError 被误标为 failed。如果 idx 指向 gate 阶段，
                # 几乎可以确定是被中断在 gate 等待中（gate 自身不会失败）。
                if stage_id.endswith("-gate"):
                    had_wait_human = any(
                        h.get("action") == "wait_human" for h in history
                    )
                    if had_wait_human:
                        run._state = "non_running"
                        _mark_interrupted(run, f"sidecar 重启中断 (gate 等待中恢复: {stage_id})")
                    else:
                        run._state = "exception"
                else:
                    run._state = "exception"
            else:
                run._state = "non_running"

        # 从 artifacts 反推 ctx
        for k, v in (snap.get("artifacts") or {}).items():
            if v:
                run.ctx[k] = os.path.join(OPEN_CODE_WORKSPACE, v)
        engine["runs"][rid] = run
    # 持久化校正后的状态（向后兼容：旧 failed → non_running 的转换）
    save_state()
    tlog(None, f"[Sidecar] 恢复 {len(engine['runs'])} 个 Run 状态")


def _mark_interrupted(run: Run, detail: str):
    """向 history 追加 sidecar 重启中断事件。"""
    run.history.append({
        "t": datetime.now().isoformat(),
        "type": "warning",
        "action": "interrupted",
        "detail": detail,
    })


# 启动时从 state.json 恢复 Run 状态
load_state()


async def exec_backend(run: Run, stage: dict):
    """启动 OpenCode Agent，实时流式捕获进度"""
    tlog(run.rid, f"agent start: stage={stage.get('id','?')}, output={stage.get('output','')}")
    agent = AGENT_CONFIG.get(stage["id"])
    if not agent:
        raise ValueError(f"Unknown stage: {stage['id']}")

    relative_output = stage["output"]
    workspace_out = os.path.join(OPEN_CODE_WORKSPACE, relative_output)
    os.makedirs(os.path.dirname(workspace_out), exist_ok=True)

    info_buffer: list[str] | None = None
    info_buf_count = 0
    # 记录开始时间
    start_time = datetime.now()

    # 构建 Prompt（不暴露绝对路径）
    design_abs = run.ctx.get("design", "")
    code_abs = run.ctx.get("code", "")
    design_rel = (
        os.path.relpath(design_abs, OPEN_CODE_WORKSPACE)
        if design_abs and design_abs != "failed"
        else "（未生成）"
    )
    code_rel = (
        os.path.relpath(code_abs, OPEN_CODE_WORKSPACE)
        if code_abs and code_abs != "failed"
        else "（未生成）"
    )
    # 把 ctx 中的绝对路径转换为相对路径（防止 LLM 据此越界）
    ctx_rel = {}
    for k, v in run.ctx.items():
        if isinstance(v, str) and v and v != "failed" and os.path.isabs(v):
            try:
                ctx_rel[k] = os.path.relpath(v, OPEN_CODE_WORKSPACE)
            except ValueError:
                ctx_rel[k] = v
        else:
            ctx_rel[k] = v

    prompt = agent["prompt"].format(
        user_requirements=run.user_requirements,
        context=json.dumps(ctx_rel, ensure_ascii=False),
        design_path=design_rel,
        code_path=code_rel,
        output_path=f"./{relative_output}",
    )

    full_prompt = f"""{prompt}
        【关键要求】
        1. 产物写入: ./{relative_output}（相对路径）
        2. 覆盖已存在文件
        3. 不要 cd 到任何绝对路径
        """

    cmd = [
        "opencode",
        "run",
        "--model",
        agent["model"],
        "--dir",
        OPEN_CODE_WORKSPACE,
        full_prompt,
    ]

    # 流式执行：合并 stderr 到 stdout（避免两个独立 read_stream 各自维护 thinking 状态）
    tlog(run.rid, f"agent spawning opencode subprocess (cwd={OPEN_CODE_WORKSPACE})")
    try:
        proc = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.STDOUT,
            cwd=OPEN_CODE_WORKSPACE,
            env=os.environ,
        )
    except Exception as e:
        tlog(run.rid, f"agent subprocess create FAILED: {e}")
        raise
    tlog(run.rid, f"agent subprocess started pid={proc.pid}")
    engine["procs"].add(proc)
    proc._done_callback = lambda p: engine["procs"].discard(p)

    # 实时解析输出（块读取 + 仅在非 info 时 flush + 累计 idle 心跳）
    async def read_stream(stream, name):
        nonlocal info_buffer, info_buf_count
        HEARTBEAT_S = 10  # 10s 心跳，能捕获较短的思考期
        SAFETY_CAP = 200
        MAX_INFO_LINES = 20  # info entry 超过此行数时截断
        # 这些"meta"事件不打断 thinking（避免开/合阶段时无意义地切分思考期）
        META_ACTIONS = {"building", "scanning"}
        # 时间基：monotonic 用于内部 duration 计算，wall 时钟仅用于客户端显示
        last_event_ts = asyncio.get_event_loop().time()
        loop_start = last_event_ts  # read_stream 启动时刻（用于 wall↔loop 转换）
        wall_start = datetime.now().timestamp()  # 对应 wall 时间
        line_buffer = b""
        thinking_since: float | None = None  # 思考开始时间戳（loop time）

        def to_wall(loop_t: float) -> str:
            """loop_time → ISO wall timestamp（用于客户端显示）"""
            return datetime.fromtimestamp(wall_start + (loop_t - loop_start)).isoformat()

        def emit_thinking_started():
            """发送 waiting 事件（带 thinking_since 字段供前端实时计算）"""
            append_history(run, 
                {
                    "t": datetime.now().isoformat(),
                    "stage": stage["id"],
                    "source": name,
                    "type": "progress",
                    "action": "waiting",
                    "detail": "正在思考…",
                    "thinking_since": to_wall(asyncio.get_event_loop().time()),
                }
            )

        def emit_thought_done(duration_s: float):
            """发送 thought_done 事件（结束思考，记录总时长）"""
            append_history(run, 
                {
                    "t": datetime.now().isoformat(),
                    "stage": stage["id"],
                    "source": name,
                    "type": "milestone",
                    "action": "thought_done",
                    "detail": f"Thought: {duration_s:.1f}s",
                    "duration_s": round(duration_s, 1),
                }
            )

        def flush():
            nonlocal info_buffer, info_buf_count
            if not info_buffer:
                return
            joined = "\n".join(info_buffer)
            entry = {
                "t": datetime.now().isoformat(),
                "stage": stage["id"],
                "source": name,
                "type": "msg",
                "action": "info",
                "detail": joined,
                "batched": info_buf_count,
            }
            # 20 行截断
            if info_buf_count > MAX_INFO_LINES:
                lines = joined.split("\n")
                entry["detail"] = "\n".join(lines[:MAX_INFO_LINES]) + "\n..."
                entry["full_detail"] = joined
                entry["truncated"] = True
                entry["total_lines"] = info_buf_count
            append_history(run, entry)
            info_buffer = []
            info_buf_count = 0

        def process_line(raw_line: str):
            """处理单行输出，更新状态"""
            nonlocal info_buffer, info_buf_count, last_event_ts, thinking_since
            text = strip_ansi(raw_line).rstrip()
            if not text.strip():
                return
            now = asyncio.get_event_loop().time()
            last_event_ts = now
            event = parse_opencode_event(text)
            if not event:
                return
            # meta 事件不打断 thinking（如 `building`）— 但仍记录到 history
            is_meta = event.get("action") in META_ACTIONS
            if not is_meta and thinking_since is not None:
                duration = now - thinking_since
                emit_thought_done(duration)
                thinking_since = None
            if event.get("type") == "msg" and event.get("action") == "info":
                if info_buffer is None:
                    info_buffer = []
                    info_buf_count = 0
                info_buffer.append(event.get("detail", ""))
                info_buf_count += 1
                if info_buf_count >= SAFETY_CAP:
                    flush()
            else:
                flush()
                entry = {
                    "t": datetime.now().isoformat(),
                    "stage": stage["id"],
                    "source": name,
                    **event,
                }
                append_history(run, entry)

        while True:
            try:
                chunk = await asyncio.wait_for(
                    stream.read(4096), timeout=HEARTBEAT_S
                )
            except asyncio.TimeoutError:
                now = asyncio.get_event_loop().time()
                if thinking_since is None:
                    tlog(run.rid, f"agent thinking... (heartbeat {HEARTBEAT_S}s)")
                    thinking_since = now
                    emit_thinking_started()
                # else: 已经在思考中，不再追加事件
                continue
            if not chunk:
                # stream 关闭，结束思考周期
                tlog(run.rid, "agent stream closed (EOF)")
                if thinking_since is not None:
                    duration = asyncio.get_event_loop().time() - thinking_since
                    emit_thought_done(duration)
                    thinking_since = None
                flush()
                break
            line_buffer += chunk
            # 按 \n 或 \r 切行（LLM 流式输出可能用 \r 而非 \n）
            while True:
                nl = line_buffer.find(b"\n")
                cr = line_buffer.find(b"\r")
                cands = [p for p in (nl, cr) if p >= 0]
                if not cands:
                    break
                idx = min(cands)
                line = line_buffer[:idx]
                line_buffer = line_buffer[idx + 1 :]
                try:
                    raw_text = line.decode("utf-8", errors="replace")
                except Exception:
                    continue
                process_line(raw_text)

    # 单流读取（stderr 已合并到 stdout）— 总超时 10 分钟强制退出
    # 用 holder 让 finally 能访问 proc，无论异常还是 cancellation
    proc_holder = {"proc": proc}
    try:
        try:
            await asyncio.wait_for(
                read_stream(proc.stdout, "out"), timeout=STAGE_TIMEOUT_S
            )
            await asyncio.wait_for(proc.wait(), timeout=30)
            engine["procs"].discard(proc)
        except asyncio.TimeoutError:
            proc.kill()
            try:
                await proc.wait()
            except Exception:
                pass
            engine["procs"].discard(proc)
            raise RuntimeError(
                f"agent 超时（>{STAGE_TIMEOUT_S}s）已被强制终止: {stage['id']}"
            )

        # 检查子进程返回值
        if proc.returncode != 0:
            raise RuntimeError(
                f"agent 异常退出（returncode={proc.returncode}）: {stage['id']}"
            )
    except BaseException:
        # 任何异常（含 CancelledError）都确保子进程被杀 — 避免孤儿进程
        p = proc_holder.get("proc")
        if p and p.returncode is None:
            try:
                p.kill()
            except Exception:
                pass
            try:
                # 不 await 太久，避免无限挂起
                await asyncio.wait_for(p.wait(), timeout=5)
            except Exception:
                pass
        engine["procs"].discard(proc)
        raise

    # 检测产物（opencode --dir 已限制工作空间在 OPEN_CODE_WORKSPACE）
    await asyncio.sleep(1)
    out_file = None
    if os.path.exists(workspace_out):
        out_file = workspace_out

    duration = (datetime.now() - start_time).total_seconds()
    rel = os.path.relpath(out_file, OPEN_CODE_WORKSPACE) if out_file else None
    run.ctx[stage["key"]] = out_file or "failed"
    append_history(run, 
        {
            "t": datetime.now().isoformat(),
            "e": f"agent_done:{stage['id']} duration={duration}s file={out_file is not None}",
            "type": "milestone",
            "action": "stage_done",
            "stage": stage["id"],
            "artifact": rel,
            "key": stage.get("key"),
            "duration_s": duration,
            "ok": out_file is not None,
        }
    )


def parse_opencode_event(text: str) -> dict | None:
    """解析 OpenCode 输出为结构化事件"""
    text = text.strip()
    if not text:
        return None
    if text.startswith("> build"):
        return {
            "type": "progress",
            "action": "building",
            "detail": text.split("·")[-1].strip(),
        }
    if text.startswith("→ Read"):
        return {
            "type": "action",
            "action": "reading",
            "detail": text[6:].strip(),
        }
    if text.startswith("← Write"):
        return {
            "type": "action",
            "action": "writing",
            "detail": text[7:].strip(),
        }
    if text.startswith("✱ Glob"):
        return {
            "type": "action",
            "action": "scanning",
            "detail": text[6:].strip(),
        }
    if text.startswith("✗"):
        return {
            "type": "error",
            "action": "error",
            "detail": text[1:].strip(),
        }
    if "Wrote file successfully" in text:
        return {"type": "milestone", "action": "file_written", "detail": text}
    if "permission requested" in text:
        return {
            "type": "warning",
            "action": "permission_denied",
            "detail": text,
        }
    return {"type": "msg", "action": "info", "detail": text[:120]}


async def handle_gate(run: Run, stage: dict):
    """处理 gate 节点 — 人类审批 / 自动检查。

    进入时 _state → non_running（暂停在 gate），等待事件后回到 running。
    取消（pause/shutdown）会通过 CancelledError 传递，不修改 _state。
    """
    gid = stage["id"]
    if stage["mode"] == "human":
        run._state = "non_running"
        await safe_persist(run)  # 节点到达后立即持久化 _state 变化（失败不影响 gate 等待）
        evt = asyncio.Event()
        run.events[gid] = evt
        append_history(run,
            {
                "t": datetime.now().isoformat(),
                "e": f"gate_wait_human:{gid}",
                "type": "gate",
                "action": "wait_human",
                "gate": gid,
            }
        )
        try:
            await evt.wait()
        except BaseException:
            # CancelledError 等异常向上传递，_state 保持 non_running
            raise
        run._state = "running"
        dec = run.ctx.get(f"decision:{gid}", "approve")
        if dec == "reject":
            target = stage.get("on_reject", 0)
            run.idx = target
            append_history(run,
                {
                    "t": datetime.now().isoformat(),
                    "e": f"gate_reject:{gid}->stage{target}",
                    "type": "gate",
                    "action": "reject",
                    "gate": gid,
                    "target_stage": target,
                }
            )
        else:
            append_history(run,
                {
                    "t": datetime.now().isoformat(),
                    "e": f"gate_approve:{gid}",
                    "type": "gate",
                    "action": "approve",
                    "gate": gid,
                }
            )
    elif stage["mode"] == "auto":
        run._state = "non_running"  # auto gate 也是停点（短暂）
        ok = os.path.exists(os.path.join(OPEN_CODE_WORKSPACE, "src/feature.py"))
        append_history(run,
            {
                "t": datetime.now().isoformat(),
                "e": f"gate_auto:{gid}:{'pass' if ok else 'fail'}",
                "type": "gate",
                "action": "auto_pass" if ok else "auto_fail",
                "gate": gid,
                "ok": ok,
            }
        )
        if not ok:
            run.idx = stage.get("on_reject", 2)
        run._state = "running"


async def pipeline_task(run: Run):
    """工作流执行循环。

    状态机（边没有状态，节点是状态）：
      - 每次循环开始时 persist（到达节点）
      - gate 节点：handle_gate 在内部切换 _state (non_running ↔ running)
      - agent 节点：exec_backend 保持 _state = running
      - 节点完成后 idx += 1（边，不持久化；下次循环 persist 捕获新位置）
      - CancelledError → _state = non_running（信号，不是异常）
      - 其他异常 → _state = exception（需要人工判断）
    """
    stages = WORKFLOW["stages"]
    run._state = "running"
    tlog(run.rid, f"pipeline start (idx={run.idx}/{len(stages)}, stage={stages[run.idx]['id'] if run.idx < len(stages) else 'done'})")
    try:
        while run.idx < len(stages):
            st = stages[run.idx]
            # === 节点到达 — 持久化当前 idx（边没有状态） ===
            # 写盘失败不影响 pipeline 执行 — safe_persist 吞掉异常
            await safe_persist(run)
            tlog(run.rid, f"→ enter {st['id']} (idx={run.idx}, type={st.get('type','agent')}, mode={st.get('mode','backend')})")
            append_history(run,
                {
                    "t": datetime.now().isoformat(),
                    "e": f"enter:{st['id']}",
                    "type": "stage",
                    "action": "enter",
                    "stage": st["id"],
                    "stage_type": st.get("type", "agent"),
                    "mode": st.get("mode", "backend"),
                }
            )
            # === 节点分派 — 统一 handler 接口 ===
            node_type = st.get("type", "agent")
            handler = NODE_HANDLERS.get(node_type, exec_backend)
            await handler(run, st)
            # gate reject 会自己设置 idx（跳回），其他情况自然推进
            if node_type == "gate" and run.ctx.get(f"decision:{st['id']}") == "reject":
                continue
            run.idx += 1
        # 循环结束 → 节点走完
        run._state = "non_running"  # is_completed == True
        tlog(run.rid, f"✓ pipeline completed (all {len(stages)} stages done)")
        append_history(run,
            {
                "t": datetime.now().isoformat(),
                "e": "pipeline:completed",
                "type": "milestone",
                "action": "pipeline_done",
                "ok": True,
            }
        )
        await safe_persist(run)
    except BaseException as e:
        is_cancel = isinstance(e, asyncio.CancelledError)
        if is_cancel:
            tlog(run.rid, f"⏸ task cancelled (idx={run.idx}, stage={stages[run.idx]['id'] if run.idx < len(stages) else 'done'})")
        else:
            tlog(run.rid, f"✗ {type(e).__name__}: {e}")
        if is_cancel:
            # CancelledError = 信号（shutdown / pause），不是异常
            # _state 由 handle_gate 设置为 non_running；agent 阶段需手动设置
            if run._state == "running":
                run._state = "non_running"
            append_history(run,
                {
                    "t": datetime.now().isoformat(),
                    "e": "task:cancelled",
                    "type": "warning",
                    "action": "task_cancelled",
                    "detail": "任务被中断（可继续）",
                }
            )
        else:
            # 真实异常 → exception 状态，需人工决策
            run._state = "exception"
            run._error = str(e) or type(e).__name__
            append_history(run,
                {
                    "t": datetime.now().isoformat(),
                    "e": f"error:{str(e)}",
                    "type": "error",
                    "action": "pipeline_error",
                    "detail": str(e),
                }
            )
        # 异常/取消都尝试持久化 — 失败不影响结果（最多下次启动 idx 还在上个节点）
        await safe_persist(run)
    # 60s 后清理 engine 内的 Run 引用（让 SSE 仍有时间读取最终状态）。
    # 用 _maybe_cleanup 防止 pause→continue 竞态：旧 pipeline_task 退出后 60s 内若
    # 已有新任务在跑（continue 触发的），清理跳过。
    try:
        loop = asyncio.get_event_loop()
        run._cleanup_handle = loop.call_later(60, run._maybe_cleanup)
    except Exception:
        pass


# 节点类型分派表 — 扩展新节点只需在此注册
NODE_HANDLERS = {
    "agent": exec_backend,
    "gate": handle_gate,
}


# ========== API ==========
@app.get("/api/workflow")
async def get_workflow():
    return {
        "name": WORKFLOW["name"],
        "stages": [
            {
                "id": s["id"],
                "mode": s.get("mode", "backend"),
                "type": s.get("type", "agent"),
            }
            for s in WORKFLOW["stages"]
        ],
    }


@app.post("/api/start")
async def start(requirements: str = ""):
    rid = f"run-{uuid.uuid4().hex[:6]}"
    run = Run(rid, user_requirements=requirements)
    engine["runs"][rid] = run
    run._task = asyncio.create_task(pipeline_task(run))
    engine["tasks"].add(run._task)
    run._task.add_done_callback(engine["tasks"].discard)
    tlog(rid, f"start requested: {(requirements or '')[:60]}")
    await safe_persist(run)
    return run.snapshot()


@app.get("/api/runs")
async def list_runs():
    """列出所有已知 Run（内存 + state.json）— UI 用于显示历史"""
    runs = []
    seen = set()
    for rid, run in engine["runs"].items():
        snap = run.snapshot()
        snap["artifacts_count"] = len(snap.get("artifacts") or {})
        snap["user_requirements"] = getattr(run, "user_requirements", "")
        runs.append(snap)
        seen.add(rid)
    # 兜底：state.json 中有但内存中没有的（理论上 load_state 已加载，这里防御性读取）
    if os.path.exists(STATE_FILE):
        try:
            with open(STATE_FILE) as f:
                data = json.load(f)
            for rid, snap in data.items():
                if rid not in seen:
                    runs.append({
                        "rid": rid,
                        "_state": snap.get("_state", "non_running"),
                        "status": snap.get("status", "unknown"),
                        "idx": snap.get("idx", 0),
                        "stage_id": snap.get("stage_id", "done"),
                        "stage_mode": snap.get("stage_mode"),
                        "artifacts": snap.get("artifacts", {}),
                        "artifacts_count": len(snap.get("artifacts") or {}),
                        "history": [],
                        "user_requirements": snap.get("user_requirements", ""),
                    })
        except Exception:
            pass
    # 按时间倒序（最近 run 在前）
    runs.sort(key=lambda r: r.get("rid", ""), reverse=True)
    return {"runs": runs}


@app.delete("/api/runs/{rid}")
async def delete_run(rid: str):
    """删除 Run（内存 + state.json）"""
    r = engine["runs"].pop(rid, None)
    if r:
        # 如果有活跃协程则取消
        if r._task and not r._task.done():
            r._task.cancel()
        save_state()
    return {"ok": True}


@app.post("/api/continue/{rid}")
async def continue_run(rid: str):
    """继续一个 non_running 的任务（同一 RID，保留 idx/ctx，从断点恢复）。"""
    r = engine["runs"].get(rid)
    if not r:
        raise HTTPException(404)
    if not r.can_continue:
        raise HTTPException(
            400,
            detail=f"无法继续: _state={r._state}, idx={r.idx}/{len(WORKFLOW['stages'])}",
        )
    # 取消旧 pipeline_task 的 60s 清理定时器（如果还在挂起）
    if r._cleanup_handle is not None:
        r._cleanup_handle.cancel()
        r._cleanup_handle = None
    # 清除中断标记事件（保持日志干净）
    if r.history and r.history[-1].get("action") == "interrupted":
        r.history.pop()
    r._task = asyncio.create_task(pipeline_task(r))
    engine["tasks"].add(r._task)
    r._task.add_done_callback(engine["tasks"].discard)
    tlog(r.rid, f"continue requested (idx={r.idx})")
    await safe_persist(r)
    return r.snapshot()


@app.post("/api/pause/{rid}")
async def pause_run(rid: str):
    """暂停一个正在执行或等待中的 Run。

    行为：
      - running（agent 执行中）→ 取消 pipeline_task，子进程被 kill → _state=non_running
      - non_running（gate 等待）→ 取消 event.wait() → _state 保持 non_running
    CancelledError 由 pipeline_task 处理，pause_run 只负责触发信号。
    """
    r = engine["runs"].get(rid)
    if not r:
        raise HTTPException(404)
    if r._state != "running":
        raise HTTPException(400, detail=f"只能暂停运行中的任务 (current _state={r._state})")
    tlog(rid, f"pause requested (idx={r.idx})")
    if r._task and not r._task.done():
        r._task.cancel()
        # 等待协程退出，让 CancelledError 走到 pipeline_task 的 except 块
        try:
            await asyncio.wait_for(asyncio.shield(r._task), timeout=5)
        except (asyncio.CancelledError, asyncio.TimeoutError):
            pass
        except Exception:
            pass
    # 兜底：确保状态正确（pipeline_task 的 except 块已经处理，但 shield 取消可能没让出时间）
    if r._state == "running":
        r._state = "non_running"
    await safe_persist(r)
    return r.snapshot()


@app.get("/api/runs/{rid}")
async def get_run(rid: str):
    r = engine["runs"].get(rid)
    if not r:
        raise HTTPException(404)
    snap = r.snapshot()
    snap["history_length"] = len(r.history)  # 客户端接续 SSE 时用
    return snap


@app.post("/api/runs/{rid}/gates/{gid}/action")
async def gate_action(rid: str, gid: str, action: str = Query(...), note: str = ""):
    r = engine["runs"].get(rid)
    if not r:
        raise HTTPException(404)
    r.ctx[f"decision:{gid}"] = action
    r.ctx[f"note:{gid}"] = note
    evt = r.events.pop(gid, None)
    if evt:
        evt.set()
    # decision/note 不在 state.json 中持久化（snapshot 跳过），但保留 ctx 供下次决策参考
    await safe_persist(r)
    return r.snapshot()

@app.get("/api/runs/{rid}/stream")
async def stream_run(rid: str, since: int = 0):
    """SSE 实时推送运行状态。

    since: 从 history 的第 N 条开始发送（0 = 全量重放；>0 = 只发新事件）
      - start: since=0（前端空白，从头订阅）
      - continue: since=当前 history 长度（前端已有旧事件，只看新事件）
      - inspect 历史: since=0（全量回放）
    """
    r = engine["runs"].get(rid)
    if not r: raise HTTPException(404)

    async def event_generator():
        last_len = max(0, min(since, len(r.history)))
        # 标记断点事件（前端用来识别"从这里开始是新的"）
        if last_len < len(r.history):
            yield f"data: {json.dumps({'e': 'stream:resume', 'since': last_len, 'total': len(r.history)})}\n\n"
        while True:
            # 检查新日志
            current = r.history
            if len(current) > last_len:
                for entry in current[last_len:]:
                    yield f"data: {json.dumps(entry)}\n\n"
                last_len = len(current)

            # 结束条件：_state 不再 running
            #  - non_running + is_completed → 完成
            #  - non_running + not completed → 暂停/中断（用户可继续）
            #  - exception → 异常（用户决策）
            if r._state != "running":
                yield f"data: {json.dumps({'e': 'stream:done', 'state': r._state, 'status': r._status_for_ui()})}\n\n"
                break

            await asyncio.sleep(0.5)

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "Connection": "keep-alive"}
    )


@app.get("/api/files")
async def read_file(path: str = Query(...)):
    """解析文件路径：先在 agent-workspace 查，再回退到项目根。

    所有产物文件实际只存在于 agent-workspace 内（隔离原则）。
    仅当路径已显式包含 .agent-workspace 前缀或超出 workspace 时才回退项目根。
    """
    full_workspace = os.path.abspath(os.path.join(OPEN_CODE_WORKSPACE, path))
    full_project = os.path.abspath(os.path.join(PROJECT_PATH, path))

    if not full_workspace.startswith(os.path.abspath(OPEN_CODE_WORKSPACE)):
        if not full_project.startswith(os.path.abspath(PROJECT_PATH)):
            raise HTTPException(403, "越界访问")

    if os.path.exists(full_workspace):
        with open(full_workspace, "r", encoding="utf-8") as f:
            return {"content": f.read()}
    if os.path.exists(full_project):
        with open(full_project, "r", encoding="utf-8") as f:
            return {"content": f.read()}
    raise HTTPException(404, f"文件不存在: {path}")


@app.get("/")
async def root():
    return HTMLResponse(open("ui.html").read())
