#!/usr/bin/env bash
# 生产形态一键启动：后端托管前端构建产物，访问 http://localhost:8080
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/scripts/env.sh"
cd "$ROOT"

JAR="backend/target/lanchat-backend.jar"
if [ ! -f "$JAR" ]; then
  echo "[run] 未找到 $JAR，先执行构建…"
  "$ROOT/scripts/build.sh"
fi

echo "[run] 启动 LanChat…（Ctrl+C 退出）"
exec java -jar "$JAR" "$@"
