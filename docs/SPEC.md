# LanChat 规格说明书（SDD）

| 项 | 内容 |
|---|---|
| 项目 | LanChat 局域网聊天室 |
| 课程 | 《Java语言及网络编程》2026–2027 课程报告 |
| 规格版本 | v1.0（2026-11-18） |
| 架构 | monorepo + 前后端分离 + 后端整洁架构启发（单模块包分层）+ SQLite |
| 技术栈 | 后端 Spring Boot 3.5.16 / Java 21 / JdbcTemplate + SQLite / JWT；前端 Vue 3 + Vite + Element Plus + Pinia + Axios |
| 开发方式 | 传统软件工程 + AI 工程化（SDD：规格→评测集→agent 实现→人工审批→验证） |
| AI 工具 | DeepSeek Harness（deepseek-flash agent；Skill、共享任务板、人工审批） |
| 契约 | [`docs/api.md`](api.md) 为前后端唯一共享契约 |

## 1. 意图（Intent）

让同一局域网内的用户打开浏览器即可注册、登录、群聊、私聊、互传文件；服务端是一个可独立运行的 Spring Boot 服务，数据落 SQLite，重启不丢。
**成功标准**：两个浏览器客户端在同一服务上完成「注册→登录→群聊→私聊→传 20MB 文件→重登看历史」全流程；500 并发 WebSocket 连接压测达标；报告可复现。

## 2. 范围

**范围内**：账号体系、在线状态、群聊/私聊、离线消息、历史消息、文件上传/下载、心跳与自动重连、WebSocket 实时推送、Swagger 接口文档、单元/集成/安全/性能测试。
**范围外**：音视频、群组管理（建群/踢人）、多服务器集群、移动端 App、TLS 实际部署（仅设计说明）、消息撤回/已读回执（未来工作）。

## 3. 需求分析

### 3.1 功能需求（FR）

| 编号 | 名称 | 描述 | 验收标准 |
|---|---|---|---|
| FR-01 | 注册 | 用户名 3–20 位（字母/数字/下划线）、密码 6–32 位、昵称 ≤20；用户名唯一 | 注册成功可登录；重名返回 409 |
| FR-02 | 登录 | 用户名+密码换 JWT（HS256，24h） | 正确返回 token；错误返回 401 |
| FR-03 | 在线状态 | 登录后建立 WS，服务端维护在线表；上线/下线/列表变化实时推送 | 两个客户端互见在线状态 |
| FR-04 | 群聊 | 消息广播给所有在线用户并落库 | 多客户端同一消息、顺序一致 |
| FR-05 | 私聊 | 发给指定用户；在线实时推送，离线入库待补发 | 双方可见；离线用户上线后收到 |
| FR-06 | 历史消息 | 群聊/私聊各拉取最近 50 条 | 刷新页面/重登后历史一致 |
| FR-07 | 文件传输 | multipart 上传 ≤20MB，SHA-256 校验，落盘并入库；接收方收到 WS 通知，可下载 | 20MB 文件上传成功且校验一致；进度条正常 |
| FR-08 | 心跳重连 | 客户端每 10s ping；服务端 35s 无消息判离线；前端断线自动重连（指数退避） | kill 服务端后前端自动恢复 |
| FR-09 | 服务端运行 | 配置端口/数据目录/上传目录；优雅关闭；启动日志 | 配置文件生效、Ctrl+C 正常退出 |
| FR-10 | 界面 | 登录/注册页 + 三栏聊天主界面 + 未读提醒 + 文件进度 + 系统消息 + 暗色模式 | 浏览器截图验收 |

### 3.2 非功能需求（NFR）

| 编号 | 指标 | 目标 | 测量方法 |
|---|---|---|---|
| NFR-01 | WebSocket 并发连接 | 500 连接稳定 ≥10 分钟 | JDK HttpClient 压测客户端 |
| NFR-02 | 消息端到端延迟 | P95 ≤ 200ms，P99 ≤ 500ms（500 连接） | 压测客户端打点 |
| NFR-03 | 消息吞吐 | ≥ 1000 msg/s | 压测客户端统计 |
| NFR-04 | 文件上传吞吐 | ≥ 20 MB/s（本机环回） | curl/压测脚本计时 |
| NFR-05 | REST 响应 | 常规接口 P95 ≤ 100ms | 压测脚本 |
| NFR-06 | 内存 | 500 连接下后端堆 ≤ 512MB | `-Xmx512m` 下运行 |
| NFR-07 | 持久化 | 重启后用户/消息/文件元数据不丢 | 重启验收 |
| NFR-08 | 前端体验 | 文件上传时界面可交互，在线列表 500 人滚动不卡 | 人工/浏览器验收 |

### 3.3 安全需求（SEC）

| 编号 | 要求 |
|---|---|
| SEC-01 | 密码用 PBKDF2WithHmacSHA256（12 万次迭代、16 字节随机盐）存储，禁止明文/MD5/SHA1 |
| SEC-02 | JWT HS256，密钥 ≥32 字节且可配置；校验签名、过期时间；生产必须换掉默认密钥 |
| SEC-03 | 未认证 REST 请求返回 401；WS 握手 token 无效直接拒绝 |
| SEC-04 | 发送者身份只从 JWT 解析，前端传入的 from/sender 一律忽略 |
| SEC-05 | 输入边界：消息 ≤2000 字符、文件 ≤20MB、文件名清洗（去路径/非法字符/`..`）、上传目录固定 |
| SEC-06 | XSS：消息按纯文本渲染（前端禁用 v-html），后端只返回 JSON 文本 |
| SEC-07 | SQL 注入：全部使用 PreparedStatement 参数化，禁止拼接 SQL |
| SEC-08 | 限流：登录失败 5 次/分钟锁定该用户名 1 分钟；单用户消息 ≤20 条/秒 |
| SEC-09 | 日志不打印明文密码、完整 token |
| SEC-10 | 部署建议 HTTPS/WSS（报告说明，课程演示不启用） |

## 4. 方案论证

| 决策点 | 备选 | 选择与理由 |
|---|---|---|
| 客户端形态 | C/S 桌面 vs B/S 浏览器 | **B/S**：零安装、老师直接验收、界面可做漂亮；C/S 才能共享 Java 代码，但需安装客户端 |
| 架构风格 | 单体（服务端渲染） vs 前后端分离 | **前后端分离**：前端独立开发部署、接口可复用；代价是需维护契约（docs/api.md），这是设计决策 |
| 实时方案 | 3s 轮询 / 长轮询 / SSE / WebSocket | **WebSocket**：双向、低延迟、服务端主动推送；轮询作为对比实验（报告数据） |
| 数据库 | MySQL / H2 / SQLite | **SQLite**：零安装、单文件随压缩包提交；写并发有限，用 WAL+短事务+写锁讨论，规模匹配 |
| 持久化方式 | JPA/Hibernate vs JdbcTemplate+SQL | **JdbcTemplate+SQL**：透明、SQL 可读、契合整洁架构的仓储实现 |
| 认证 | Session vs JWT | **JWT**：前后端分离下无状态、跨域简单 |
| 后端分层 | 传统三层 vs 整洁架构启发 | **整洁架构**：domain/application/adapter/infrastructure 依赖向内；业务不依赖框架，可测试 |
| 模块划分 | 单 Maven 模块 vs 多模块 | **单模块包分层**：规模小、简单；多模块留作未来工作 |
| 前端栈 | Vue+Element Plus / React+AntD / Naive UI | **Vue 3 + Element Plus**：中文文档、组件全、上手快 |
| 文件传输 | multipart 整文件 vs 分片断点续传 | **multipart + SHA-256**：简单可靠；分片留作未来工作 |
| 部署形态 | 前后端分别启动 vs 一键单服务 | **一键单服务**：前端构建产物由后端托管，老师一条命令验收 |

## 5. 系统设计

### 5.1 总体架构

```
浏览器（Vue 3 + Element Plus）
   │  REST /api/**（HTTP+JSON+JWT）        WebSocket /ws/chat?token=
   ▼
Spring Boot（backend/，单模块）
   adapter/in  ──  AuthController / ChatController / FileController / ChatWebSocketHandler
        │ 调用
   application ──  AuthService / ChatService / UserService / FileService
        │ 依赖端口接口
   domain      ──  User / Message / ChatFile + UserRepository / MessageRepository / FileRepository / PasswordHasher / TokenIssuer / FileStorage / RealtimeNotifier
        ▲ 实现
   adapter/out ──  JdbcUserRepository / JdbcMessageRepository / JdbcFileRepository / LocalFileStorage / WebSocketNotifier
   infrastructure ──  Spring 配置、JWT/PBKDF2 实现、SQLite 数据源、CORS、全局异常处理
```

依赖规则：`infrastructure / adapter → application → domain`；domain 不出现 Spring/JDBC 注解。

### 5.2 数据模型（SQLite）

| 表 | 字段 | 说明 |
|---|---|---|
| users | id, username(unique), nickname, password_hash, salt, created_at | 密码哈希 + 盐 |
| messages | id, sender_id, receiver_id(null=群聊), content, created_at, delivered | delivered 用于私聊离线补发 |
| files | id, uploader_id, original_name, stored_name, size, sha256, created_at | 文件元数据 |

索引：`users.username` 唯一索引；`messages(created_at)`、`messages(receiver_id, delivered)`、`files(uploader_id)`。
SQLite 配置：WAL 模式、`busy_timeout=5000`、外键开启；连接池上限 4。

### 5.3 关键流程

1. **登录**：POST /api/auth/login → 校验 PBKDF2 → 签发 JWT → 前端建 WS（token 查询参数）→ 服务端注册在线 → 推送全量 userList → 补发该用户未投递私聊。
2. **群聊/私聊**：WS 收 `{type:"chat", to?, content}` → ChatService 校验 → 落库 → 广播/单播 → 客户端渲染。
3. **文件**：前端 multipart 上传 → 服务端校验大小/文件名 → 流式落盘 + SHA-256 → 入库 → 若有接收方则 WS 推 `{type:"file"}` 通知 → 接收方下载 `/api/files/{id}/download`。
4. **心跳/重连**：前端 10s ping，服务端回 pong；服务端 35s 无消息断开；前端指数退避重连并用原 token 重新握手（token 过期则回登录页）。
5. **优雅关闭**：`@PreDestroy` 关闭 WS 会话、关闭连接池。

### 5.4 契约与文档

- REST/WS 契约见 [`docs/api.md`](api.md)，改动必须同步更新本文件"变更记录"。
- Swagger UI：`/swagger-ui.html`（springdoc 自动生成）。

## 6. 验收规格（映射评测集）

功能：FR-01～FR-10 → `EV-FUNC-*`；性能：NFR-01～08 → `EV-PERF-*`；安全：SEC-01～10 → `EV-SEC-*`。用例明细见 [`docs/eval-plan.md`](eval-plan.md)。
**门禁**：P0 用例 100% 通过；NFR-02/03/04 达标；SEC-01～09 全部通过；`mvn test` 与 `npm run build` 无错误。

## 7. 里程碑（按 4 周排，截止日期待用户确认）

| 里程碑 | 内容 | 目标日期 |
|---|---|---|
| M0 | 规格、契约、评测集、脚手架、环境 | 2026-11-18 |
| M1 | 后端核心（domain/application/适配器/SQLite/JWT） | 2026-11-20 |
| M2 | 前端界面（登录/聊天/文件/重连） | 2026-11-22 |
| M3 | 联调 + 功能/安全测试 + 修复 | 2026-11-24 |
| M4 | 压测与数据（并发/延迟/吞吐/轮询对比） | 2026-11-25 |
| M5 | 报告撰写与排版 | 2026-11-28 |
| M6 | 打包、GitHub 发布、最终验收 | 2026-11-30 |

## 8. 风险与边界

| 风险 | 影响 | 应对 |
|---|---|---|
| SQLite 写并发（写锁） | 高并发下发消息变慢 | WAL + 短事务 + busy_timeout；压测如实报告瓶颈 |
| WebSocket 连接内存 | 500 连接内存超限 | `-Xmx512m` 实测 + 调 JVM 参数 |
| 压测机与演示机差异 | 数据不代表老师环境 | 报告写清环境（16 核/30G/Fedora）与复现脚本 |
| token 过期/重连失败 | 用户体验中断 | 前端捕获 4401 回登录页 |
| 时间不足 | 报告质量 | 范围外功能明确砍掉（见第 2 节） |

## 9. 非目标 / 未来工作

多群组、消息撤回、已读回执、分片断点续传、TLS 与 Nginx 部署、多节点集群、移动端。

## 10. 变更记录

| 版本 | 日期 | 变更 | 原因 |
|---|---|---|---|
| v0.1 | 2026-11-18 | 初版：Java C/S（Swing 客户端 + 自写 TCP 协议 + BIO 多线程） | 课程选题讨论 |
| v0.2 | 2026-11-18 | 改为 B/S 前后端分离（Vue 3 + Element Plus / Spring Boot / SQLite / WebSocket），后端整洁架构启发 | 教师验收视角：界面可见、实现简单；原 RPC/自写协议方案对教师不可验收 |
