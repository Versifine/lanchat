# LanChat AI 开发日志

> 本文件记录 AI 工程化开发过程：需求变更、规格迭代、agent 分工、人工审批与验收结果。
> 课程要求"高风险动作要人工审批，如果不正确，回到需求，重新定义边界"，这里的记录就是证据。

## 2026-11-18

### 09:xx 需求澄清（人工）

- 用户提供课程报告说明，明确选题自由、两条路线（传统工程 / AI 工程化）、报告要求。
- 讨论后确定路线：**传统软件工程 + AI 工程化（SDD）结合**；报告需写开发环境与软件开发流程。

### 10:00–10:30 方案迭代（人工审批：否决 RPC 框架方案）

- 初版方案：轻量级 RPC 框架（BIO/NIO/虚拟线程对比）。用户反馈"老师看不懂这些"，要求做**老师看得懂的小应用**。
- 决策：改为局域网聊天室。
- 记录：这属于课程要求的"方案不正确 → 回到需求、重新定义边界"的真实案例。

### 10:40–10:50 架构迭代（人工审批：改为前后端分离）

- 初版：Swing 桌面客户端 + 自写 TCP 协议 + BIO 多线程。
- 用户要求：monorepo、前后端分离、前端漂亮组件库、后端简单写、后端整洁架构启发、数据库 SQLite。
- 决策（v2）：
  - 前端 Vue 3 + Vite + Element Plus + Pinia + Axios（纯 JS）
  - 后端 Spring Boot 3.5.16 + Java 21，单 Maven 模块 + 包分层，domain/application/adapter/infrastructure
  - SQLite（sqlite-jdbc + JdbcTemplate + SQL），WAL 模式
  - REST + WebSocket（JWT 认证）；文件 HTTP multipart 上传 + SHA-256
  - 生产形态：前端构建产物由后端托管，一键单服务
- 清理 v0.1 的 Swing 脚手架（未提交的中间产物），重写 SPEC/契约。

### 10:49 环境准备（自动，无需审批）

- 本地安装 Temurin JDK 21.0.13 与 Maven 3.9.16 到 `~/.local/opt`（无 root 权限，用户目录安装）。
- 核实：Node 24.19 / npm 11.17 / pnpm 11.22 可用；npm 官方源与 npmmirror 可达；Spring Boot 3.5.16、springdoc 2.10.0、jjwt 0.12.7、sqlite-jdbc 3.54.1.0 可拉取。
- 版本选择理由：JDK 21 LTS（生态稳定、课程通用）、Spring Boot 3.5.x（Java 21 兼容）、Element Plus 2.14（中文文档、组件全）。

### 10:55 SDD 材料（AI 产出，待人工审阅）

- 产出 `docs/SPEC.md`（v1.0）、`docs/api.md`（契约 v1）、`docs/eval-plan.md`（评测集）、`docs/ui-design.md`、`skills/lanchat-sdd/SKILL.md`。
- 下一步：Lead 分配共享任务，后端/前端 agent 并行实现，测试 agent 编写评测集与压测工具。

### 11:00–11:20 后端 / 前端实现完成与联调（AI 产出，Lead 验收）

- **backend-dev**（task-1，已 complete）：完成 72 个 Java 文件（约 3700 行），严格按整洁架构分层：
  - domain 零 Spring/JDBC 依赖；application 纯 Java 用例服务；adapter/in 为 REST Controller + WS Handler + JWT 过滤器 + 全局异常；adapter/out 为 JdbcTemplate 仓储 + 本地文件存储 + WS 通知；infrastructure 为 JWT/PBKDF2/限流/心跳/会话表/环境配置。
  - 自测证据：REST 49/49、WebSocket 45/45（含离线补发不重复、重复登录踢下线、伪造 sender 忽略、超长 1008、限流 1009、21MB→413）、优雅关闭与重启持久化通过；`mvn package` BUILD SUCCESS（jar 40.9MB）。
- **frontend-dev**（task-2，已 complete）：完成 18 个源文件（约 3300 行），纯 JS 无新增依赖：
  - 登录/注册页、三栏聊天主界面、在线列表与未读红点、气泡消息、文件上传进度与文件卡片、WS 指数退避重连与心跳、暗色模式、XSS 纯文本渲染。
  - 自测证据：`npm run build` 成功；真实后端契约联调 17/17；真实浏览器 E2E 11/11（无 mock）；mock 全场景 23/23；修复登录按钮双提交 bug；20 张截图存入 `assets/screenshots/`。
- **Lead 独立验收**：启动干净实例（仓库根启动、全新 data/lanchat.db），运行 `/tmp/verify_lanchat.mjs`：注册/登录/错误码/userList/群聊/私聊/心跳/历史/文件上传+WS 通知+SHA-256 下载一致/超长消息/SQL 注入 —— **22/22 通过**。
- **端口冲突决策**（Lead）：8080 被本机 WeKnora-lite 占用且不可动，LanChat 统一改用 8081；已同步 application.yml、docs/api.md、README、scripts、skills、vite 代理。
- **启动目录规范**：统一从仓库根启动（`scripts/dev.sh` / `scripts/run.sh`），运行时数据固定落在仓库根 `data/`（gitignored）；避免切换 cwd 造成"换库丢数据"错觉。
- **契约补充**（docs/api.md 实现说明）：GET /api/files/{id} 直接返回 file 对象；私聊消息同时推送给发送者与接收者；超长/限流只回 error 帧不断连；下载兼容 ?token=。
- **Git 提交**：`60b213f feat: 完成 LanChat 前后端实现...（120 files, +9173）`。

## 人工审批记录

| 日期 | 动作 | 审批人 | 结果 |
|---|---|---|---|
| 2026-11-18 | 否决 RPC 框架选题，改为小应用 | 用户 | ✅ 改为局域网聊天室 |
| 2026-11-18 | 改为前后端分离 + SQLite + 整洁架构 | 用户 | ✅ 采纳 v2 架构 |
| 待定 | 创建 GitHub 公开仓库并推送代码 | 用户 | ⏳ 待审批 |
| 待定 | 生成并提交最终报告 docx / zip | 用户 | ⏳ 待审批 |

## 待补充

- 每个开发节点的 agent 产出、测试结果、压测数据。
- 至少一次"验收不通过 → 回需求重定义边界"的迭代记录。

## 2026-11-18 11:20–11:35 测试阶段与缺陷修复（QA + 后端 agent 协作）

- **qa-dev**（task-3 进行中）：完成 25 个测试类、158 个 JUnit 用例（单元/仓储/REST 集成/WS 集成/安全用例），首轮全绿；随后编写压测工具 `tools/bench/BenchMain.java`（JDK HttpClient/WebSocket，连接数/延迟/吞吐/资源）与 `PollingVsWsMain.java`（轮询 vs WebSocket 对比），产出 `assets/bench/*.csv`。
- **QA 发现 2 个真实缺陷并报告**（未自行改主代码）：
  - BUG-1：`FileNameCleaner` 的扩展名正则误写成精确 `{12}`，导致 `.pdf/.txt/.sh` 等常规扩展名全部丢失，落盘文件无扩展名；
  - BUG-2：sqlite-jdbc 唯一约束异常 SQLState=null、errorCode=19，Spring 无法翻译为 `DuplicateKeyException`，并发注册同一用户名时输的一方返回 500/1012 而非 409/1002。
- **backend-dev 修复**（只改 main 代码）：BUG-1 改为 `{1,12}` 上界并复用同一扩展名规则；BUG-2 新增 `DataAccessException` 兜底识别（errorCode=19 / SQLState 23xxx / UNIQUE 关键字）→ `BusinessException(1002)`。
- **验证**：隔离实例 21/21（落盘 UUID+.pdf/.gz/.sh、超长扩展名丢弃、路径穿越仍被清洗；20 线程并发注册 1×200 + 19×409 + 0×500）；`mvn test` 157/158，唯一失败是 QA 按旧行为写的断言（已交 QA 更新为 1002），属预期。
- **过程价值**：这是课程要求的"测试发现问题 → 修复 → 回归"的真实闭环，报告"系统验证"与"AI 工程化实践"章节引用。
