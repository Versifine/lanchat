#!/usr/bin/env bash
# 压测入口：编译并运行 tools/bench 下的 JDK HttpClient 压测客户端
# 用法示例：
#   scripts/bench.sh --url ws://localhost:8081/ws/chat --connections 500 --duration 60
#   scripts/bench.sh --url ws://localhost:8081/ws/chat --connections 500 --rate 1
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/scripts/env.sh"
cd "$ROOT"

OUT=tools/bench/out
mkdir -p "$OUT"
# bench 客户端只依赖 JDK 标准库（HttpClient + 手写 JSON），不依赖 Spring
javac -encoding UTF-8 -d "$OUT" tools/bench/*.java
exec java -cp "$OUT" com.lanchat.bench.BenchMain "$@"
