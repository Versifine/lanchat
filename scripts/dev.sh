#!/usr/bin/env bash
# 开发模式：同时起后端 (:8081) 与前端 Vite dev server (:5173)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/scripts/env.sh"
cd "$ROOT"

cleanup() {
  echo -e "\n[dev] 正在停止…"
  kill "${BACK_PID:-}" "${FRONT_PID:-}" 2>/dev/null || true
  wait 2>/dev/null || true
}
trap cleanup INT TERM EXIT

echo "[dev] 启动后端 Spring Boot (:8081)…"
mvn -q -f backend/pom.xml spring-boot:run &
BACK_PID=$!

if [ ! -d frontend/node_modules ]; then
  echo "[dev] 首次运行，安装前端依赖…"
  npm --prefix frontend install
fi

echo "[dev] 启动前端 Vite (:5173)…"
npm --prefix frontend run dev &
FRONT_PID=$!

wait
