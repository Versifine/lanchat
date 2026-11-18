#!/usr/bin/env bash
# 临时压测后端实例管理（独立端口 + /tmp 临时库，不影响 8081 正式实例）。
#
# 用法：
#   tools/bench/temp_backend.sh start [port] [heap]   # 默认 8082 -Xmx512m
#   tools/bench/temp_backend.sh stop  [port]
#   tools/bench/temp_backend.sh pid   [port]
#
# 说明：数据目录固定 /tmp/qa-bench（每次 start 前清空），日志 /tmp/qa-bench/lanchat.log。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
source "$ROOT/scripts/env.sh"
cd "$ROOT"

ACTION="${1:-start}"
PORT="${2:-8082}"
HEAP="${3:--Xmx512m}"
DIR="/tmp/qa-bench"
PIDFILE="$DIR/pid.txt"

case "$ACTION" in
  start)
    if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
      echo "临时实例已在运行: pid=$(cat "$PIDFILE") port=$PORT"
      exit 0
    fi
    rm -rf "$DIR"
    mkdir -p "$DIR"
    (
      nohup java "$HEAP" -jar backend/target/lanchat-backend.jar \
        --server.port="$PORT" \
        --spring.datasource.url="jdbc:sqlite:$DIR/lanchat.db?journal_mode=WAL&busy_timeout=5000&foreign_keys=on" \
        --lanchat.upload.dir="$DIR/uploads" \
        --logging.file.name="$DIR/lanchat.log" \
        > "$DIR/console.log" 2>&1 &
      echo $! > "$PIDFILE"
    )
    PID="$(cat "$PIDFILE")"
    for _ in $(seq 1 60); do
      if curl -s -o /dev/null "http://localhost:$PORT/api/users" 2>/dev/null; then
        echo "临时实例启动完成: pid=$PID port=$PORT heap=$HEAP db=$DIR/lanchat.db"
        exit 0
      fi
      sleep 1
    done
    echo "临时实例启动失败，见 $DIR/console.log" >&2
    exit 1
    ;;
  stop)
    if [ -f "$PIDFILE" ]; then
      PID="$(cat "$PIDFILE")"
      kill "$PID" 2>/dev/null || true
      rm -f "$PIDFILE"
      echo "已停止临时实例: pid=$PID"
    else
      echo "没有 pid 文件，临时实例未运行"
    fi
    ;;
  pid)
    cat "$PIDFILE" 2>/dev/null || echo ""
    ;;
  *)
    echo "用法: temp_backend.sh {start|stop|pid} [port] [heap]" >&2
    exit 2
    ;;
esac
