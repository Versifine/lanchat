#!/usr/bin/env bash
# 一次性在 VS Code 打开录屏演示用到的全部代码文件（路径相对仓库根）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

code -n \
  backend/src/main/java/com/lanchat/adapter/in/ws/ChatWebSocketHandler.java \
  backend/src/main/java/com/lanchat/application/service/ChatService.java \
  backend/src/main/java/com/lanchat/adapter/out/persistence/JdbcMessageRepository.java \
  backend/src/main/java/com/lanchat/application/service/AuthService.java \
  backend/src/main/java/com/lanchat/infrastructure/websocket/SessionRegistry.java \
  backend/src/test/java/com/lanchat/integration/WebSocketIntegrationTest.java \
  frontend/src/api/ws.js

echo "已在 VS Code 打开 7 个演示文件（用 Ctrl+P 按文件名切换）"
