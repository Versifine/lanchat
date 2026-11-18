#!/usr/bin/env bash
# 生产形态构建：前端构建 → 拷贝 dist 到后端 static → 打包后端可执行 jar
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/scripts/env.sh"
cd "$ROOT"

echo "[build] 1/4 安装前端依赖…"
if [ -f frontend/package-lock.json ]; then
  npm --prefix frontend ci
else
  npm --prefix frontend install
fi

echo "[build] 2/4 前端构建…"
npm --prefix frontend run build

echo "[build] 3/4 拷贝前端产物到后端 static…"
rm -rf backend/src/main/resources/static
mkdir -p backend/src/main/resources/static
cp -r frontend/dist/. backend/src/main/resources/static/

echo "[build] 4/4 后端打包…"
mvn -q -f backend/pom.xml clean package -DskipTests

echo "[build] 完成：backend/target/lanchat-backend.jar"
