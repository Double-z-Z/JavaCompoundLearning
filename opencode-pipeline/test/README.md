# Sidecar UI Tests

Playwright-based autonomous tests for the OpenCode Pipeline sidecar.

## What it tests

The test (`test_sidecar.py`) verifies the critical `append_history` recursion fix and the end-to-end workflow startup path:

1. **UI loads** — page renders without errors
2. **Workflow definition loaded** — `WORKFLOW_STAGES` populated via `/api/workflow`
3. **"工作流已启动" log appears** — `POST /api/start` succeeds
4. **Architect enter event arrives** — *the critical assertion*: without the recursion fix, `append_history` would loop infinitely and the `enter` event would never reach SSE
5. **Architect produces output** — `opencode` subprocess actually starts and produces stdout (the `--dir` flag is honored)
6. **No critical JS errors** — browser console is clean
7. **History list contains active run** — the new run appears in the left pane
8. **Substantial log activity** — at least 3 log lines emitted (proves SSE full chain works)

Screenshots from the last successful run are in `screenshots/`:

- `test-01-loaded.png` — initial UI state
- `test-02-running.png` — middle of architect stage
- `test-03-final.png` — final state after architect events

## Prerequisites

- **Python 3.10+**
- **A running sidecar** on a test port (8765 by default)
- **Playwright Python** with chromium installed

## Setup

```bash
# 1. Create venv (one-time)
cd /home/dz-fedora/workspace/JavaLearning/opencode-pipeline
uv venv .venv
source .venv/bin/activate

# 2. Install playwright
uv pip install playwright

# 3. Install chromium browser (one-time, ~150 MB)
python3 -m playwright install chromium
```

## Running

```bash
# Terminal 1: Start sidecar in background (use a non-default port to avoid conflict with 8080)
cd /home/dz-fedora/workspace/JavaLearning/opencode-pipeline
setsid bash -c "exec python3 -m uvicorn sidecar:app --host 127.0.0.1 --port 8765" \
  > /tmp/sidecar-test.log 2>&1 < /dev/null &

# Terminal 2: Run test
cd /home/dz-fedora/workspace/JavaLearning/opencode-pipeline
source .venv/bin/activate
python3 test/test_sidecar.py
```

The test takes about 30 seconds. On success, all 8 assertions pass and screenshots are saved to `test/screenshots/`.

## Cleanup

```bash
# Stop test sidecar
pkill -f "uvicorn sidecar:app --host 127.0.0.1 --port 8765"
```

## Customizing the target

Override the target URL with an env var:

```bash
SIDECAR_URL=http://192.168.1.10:8080 python3 test/test_sidecar.py
```

## Test artifacts

- `test/test_sidecar.py` — test script
- `test/screenshots/test-0{1,2,3}-*.png` — last successful run screenshots
- `/tmp/sidecar-test.log` — sidecar console output (during test)
- `~/.cache/ms-playwright/` — chromium binary (shared with other tools)

## History

- **2026-06-06**: Initial test created to verify the `append_history` infinite recursion bug fix (the bug caused `pipeline_task` to crash silently before any events were emitted, leaving the UI stuck on "工作流已启动" with no follow-up activity).
