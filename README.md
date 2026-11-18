# LanChat —— 局域网聊天室

《Java语言及网络编程》2026–2027 课程报告项目。前后端分离的 B/S 聊天应用：

- **后端**：Java 21 + Spring Boot 3.5，整洁架构启发（domain / application / adapter / infrastructure），SQLite 持久化，JWT 认证，REST + WebSocket。
- **前端**：Vue 3 + Vite + Element Plus + Pinia，登录/注册、三栏聊天界面、私聊、群聊、文件上传进度、断线重连、暗色模式。
- **文档**：SDD 规格、API 契约、评测集、AI 开发日志（见 `docs/`）。

## 功能

- 注册 / 登录（JWT + PBKDF2 密码哈希）
- 在线用户列表与状态实时推送
- 群聊、私聊、离线消息补发、最近 50 条历史
- 文件上传/下载（multipart，≤20MB，SHA-256 校验，WS 通知接收方）
- 心跳检测、断线自动重连
- Swagger UI 接口文档（`/swagger-ui.html`）

## 快速开始

环境要求：JDK 21、Maven 3.9+、Node 20+（本机已把 JDK/Maven 装在 `~/.local/opt`，`scripts/env.sh` 会自动注入）。

```bash
# 开发模式：后端 :8081 + 前端 Vite :5173（自动代理 /api /ws）
scripts/dev.sh

# 生产形态（老师验收推荐）：一条命令构建前端并打包后端，启动单服务
scripts/build.sh
scripts/run.sh          # 打开 http://localhost:8081
```

也可以分别启动：

```bash
source scripts/env.sh
mvn -f backend/pom.xml spring-boot:run        # 后端
npm --prefix frontend install && npm --prefix frontend run dev   # 前端
```

默认端口 8081（8080 已被本机其他服务占用），SQLite 数据在 `data/lanchat.db`，上传文件在 `data/uploads/`。

## 测试与压测

```bash
source scripts/env.sh
mvn -f backend/pom.xml test     # 单元 + 集成测试
npm --prefix frontend run build # 前端构建校验
scripts/bench.sh                # 压测（连接数/延迟/吞吐/文件），数据输出到 assets/bench/
```

## 目录结构

```
backend/                 Spring Boot 后端（Maven）
  src/main/java/com/lanchat/{domain,application,adapter,infrastructure}
frontend/                Vue 3 前端（Vite）
docs/                    SPEC.md / api.md / eval-plan.md / ui-design.md / ai-devlog.md
skills/lanchat-sdd/      AI 工程化 skill（开发规约 + 验收流程）
scripts/                 env / dev / build / run / bench
report/                  课程报告（Markdown 母本 + docx 生成脚本）
assets/                  架构图、压测原始数据、界面截图
```

## 文档索引

| 文档 | 说明 |
|---|---|
| [docs/SPEC.md](docs/SPEC.md) | SDD 规格：需求、方案论证、设计、验收标准 |
| [docs/api.md](docs/api.md) | 前后端唯一契约：REST + WebSocket + 错误码 |
| [docs/eval-plan.md](docs/eval-plan.md) | 可执行的验收评测集 |
| [docs/ui-design.md](docs/ui-design.md) | 前端视觉与交互规范 |
| [docs/ai-devlog.md](docs/ai-devlog.md) | AI 工程化过程记录（含人工审批） |
| [skills/lanchat-sdd/SKILL.md](skills/lanchat-sdd/SKILL.md) | 开发规约与验收流程 |

## 测试与性能（本机实测，详见 assets/bench/README.md）

- 自动化测试：**160 个 JUnit 用例全部通过**（单元 / 仓储 / REST 集成 / WebSocket 集成 / 安全用例）。
- 500 并发 WebSocket 连接、每连接 1 msg/s：连接成功率 100%，端到端延迟 **P50 10.1 ms / P95 21.4 ms / P99 31.2 ms**，100% 投递、0 错误（`-Xmx512m`，heap ≈171–218 MB）。
- 广播吞吐：**1066.75 msg/s**（500 连接 × 2.3 msg/s；该运行点延迟升高，说明吞吐与延迟不在同一运行点）。
- 3 秒轮询 vs WebSocket：P95 延迟 3006 ms vs 327 ms，请求数 18 次/分钟 vs 0，接收字节约为轮询的 1/34。
- 20 MB 文件 multipart 上传：平均 **160.21 MB/s**，SHA-256 全部一致；21 MB 触发 413/1006。
- 已知瓶颈：建连时全量 `userList` 广播存在 O(N²) 风暴（并发建连场景），广播为 O(N) 扇出；详见压测 README 的"瓶颈观察与建议"。

## AI 工程化说明

本项目按课程建议的 SDD 流程开发：需求 → 规格（SPEC）→ 可验收评测集 → AI agent 产出代码 → 人工审批高风险动作 → 自动化验证。过程记录见 `docs/ai-devlog.md`，发布地址见仓库 About。

## 许可证

课程作业项目，仅用于学习与教学。
