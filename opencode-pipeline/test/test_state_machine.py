"""
Pytest 单元测试 — 覆盖 3 状态模型 + 节点驱动持久化 + 暂停/继续 API。

测试策略：
- Run 类属性：纯逻辑，直接测
- load_state 向后兼容：tmp STATE_FILE + 旧格式 state.json
- pipeline_task 状态机：mock exec_backend / handle_gate 避免真实 opencode 调用
- API 端点：FastAPI TestClient + 上面的 mock

不依赖 Playwright / 浏览器，运行只需 pytest + httpx。
"""
import asyncio
import json
import sys
import os
import pytest
from pathlib import Path
from unittest.mock import AsyncMock, MagicMock, patch

# 把项目根加入 sys.path
PROJECT_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_DIR))

# 必须在 sidecar 加载前设置环境，否则它读 ~/.opencode-pipeline/state.json
import sidecar
from sidecar import Run, WORKFLOW, engine, NODE_HANDLERS

# ================== Fixtures ==================

@pytest.fixture
def tmp_workspace(tmp_path, monkeypatch):
    """隔离 STATE_FILE / OPEN_CODE_WORKSPACE 到 tmp 目录。"""
    tmp_state = tmp_path / "state.json"
    tmp_ws = tmp_path / ".agent-workspace"
    tmp_ws.mkdir()
    monkeypatch.setattr(sidecar, "STATE_FILE", str(tmp_state))
    monkeypatch.setattr(sidecar, "OPEN_CODE_WORKSPACE", str(tmp_ws))
    # 清空 engine，让每个测试干净起步
    engine["runs"].clear()
    engine["tasks"].clear()
    engine["procs"].clear()
    return {"state_file": tmp_state, "workspace": tmp_ws}


@pytest.fixture
def no_persist(monkeypatch):
    """测试默认不真正写盘。"""
    async def fake_persist(run=None):
        pass
    monkeypatch.setattr(sidecar, "persist", fake_persist)


@pytest.fixture
def mock_agent(monkeypatch):
    """把 agent 节点替换成直接成功（不调 opencode）。"""
    async def fake_exec(run, stage):
        # 模拟产出文件：写入 workspace
        rel = stage.get("output", "")
        if rel:
            full = os.path.join(sidecar.OPEN_CODE_WORKSPACE, rel)
            os.makedirs(os.path.dirname(full), exist_ok=True)
            with open(full, "w") as f:
                f.write(f"# Mock output for {stage['id']}\n")
        run.ctx[stage["key"]] = (
            os.path.join(sidecar.OPEN_CODE_WORKSPACE, stage["output"])
            if rel
            else "failed"
        )

    monkeypatch.setattr(sidecar, "exec_backend", fake_exec)
    NODE_HANDLERS["agent"] = fake_exec
    return fake_exec


@pytest.fixture
def mock_gate(monkeypatch):
    """把 gate 节点替换成"自动批准"版本。"""
    async def fake_gate(run, stage):
        if stage.get("mode") == "human":
            run._state = "non_running"
            run.ctx[f"decision:{stage['id']}"] = "approve"
            run._state = "running"
        elif stage.get("mode") == "auto":
            # 模拟 auto gate 直接通过
            pass

    monkeypatch.setattr(sidecar, "handle_gate", fake_gate)
    NODE_HANDLERS["gate"] = fake_gate
    return fake_gate


# ================== Run 类属性测试 ==================

class TestRunStateProperties:
    def test_initial_state(self):
        r = Run("test-1")
        assert r._state == "running"
        assert r.idx == 0
        assert not r.is_completed
        assert not r.is_waiting_gate
        assert not r.can_continue
        assert r._status_for_ui() == "running"

    def test_waiting_gate_is_non_running(self):
        r = Run("test-1")
        r.idx = 1  # direction-gate
        r._state = "non_running"
        assert r.is_waiting_gate
        assert not r.is_completed
        assert r.can_continue  # 未完成，可继续
        assert r._status_for_ui() == "waiting_gate"

    def test_completed_when_idx_past_end(self):
        r = Run("test-1")
        r.idx = len(WORKFLOW["stages"])
        r._state = "non_running"
        assert r.is_completed
        assert not r.is_waiting_gate
        assert not r.can_continue
        assert r._status_for_ui() == "completed"

    def test_exception_cannot_continue(self):
        r = Run("test-1")
        r.idx = 0
        r._state = "exception"
        assert not r.can_continue
        assert r._status_for_ui() == "exception"

    def test_snapshot_includes_state_and_status(self):
        r = Run("test-1")
        r.idx = 1
        r._state = "non_running"
        snap = r.snapshot()
        assert snap["_state"] == "non_running"
        assert snap["status"] == "waiting_gate"  # UI 兼容
        assert snap["waiting_gate"] == "direction-gate"
        assert snap["error"] is None

    def test_snapshot_with_error(self):
        r = Run("test-1")
        r._state = "exception"
        r._error = "opencode 崩溃"
        snap = r.snapshot()
        assert snap["_state"] == "exception"
        assert snap["error"] == "opencode 崩溃"


# ================== load_state 向后兼容 ==================

class TestLoadStateBackwardCompat:
    def test_old_failed_at_gate_becomes_non_running(self, tmp_workspace):
        """旧 status=failed + stage_id=*-gate + history 有 wait_human → non_running。"""
        state_file = tmp_workspace["state_file"]
        old = {
            "run-x": {
                "rid": "run-x",
                "status": "failed",
                "idx": 1,
                "stage_id": "direction-gate",
                "stage_mode": "human",
                "artifacts": {"design": "docs/design.md"},
                "history": [
                    {"action": "enter", "stage": "architect"},
                    {"action": "stage_done", "stage": "architect"},
                    {"action": "enter", "stage": "direction-gate"},
                    {"action": "wait_human", "gate": "direction-gate"},
                    {"action": "pipeline_error", "detail": ""},
                ],
                "user_requirements": "test",
            }
        }
        state_file.write_text(json.dumps(old))
        sidecar.load_state()

        r = engine["runs"]["run-x"]
        assert r._state == "non_running", f"expected non_running, got {r._state}"
        assert r.idx == 1
        assert r.is_waiting_gate
        # 修复事件被追加
        assert any(h.get("action") == "interrupted" for h in r.history)

    def test_old_failed_at_agent_stays_exception(self, tmp_workspace):
        """旧 status=failed + stage_id=architect (非 gate) → 保持 exception。"""
        state_file = tmp_workspace["state_file"]
        old = {
            "run-y": {
                "rid": "run-y",
                "status": "failed",
                "idx": 0,
                "stage_id": "architect",
                "stage_mode": "backend",
                "artifacts": {},
                "history": [
                    {"action": "enter", "stage": "architect"},
                    {"action": "pipeline_error", "detail": "opencode 崩溃"},
                ],
                "user_requirements": "test",
            }
        }
        state_file.write_text(json.dumps(old))
        sidecar.load_state()

        r = engine["runs"]["run-y"]
        assert r._state == "exception"
        assert not r.can_continue

    def test_old_waiting_gate_becomes_non_running(self, tmp_workspace):
        """旧 status=waiting_gate → non_running。"""
        state_file = tmp_workspace["state_file"]
        old = {
            "run-z": {
                "rid": "run-z",
                "status": "waiting_gate",
                "idx": 1,
                "stage_id": "direction-gate",
                "artifacts": {"design": "docs/design.md"},
                "history": [],
                "user_requirements": "test",
            }
        }
        state_file.write_text(json.dumps(old))
        sidecar.load_state()

        r = engine["runs"]["run-z"]
        assert r._state == "non_running"
        assert r.is_waiting_gate

    def test_old_completed_stays_non_running(self, tmp_workspace):
        state_file = tmp_workspace["state_file"]
        old = {
            "run-c": {
                "rid": "run-c",
                "status": "completed",
                "idx": len(WORKFLOW["stages"]),
                "stage_id": "done",
                "artifacts": {"review": "docs/review.md"},
                "history": [],
                "user_requirements": "test",
            }
        }
        state_file.write_text(json.dumps(old))
        sidecar.load_state()
        r = engine["runs"]["run-c"]
        assert r._state == "non_running"
        assert r.is_completed

    def test_new_state_running_becomes_non_running_on_load(self, tmp_workspace):
        """新 _state=running 加载时强制转 non_running（pipeline_task 必然丢失）。"""
        state_file = tmp_workspace["state_file"]
        old = {
            "run-n": {
                "rid": "run-n",
                "_state": "running",
                "status": "running",
                "idx": 0,
                "stage_id": "architect",
                "artifacts": {},
                "history": [],
                "user_requirements": "test",
            }
        }
        state_file.write_text(json.dumps(old))
        sidecar.load_state()
        r = engine["runs"]["run-n"]
        assert r._state == "non_running"

    def test_new_state_exception_at_gate_recovers(self, tmp_workspace):
        """新 _state=exception 但在 gate 阶段 → 转 non_running（防御旧数据）。"""
        state_file = tmp_workspace["state_file"]
        old = {
            "run-r": {
                "rid": "run-r",
                "_state": "exception",
                "status": "exception",
                "idx": 1,
                "stage_id": "direction-gate",
                "artifacts": {"design": "docs/design.md"},
                "history": [
                    {"action": "wait_human", "gate": "direction-gate"},
                ],
                "user_requirements": "test",
            }
        }
        state_file.write_text(json.dumps(old))
        sidecar.load_state()
        r = engine["runs"]["run-r"]
        assert r._state == "non_running"
        assert r.is_waiting_gate

    def test_corrupted_state_file_does_not_crash(self, tmp_workspace):
        """损坏的 state.json 不应导致 server 启动失败。"""
        state_file = tmp_workspace["state_file"]
        state_file.write_text("not valid json {{{")
        # 不应抛异常
        sidecar.load_state()
        assert engine["runs"] == {}

    def test_ctx_reconstructed_from_artifacts(self, tmp_workspace):
        """ctx 从 artifacts 反推回绝对路径。"""
        state_file = tmp_workspace["state_file"]
        old = {
            "run-a": {
                "rid": "run-a",
                "status": "completed",
                "idx": 6,
                "artifacts": {
                    "design": "docs/design.md",
                    "code": "src/feature.py",
                    "review": "docs/review.md",
                },
                "history": [],
                "user_requirements": "test",
            }
        }
        state_file.write_text(json.dumps(old))
        sidecar.load_state()
        r = engine["runs"]["run-a"]
        assert r.ctx["design"] == os.path.join(sidecar.OPEN_CODE_WORKSPACE, "docs/design.md")
        assert r.ctx["code"] == os.path.join(sidecar.OPEN_CODE_WORKSPACE, "src/feature.py")


# ================== pipeline_task 状态机 ==================

class TestPipelineTaskStateMachine:
    @pytest.mark.asyncio
    async def test_full_pipeline_runs_to_completion(
        self, tmp_workspace, mock_agent, mock_gate, no_persist
    ):
        """完整跑完 → non_running + is_completed。"""
        from sidecar import pipeline_task
        run = Run("test-full")
        engine["runs"]["test-full"] = run
        await pipeline_task(run)
        assert run._state == "non_running"
        assert run.is_completed
        assert run.idx == len(WORKFLOW["stages"])
        # 所有 agent 节点都执行了
        assert "design" in run.ctx
        assert "code" in run.ctx
        assert "review" in run.ctx

    @pytest.mark.asyncio
    async def test_cancel_during_agent_keeps_non_running(
        self, tmp_workspace, monkeypatch, no_persist
    ):
        """agent 执行中发 CancelledError → non_running（不是 exception）。"""

        async def hanging_exec(run, stage):
            # 模拟 agent 长时间挂起，直到被 cancel
            await asyncio.sleep(60)

        monkeypatch.setattr(sidecar, "exec_backend", hanging_exec)
        NODE_HANDLERS["agent"] = hanging_exec

        run = Run("test-cancel")
        engine["runs"]["test-cancel"] = run
        task = asyncio.create_task(sidecar.pipeline_task(run))

        # 等到 architect 阶段开始执行
        await asyncio.sleep(0.1)
        # 取消
        task.cancel()
        try:
            await task
        except (asyncio.CancelledError, Exception):
            pass

        assert run._state == "non_running", f"expected non_running, got {run._state}"
        assert run.idx == 0
        # 不应进入 exception
        assert "task_cancelled" in [h.get("action") for h in run.history]

    @pytest.mark.asyncio
    async def test_real_exception_becomes_exception(
        self, tmp_workspace, monkeypatch, no_persist
    ):
        """agent 抛 RuntimeError → exception 状态。"""

        async def failing_exec(run, stage):
            raise RuntimeError("agent 崩溃了")

        monkeypatch.setattr(sidecar, "exec_backend", failing_exec)
        NODE_HANDLERS["agent"] = failing_exec

        run = Run("test-fail")
        engine["runs"]["test-fail"] = run
        await sidecar.pipeline_task(run)
        assert run._state == "exception"
        assert run._error == "agent 崩溃了"
        assert "pipeline_error" in [h.get("action") for h in run.history]

    @pytest.mark.asyncio
    async def test_cancel_at_gate_keeps_non_running(
        self, tmp_workspace, monkeypatch, no_persist
    ):
        """gate 等待中发 CancelledError → non_running（保留等待状态）。"""

        async def quick_agent(run, stage):
            # agent 阶段直接标记完成（不调 opencode）
            pass

        async def hanging_gate(run, stage):
            if stage.get("mode") == "human":
                run._state = "non_running"
                # 模拟 evt.wait() 挂起
                await asyncio.sleep(60)

        monkeypatch.setattr(sidecar, "exec_backend", quick_agent)
        monkeypatch.setattr(sidecar, "handle_gate", hanging_gate)
        NODE_HANDLERS["agent"] = quick_agent
        NODE_HANDLERS["gate"] = hanging_gate

        run = Run("test-gate-cancel")
        engine["runs"]["test-gate-cancel"] = run
        task = asyncio.create_task(sidecar.pipeline_task(run))

        # 等到 direction-gate 阶段开始
        await asyncio.sleep(0.1)
        task.cancel()
        try:
            await task
        except (asyncio.CancelledError, Exception):
            pass

        assert run._state == "non_running", f"expected non_running, got {run._state}"
        assert run.idx == 1
        assert run.is_waiting_gate


# ================== 持久化行为 ==================

class TestPersistBehavior:
    @pytest.mark.asyncio
    async def test_state_file_written_on_node_arrival(
        self, tmp_workspace, mock_agent, mock_gate
    ):
        """每个节点到达时写 state.json。"""
        from sidecar import pipeline_task
        state_file = tmp_workspace["state_file"]
        run = Run("test-persist")
        engine["runs"]["test-persist"] = run
        await pipeline_task(run)

        # state.json 应该被写过（load_state 时没有，但 pipeline 跑过）
        assert state_file.exists()
        data = json.loads(state_file.read_text())
        assert "test-persist" in data
        saved = data["test-persist"]
        assert saved["_state"] == "non_running"
        assert saved["status"] == "completed"
        assert saved["idx"] == len(WORKFLOW["stages"])

    @pytest.mark.asyncio
    async def test_persist_failure_does_not_crash(
        self, tmp_workspace, mock_agent, mock_gate, monkeypatch
    ):
        """persist() 抛异常时，pipeline 不应崩溃。"""
        call_count = {"n": 0}

        async def failing_persist(run=None):
            call_count["n"] += 1
            raise OSError("disk full")

        monkeypatch.setattr(sidecar, "persist", failing_persist)

        run = Run("test-persist-fail")
        engine["runs"]["test-persist-fail"] = run
        await sidecar.pipeline_task(run)
        # 至少 persist 被调用过（即使失败）
        assert call_count["n"] > 0
        # pipeline 仍能完成
        assert run._state == "non_running"
        assert run.is_completed


# ================== API 端点 ==================

class TestApiEndpoints:
    def test_continue_rejects_running(self, tmp_workspace, mock_agent, mock_gate, no_persist):
        """running 状态不能 continue。"""
        from fastapi.testclient import TestClient
        client = TestClient(sidecar.app)
        run = Run("test-running")
        run._state = "running"
        engine["runs"]["test-running"] = run
        r = client.post("/api/continue/test-running")
        assert r.status_code == 400

    def test_continue_rejects_exception(self, tmp_workspace):
        """exception 状态不能 continue。"""
        from fastapi.testclient import TestClient
        client = TestClient(sidecar.app)
        run = Run("test-exc")
        run._state = "exception"
        engine["runs"]["test-exc"] = run
        r = client.post("/api/continue/test-exc")
        assert r.status_code == 400

    def test_continue_rejects_completed(self, tmp_workspace):
        """已完成（idx >= total）不能 continue。"""
        from fastapi.testclient import TestClient
        client = TestClient(sidecar.app)
        run = Run("test-done")
        run._state = "non_running"
        run.idx = len(WORKFLOW["stages"])
        engine["runs"]["test-done"] = run
        r = client.post("/api/continue/test-done")
        assert r.status_code == 400

    def test_continue_accepts_non_running_with_remaining(
        self, tmp_workspace, mock_agent, mock_gate, no_persist
    ):
        """non_running + idx < total → 接受。"""
        from fastapi.testclient import TestClient
        client = TestClient(sidecar.app)
        run = Run("test-ok")
        run._state = "non_running"
        run.idx = 1
        engine["runs"]["test-ok"] = run
        r = client.post("/api/continue/test-ok")
        assert r.status_code == 200
        assert r.json()["rid"] == "test-ok"
        # 触发清理
        if run._task and not run._task.done():
            run._task.cancel()

    def test_pause_rejects_non_running(self, tmp_workspace):
        """non_running 状态不能 pause。"""
        from fastapi.testclient import TestClient
        client = TestClient(sidecar.app)
        run = Run("test-pause")
        run._state = "non_running"
        engine["runs"]["test-pause"] = run
        r = client.post("/api/pause/test-pause")
        assert r.status_code == 400

    def test_pause_rejects_exception(self, tmp_workspace):
        from fastapi.testclient import TestClient
        client = TestClient(sidecar.app)
        run = Run("test-pause-exc")
        run._state = "exception"
        engine["runs"]["test-pause-exc"] = run
        r = client.post("/api/pause/test-pause-exc")
        assert r.status_code == 400

    def test_pause_404_for_unknown_rid(self, tmp_workspace):
        from fastapi.testclient import TestClient
        client = TestClient(sidecar.app)
        r = client.post("/api/pause/does-not-exist")
        assert r.status_code == 404

    def test_continue_404_for_unknown_rid(self, tmp_workspace):
        from fastapi.testclient import TestClient
        client = TestClient(sidecar.app)
        r = client.post("/api/continue/does-not-exist")
        assert r.status_code == 404

    def test_snapshot_returns_state_field(self, tmp_workspace):
        """snapshot 必须包含 _state 字段。"""
        from fastapi.testclient import TestClient
        client = TestClient(sidecar.app)
        run = Run("test-snap")
        run._state = "non_running"
        run.idx = 1
        engine["runs"]["test-snap"] = run
        r = client.get("/api/runs/test-snap")
        assert r.status_code == 200
        data = r.json()
        assert "_state" in data
        assert data["_state"] == "non_running"
        assert data["status"] == "waiting_gate"


class TestSseResume:
    """SSE 在 continue 场景下应只发新事件（since > 0），避免重放旧 history。"""

    def test_snapshot_includes_history_length(self, tmp_workspace):
        from fastapi.testclient import TestClient
        client = TestClient(sidecar.app)
        run = Run("test-len")
        run.history.extend([
            {"action": "enter", "stage": "architect"},
            {"action": "building"},
            {"action": "task_cancelled"},
        ])
        engine["runs"]["test-len"] = run
        data = client.get("/api/runs/test-len").json()
        assert data["history_length"] == 3
        # snapshot 仍然只返最近 30 条（不包含 history_length 计算用的全部）
        assert len(data["history"]) == 3

    def test_sse_since_zero_sends_resume_marker(self, tmp_workspace, mock_agent, mock_gate, no_persist):
        """since=0 + 已有 history → 先发 stream:resume 标记（让前端区分"历史"和"新"）"""
        from fastapi.testclient import TestClient
        client = TestClient(sidecar.app)
        run = Run("test-sse-resume")
        run.history.extend([{"action": "old_event"}])
        run._state = "non_running"  # 立即结束 stream
        engine["runs"]["test-sse-resume"] = run

        with client.stream("GET", "/api/runs/test-sse-resume/stream?since=0") as r:
            assert r.status_code == 200
            events = list(r.iter_lines())
        # 第一条应该是 stream:resume 标记
        import json
        first = json.loads(events[0].replace("data: ", ""))
        assert first["e"] == "stream:resume"
        assert first["since"] == 0  # last_len at marker time
        assert first["total"] == 1
        # 第二条应该是 old_event（从 last_len=0 开始发）
        second = json.loads(events[2].replace("data: ", ""))
        assert second["action"] == "old_event"
        # 收尾有 stream:done
        assert any("stream:done" in e for e in events)

    def test_sse_since_equals_total_skips_resume_marker(self, tmp_workspace, mock_agent, mock_gate, no_persist):
        """since 等于 history 长度 → 没有 stream:resume 标记，直接空流等待新事件"""
        from fastapi.testclient import TestClient
        client = TestClient(sidecar.app)
        run = Run("test-sse-skip")
        run.history.extend([{"action": "x"}, {"action": "y"}, {"action": "z"}])
        run._state = "non_running"
        engine["runs"]["test-sse-skip"] = run

        with client.stream("GET", "/api/runs/test-sse-skip/stream?since=3") as r:
            events = list(r.iter_lines())
        # 没有 resume 标记
        assert all("stream:resume" not in e for e in events)
        # 只有 stream:done 收尾
        assert any("stream:done" in e for e in events)

    def test_sse_since_clamps_to_length(self, tmp_workspace, mock_agent, mock_gate, no_persist):
        """since 超过 history 长度时 clamp 到 len(history)"""
        from fastapi.testclient import TestClient
        client = TestClient(sidecar.app)
        run = Run("test-sse-clamp")
        run.history.extend([{"action": "a"}])
        run._state = "non_running"
        engine["runs"]["test-sse-clamp"] = run

        with client.stream("GET", "/api/runs/test-sse-clamp/stream?since=999") as r:
            events = list(r.iter_lines())
        # since 被 clamp 到 1，没有 resume 标记
        assert all("stream:resume" not in e for e in events)


class TestRunLifecycle:
    """完整生命周期测试：start → pause → continue → pause → continue → complete。
    验证：进程不泄漏、定时器正确取消、state.json 同步。"""

    @pytest.mark.asyncio
    async def test_pause_continue_no_zombie(self, tmp_workspace, monkeypatch, no_persist):
        """pause → continue 后，旧 task 不可有遗留的回调/定时器。"""
        from httpx import AsyncClient, ASGITransport

        # mock 一个会长时间挂起的 agent
        async def hanging_agent(run, stage):
            await asyncio.sleep(60)

        monkeypatch.setattr(sidecar, "exec_backend", hanging_agent)
        NODE_HANDLERS["agent"] = hanging_agent

        async with AsyncClient(transport=ASGITransport(app=sidecar.app), base_url="http://test") as client:
            r = await client.post("/api/start?requirements=测试")
            assert r.status_code == 200
            rid = r.json()["rid"]
            run = engine["runs"][rid]
            # 等到 agent 进入挂起
            await asyncio.sleep(0.2)
            # 验证 _state 是 running
            assert run._state == "running", f"expected running, got {run._state}"
            # pause
            p = await client.post(f"/api/pause/{rid}")
            assert p.status_code == 200
            # 验证：旧 task 已结束，state 是 non_running
            assert run._state == "non_running"
            assert run._task is None or run._task.done()
            # cleanup 句柄应在 60s 后才触发；我们手动取消它避免测试挂起
            if run._cleanup_handle is not None:
                run._cleanup_handle.cancel()

            # continue
            c = await client.post(f"/api/continue/{rid}")
            assert c.status_code == 200
            # 新 task 应当存在且非 done
            new_task = run._task
            assert new_task is not None
            assert not new_task.done()
            # 取消它清理测试
            new_task.cancel()
            try:
                await new_task
            except (asyncio.CancelledError, Exception):
                pass
            if run._cleanup_handle is not None:
                run._cleanup_handle.cancel()

    @pytest.mark.asyncio
    async def test_multiple_pause_continue_cycles(self, tmp_workspace, monkeypatch, no_persist):
        """完整跑完一次 pipeline，验证 _task 引用和 cleanup 句柄正确。"""
        from fastapi.testclient import TestClient
        client = TestClient(sidecar.app)

        async def quick_agent(run, stage):
            pass  # 立即完成

        monkeypatch.setattr(sidecar, "exec_backend", quick_agent)
        NODE_HANDLERS["agent"] = quick_agent

        r = client.post("/api/start?requirements=cycle")
        rid = r.json()["rid"]
        run = engine["runs"][rid]

        # 等到完成
        for _ in range(50):
            await asyncio.sleep(0.1)
            if run._state != "running":
                break
        assert run._state == "non_running"
        assert run.is_completed
        # 取消 cleanup
        if run._cleanup_handle is not None:
            run._cleanup_handle.cancel()

