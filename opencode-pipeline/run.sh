#!/bin/bash
# OpenCode Pipeline Sidecar
# 放在 opencode-pipeline 项目根目录
set -e
cd "$(dirname "$0")"

# PROJECT_PATH 由 sidecar.py 内部从 __file__ 动态推断，无需 env 覆盖

# 依赖检查
python3 -c "import fastapi, uvicorn" 2>/dev/null || {
    echo "[run.sh] 安装依赖 fastapi uvicorn..."
    pip install --user fastapi uvicorn 2>&1 | tail -3
}

# 启动 sidecar
exec python3 -m uvicorn sidecar:app --host 0.0.0.0 --port 8080 --log-level info
