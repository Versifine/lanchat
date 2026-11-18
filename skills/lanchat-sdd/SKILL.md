# LanChat 开发规约（Skill）

> 课程要求："把要做什么写成可验收的规格，即测试与评测集，形成 skill（markdown 文件）。"
> 本文件既是 AI agent / 开发者的工作手册，也是仓库的验收流程定义。任何 agent 开工前先读 `docs/SPEC.md`、`docs/api.md`、`docs/eval-plan.md` 和本文件。

## 1. 适用与前置

- 在 `/home/versi/Projects/Java` 仓库内工作；monorepo：`backend/`（Spring Boot）、`frontend/`（Vue 3）。
- 环境已就绪：JDK 21 与 Maven 3.9.16 位于 `~/.local/opt`，用 `source scripts/env.sh` 注入；Node 24 + npm 11 全局可用。
- 只允许改自己任务范围内的文件；跨范围改动先报 Lead。**不要执行 git 命令**（提交与发布由 Lead 统一做）。

## 2. 常用命令

```bash
source scripts/env.sh                 # JAVA_HOME / Maven
mvn -f backend/pom.xml test           # 后端单测 + 集成测试
mvn -f backend/pom.xml package        # 打可执行 jar（含 Swagger UI 等）
npm --prefix frontend install         # 安装前端依赖
npm --prefix frontend run build       # 前端构建（产物 frontend/dist）
npm --prefix frontend run dev         # 前端开发服务器（:5173，代理 /api /ws 到 :8081）
scripts/dev.sh                        # 一条命令同时起后端 + 前端（开发）
scripts/build.sh                      # 前端构建 + 拷 dist 到后端 static + 后端打包
scripts/run.sh                        # 生产形态一键启动（后端托管前端）
```

## 3. 契约规则（最重要）

1. `docs/api.md` 是前后端唯一契约。**先改契约再改代码**；两端实现必须与契约逐字段一致（字段名、类型、可空性、时间戳毫秒）。
2. 业务错误码、时间戳格式、认证头、WS 消息类型不得私自新增；新增须同时更新 `docs/api.md` 与 `docs/SPEC.md` 变更记录。
3. 服务端永远从 JWT 取当前用户身份，禁止信任前端传来的发送者字段。

## 4. 后端分层规则（整洁架构启发，单模块包分层）

```
com.lanchat
├── domain/
│   ├── model/      实体与值对象（record/普通类）
│   └── port/       仓储与外部能力接口（Repository、PasswordHasher、TokenIssuer、FileStorage、RealtimeNotifier）
├── application/
│   ├── dto/        用例输入/输出（record）
│   └── service/    用例服务（AuthService/ChatService/UserService/FileService）
├── adapter/
│   ├── in/web/     REST Controller + 请求/响应 DTO + 全局异常处理
│   ├── in/ws/      WebSocket Handler（只做协议转换，调用 application）
│   └── out/        SQLite 仓储实现（JdbcTemplate）、本地文件存储、WebSocket 通知实现
└── infrastructure/ Spring 配置、JWT/PBKDF2 实现、数据源与 schema、CORS/静态资源/限流
```

- 依赖方向：`adapter/infrastructure → application → domain`。`domain` 不得 import Spring/JDBC。
- Controller 不写业务逻辑；Service 不依赖 HttpServletRequest/Session；Repository 接口在 domain、实现在 adapter/out。
- SQL 一律 PreparedStatement 参数化；禁止字符串拼接 SQL。
- 所有外部输入在 adapter 层校验一次；业务约束在 application 层再校验。
- 资源一律 try-with-resources；线程池/连接池在 infrastructure 统一配置并有命名。

## 5. 前端规则

- Vue 3 `<script setup>` + Element Plus + Pinia + Axios；纯 JavaScript（不引入 TypeScript）。
- 所有请求走 `src/api/http.js`（统一带 token、统一 401 跳登录、统一错误提示）。
- WebSocket 封装在 `src/api/ws.js`：自动重连（指数退避 1/2/4/8/16/30s）、10s 心跳、按 type 分发。
- 消息一律按文本渲染，禁止 `v-html`（XSS）；文件大小/进度用 Element Plus 组件展示。
- 构建必须通过 `npm run build`；不引入重量级额外依赖（新增依赖需说明理由）。

## 6. 测试要求（DoD）

- 新增/修改业务逻辑必须补 JUnit 测试，保持 `mvn test` 全绿。
- 改动 REST/WS 行为必须同步 `docs/eval-plan.md` 并补对应用例。
- 安全相关改动（认证、上传、SQL、限流）必须有对应用例（EV-SEC-*）。
- 完成定义（DoD）：功能实现 + 契约同步 + 测试通过 + 无编译警告级问题 + 日志合理 + 错误处理完整。

## 7. 高风险动作（必须人工审批）

以下动作不得擅自执行，需 Lead 向用户确认并记录到 `docs/ai-devlog.md`：
1. 推送到远程仓库 / 创建公开仓库 / 发布 Release；
2. 删除或重命名数据库文件、修改既有表结构（破坏性变更）；
3. 修改 `docs/api.md` 的破坏性变更（删字段/改语义）；
4. 引入新的重大第三方依赖或修改构建/CI 配置；
5. 任何会消耗用户资源（大额下载、长时间压测超过 30 分钟）的操作。

## 8. 常见错误清单

- ❌ 前端字段名与 `docs/api.md` 不一致（如 `from` vs `sender`）→ 联调失败。
- ❌ 服务端信任请求体里的 sender/from → 越权伪造。
- ❌ SQLite 用长事务/忘记 WAL → 并发写锁。
- ❌ WS 在消息线程里做阻塞 IO/数据库重活 → 延迟飙高。
- ❌ 上传文件名直接拼路径 → 目录穿越。
- ❌ 前端用 v-html 渲染消息 → XSS。
- ❌ 忘记关闭 SSH/文件流/连接 → 句柄泄漏。
