#!/usr/bin/env bash
# 一次性在 VS Code 打开录屏演示用到的代码文件（按"一条消息的路径"顺序）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

code -n \
  frontend/src/api/ws.js \
  backend/src/main/java/com/lanchat/adapter/in/ws/ChatWebSocketHandler.java \
  backend/src/main/java/com/lanchat/application/service/ChatService.java \
  backend/src/main/java/com/lanchat/adapter/out/persistence/JdbcMessageRepository.java \
  backend/src/main/java/com/lanchat/adapter/out/realtime/WebSocketRealtimeNotifier.java \
  backend/src/main/java/com/lanchat/infrastructure/websocket/SessionRegistry.java \
  backend/src/main/java/com/lanchat/application/service/AuthService.java \
  backend/src/test/java/com/lanchat/integration/WebSocketIntegrationTest.java

echo "已按消息路径打开 8 个演示文件（前 6 个主选，后 2 个加讲；Ctrl+P 按文件名切换）"
