"""
Playwright autonomous test for sidecar UI.

Verifies:
1. UI loads and shows workflow structure
2. Clicking "启动工作流" starts a new run
3. SSE events flow through to UI (the infinite recursion fix)
4. Architect stage output appears in real-time log
5. No fatal errors in browser console

After test, take screenshots for verification.

Usage:
    # 1. Start sidecar on a test port
    setsid bash -c "exec python3 -m uvicorn sidecar:app --host 127.0.0.1 --port 8765" > /tmp/sidecar.log 2>&1 < /dev/null &

    # 2. Activate venv and run
    source .venv/bin/activate
    python3 test/test_sidecar.py
"""
import os
import sys
from pathlib import Path
from playwright.sync_api import sync_playwright, TimeoutError as PWTimeout


# Project layout: this file is in <project>/test/
TEST_DIR = Path(__file__).resolve().parent
PROJECT_DIR = TEST_DIR.parent
SCREENSHOT_DIR = TEST_DIR / "screenshots"
SCREENSHOT_DIR.mkdir(exist_ok=True)


def run_test():
    results = {"passed": [], "failed": []}
    base_url = os.environ.get("SIDECAR_URL", "http://127.0.0.1:8765")
    screenshot_dir = str(SCREENSHOT_DIR)

    def check(name, cond, detail=""):
        if cond:
            results["passed"].append(name)
            print(f"  ✓ {name}" + (f" — {detail}" if detail else ""))
        else:
            results["failed"].append(f"{name}: {detail}")
            print(f"  ✗ {name} — {detail}")
        return cond

    print("=" * 60)
    print("PLAYWRIGHT AUTONOMOUS TEST — Sidecar UI")
    print("=" * 60)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        ctx = browser.new_context(viewport={"width": 1400, "height": 900})
        page = ctx.new_page()

        # Capture console messages
        console_msgs = []
        page.on("console", lambda msg: console_msgs.append(f"[{msg.type}] {msg.text}"))
        page.on("pageerror", lambda err: console_msgs.append(f"[pageerror] {err}"))

        try:
            # === Step 1: Load UI ===
            print("\n[Step 1] Load UI at", base_url)
            page.goto(base_url, timeout=10000)
            check("UI loads", page.title() is not None)

            # Wait for workflow definition loaded (drives stages-list)
            try:
                page.wait_for_function(
                    "window.WORKFLOW_STAGES && window.WORKFLOW_STAGES.length === 6",
                    timeout=10000,
                )
                check("Workflow definition loaded", True,
                      f"WORKFLOW_STAGES has {page.evaluate('window.WORKFLOW_STAGES.length')} stages")
            except PWTimeout:
                check("Workflow definition loaded", False, "WORKFLOW_STAGES not set after 10s")

            # Take initial screenshot
            page.screenshot(path=f"{screenshot_dir}/test-01-loaded.png")
            print(f"  📸 saved test-01-loaded.png")

            # === Step 2: Verify the append_history recursion fix ===
            # The bug was: append_history called itself recursively, causing
            # pipeline_task to crash. With the fix, the "工作流已启动" log
            # should be followed by architect stage events.
            print("\n[Step 2] Click 启动工作流 button")
            page.locator("#requirements").fill("Playwright autonomous test: 设计一个缓存层模块")
            page.locator("#startBtn").click()

            # Wait for the "工作流已启动" log to appear
            page.wait_for_function(
                "Array.from(document.querySelectorAll('#log .log-line'))"
                ".some(el => el.innerText.includes('工作流已启动'))",
                timeout=5000,
            )
            check("工作流已启动 log appears", True)

            # Critical assertion: wait for architect stage events
            # The "entering stage architect" should fire because the append_history
            # recursion is fixed. Without the fix, no further events would arrive.
            print("\n[Step 3] Wait for architect stage events (recursion fix verification)")
            try:
                page.wait_for_function(
                    "Array.from(document.querySelectorAll('#log .log-line'))"
                    ".some(el => el.innerText.includes('architect') && el.innerText.includes('enter'))",
                    timeout=15000,
                )
                check("architect enter event arrives", True,
                      "append_history recursion fix verified")
            except PWTimeout:
                check("architect enter event arrives", False,
                      "TIMEOUT 15s — recursion bug still present!")

            # Check for the building event
            try:
                page.wait_for_function(
                    "Array.from(document.querySelectorAll('#log .log-line'))"
                    ".some(el => el.innerText.includes('building') || "
                    "el.innerText.includes('思考中') || el.innerText.includes('ls'))",
                    timeout=20000,
                )
                check("architect produces output (building/ls/thinking)", True)
            except PWTimeout:
                check("architect produces output", False,
                      "TIMEOUT 20s — opencode not producing output")

            # Take screenshot mid-run
            page.screenshot(path=f"{screenshot_dir}/test-02-running.png")
            print(f"  📸 saved test-02-running.png")

            # === Step 4: Verify no JS errors ===
            print("\n[Step 4] Browser console error check")
            errors = [m for m in console_msgs if m.startswith("[error]") or m.startswith("[pageerror]")]
            if errors:
                print("  Console errors:")
                for e in errors[:5]:
                    print(f"    {e}")
            check("no critical JS errors", len(errors) == 0, f"{len(errors)} errors found")

            # === Step 5: Verify history list shows the new run ===
            print("\n[Step 5] History list shows new run")
            try:
                page.wait_for_function(
                    "Array.from(document.querySelectorAll('#runs > div'))"
                    ".some(el => el.innerText.includes('run-') && "
                    "!el.innerText.includes('中断') && "
                    "(el.innerText.includes('architect') || el.innerText.includes('coding')))",
                    timeout=5000,
                )
                check("history list contains active run", True)
            except PWTimeout:
                check("history list contains active run", False)

            page.screenshot(path=f"{screenshot_dir}/test-03-final.png")
            print(f"  📸 saved test-03-final.png")

            # Capture log count
            log_count = page.locator("#log .log-line").count()
            print(f"\n  Total log lines: {log_count}")
            check("substantial log activity", log_count >= 3,
                  f"log_count={log_count}")

        finally:
            # Cleanup
            ctx.close()
            browser.close()

    # === Report ===
    print("\n" + "=" * 60)
    print(f"RESULTS: {len(results['passed'])} passed, {len(results['failed'])} failed")
    print("=" * 60)
    for p in results["passed"]:
        print(f"  ✓ {p}")
    for f in results["failed"]:
        print(f"  ✗ {f}")

    return 0 if not results["failed"] else 1


if __name__ == "__main__":
    sys.exit(run_test())
