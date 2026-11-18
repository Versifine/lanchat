#!/usr/bin/env bash
# 压测入口：运行 JDK HttpClient 压测客户端（com.lanchat.bench.BenchMain）
# 用法示例：
#   scripts/bench.sh --url ws://localhost:8080/ws/chat --connections 500 --duration 60
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/scripts/env.sh"
cd "$ROOT"

mvn -q -f backend/pom.xml -DskipTests package
exec java -cp backend/target/lanchat-backend.jar com.lanchat.bench.BenchMain "$@"
