#!/usr/bin/env bash
# 压测期间采集后端进程内存（RSS + JVM 堆），用于 EV-PERF-07 观察。
#
# 用法：
#   tools/bench/monitor_backend.sh <pid> <out.csv> <duration_seconds> [interval_seconds]
#
# 说明：优先用 jcmd GC.heap_info 读 JVM 堆（需要与本进程同一用户/同一 JDK），
# 失败时仅记录 /proc/<pid>/status 的 VmRSS。
set -euo pipefail

PID="${1:?用法: monitor_backend.sh <pid> <out.csv> <duration_seconds> [interval_seconds]}"
OUT="${2:?缺少输出 CSV 路径}"
DURATION="${3:?缺少采集时长（秒）}"
INTERVAL="${4:-1}"

mkdir -p "$(dirname "$OUT")"
{
  echo "elapsed_s,rss_kb,heap_used_kb,heap_total_kb"
} > "$OUT"

START="$(date +%s)"
END=$((START + DURATION))
while :; do
  NOW="$(date +%s)"
  ELAPSED=$((NOW - START))
  if [ "$ELAPSED" -gt "$DURATION" ]; then break; fi

  if [ ! -r "/proc/$PID/status" ]; then
    echo "$ELAPSED,0,0,0" >> "$OUT"
    break
  fi
  RSS_KB="$(awk '/^VmRSS:/{print $2}' "/proc/$PID/status" 2>/dev/null || echo 0)"

  HEAP_USED_KB=0
  HEAP_TOTAL_KB=0
  if command -v jcmd >/dev/null 2>&1; then
    HEAP_INFO="$(jcmd "$PID" GC.heap_info 2>/dev/null | tr '\n' ' ' || true)"
    USED="$(printf '%s' "$HEAP_INFO" | grep -oE 'used [0-9]+K' | head -1 | grep -oE '[0-9]+' || true)"
    TOTAL="$(printf '%s' "$HEAP_INFO" | grep -oE 'total [0-9]+K' | head -1 | grep -oE '[0-9]+' || true)"
    HEAP_USED_KB="${USED:-0}"
    HEAP_TOTAL_KB="${TOTAL:-0}"
  fi
  echo "$ELAPSED,$RSS_KB,$HEAP_USED_KB,$HEAP_TOTAL_KB" >> "$OUT"
  sleep "$INTERVAL" || break
done
