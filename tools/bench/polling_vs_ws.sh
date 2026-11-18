#!/usr/bin/env bash
# 轮询 vs WebSocket 实时方案对比实验（EV-PERF-04）
# 编译并运行 tools/bench/PollingVsWsMain.java，结果写入 assets/bench/polling_vs_ws.csv。
#
# 用法：
#   tools/bench/polling_vs_ws.sh
#   tools/bench/polling_vs_ws.sh --duration 30 --send-interval-ms 1000 --poll-interval-ms 3000
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
source "$ROOT/scripts/env.sh"
cd "$ROOT"

OUT_DIR=tools/bench/out
mkdir -p "$OUT_DIR"
javac -encoding UTF-8 -d "$OUT_DIR" tools/bench/*.java
exec java -cp "$OUT_DIR" com.lanchat.bench.PollingVsWsMain "$@"
