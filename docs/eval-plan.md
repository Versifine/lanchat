# LanChat 验收评测集（Eval Plan）

> 课程要求：把"要做什么"写成可验收的规格，即测试与评测集。本文件把 `docs/SPEC.md` 的 FR/NFR/SEC 映射为可执行用例。
> 执行位置：`backend/src/test/java/com/lanchat/**`（JUnit 5）、`tools/bench/**`（压测客户端）、浏览器 E2E 人工/自动化。

## 1. 测试分层

| 层 | 工具 | 覆盖 |
|---|---|---|
| 单元测试 | JUnit 5 | domain/application 逻辑、PBKDF2、JWT、文件名清洗、参数校验 |
| 仓储测试 | JUnit 5 + 内存/临时 SQLite | 用户唯一性、消息历史、离线私聊领取、文件元数据 |
| 接口集成测试 | `@SpringBootTest` + MockMvc | REST 状态码/错误码/鉴权/校验/上传下载 |
| WebSocket 集成测试 | Spring `StandardWebSocketClient` | 握手鉴权、群聊广播、私聊、离线补发、心跳、重复登录踢下线 |
| 压测 | JDK HttpClient 自写客户端 | 并发连接、消息延迟/吞吐、轮询 vs WS、文件上传吞吐 |
| 前端 | `npm run build` + 浏览器 E2E | 登录、聊天、文件、重连、暗色模式、截图 |

## 2. 功能用例（P0 = 必须通过）

| 编号 | 场景 | 步骤 | 期望 |
|---|---|---|---|
| EV-FUNC-01 | 注册成功 | 合法用户名/密码 POST /register | 200，返回 token 与 user；库中密码为 PBKDF2 格式 |
| EV-FUNC-02 | 注册重名 | 重复用户名 | 409 / code 1002 |
| EV-FUNC-03 | 注册参数非法 | 用户名 2 位、密码 5 位 | 400 / code 1004 |
| EV-FUNC-04 | 登录成功 | 正确凭据 | 200 token 可解出 userId |
| EV-FUNC-05 | 登录失败 | 密码错误 | 401 / code 1003 |
| EV-FUNC-06 | 未认证访问 | 无 token 调 /api/users | 401 / code 1001 |
| EV-FUNC-07 | 在线列表 | 两客户端登录，查 /api/users | online 正确；WS 收到 userList 变更 |
| EV-FUNC-08 | 群聊 | A 发群聊 | A/B 均实时收到，顺序一致，库中有记录 |
| EV-FUNC-09 | 私聊（在线） | A 私聊 B | 仅 A/B 收到 |
| EV-FUNC-10 | 私聊（离线） | B 不在线时 A 私聊 B；B 登录 | B 登录后收到 offline=true 的消息；历史可查 |
| EV-FUNC-11 | 历史消息 | 重登后拉 /messages/group | 返回最近 50 条，升序 |
| EV-FUNC-12 | 文件上传下载 | 上传 1MB 文件 | 200，sha256 与本地一致；下载字节一致 |
| EV-FUNC-13 | 文件通知 | A 上传 with to=B | B 收到 WS `file` 通知，可下载 |
| EV-FUNC-14 | 心跳 | 客户端 ping | 收到 pong |
| EV-FUNC-15 | 重复登录 | 同账号再登录 | 旧连接被关闭并收到 system 提示，新连接生效 |
| EV-FUNC-16 | 服务端重启持久化 | 重启后重登 | 用户/历史消息/文件元数据仍在 |

## 3. 安全用例

| 编号 | 场景 | 期望 |
|---|---|---|
| EV-SEC-01 | 密码存储 | `users.password_hash` 为 PBKDF2 格式且不等于明文 |
| EV-SEC-02 | JWT 篡改 | 改签名/过期 token → 401 |
| EV-SEC-03 | WS 伪造 token | 握手失败，连接不建立 |
| EV-SEC-04 | 伪造发送者 | WS 消息中带 `from`/`sender` 字段被忽略，以 JWT 身份为准 |
| EV-SEC-05 | 消息超长 | 2001 字符 → WS 返回 error 1008 且不广播 |
| EV-SEC-06 | 超大文件 | 21MB 上传 → 413 / code 1006 |
| EV-SEC-07 | 路径穿越文件名 | 上传 `../../evil.sh` → 落盘名为清洗后的安全名，目录不逃逸 |
| EV-SEC-08 | SQL 注入 | 用户名 `a' OR '1'='1` 注册/登录不越权、不报 SQL 错误 |
| EV-SEC-09 | XSS 内容 | 消息内容含 `<script>` → 前端按文本显示，不执行 |
| EV-SEC-10 | 登录限流 | 连续 5 次错误后第 6 次 429 |
| EV-SEC-11 | 消息限流 | 单用户短时间 >20 条/s → 429/error 1009，服务端不崩 |
| EV-SEC-12 | 日志检查 | 日志中无明文密码、无完整 token |

## 4. 性能用例（压测客户端，记录原始 CSV 到 `assets/bench/`）

| 编号 | 场景 | 指标 | 目标 |
|---|---|---|---|
| EV-PERF-01 | 连接建立 | 100/500/1000 并发 WS 连接成功率 | 100%，稳定 ≥10 分钟 |
| EV-PERF-02 | 群聊延迟 | 500 连接、每连接 1 msg/s，P50/P95/P99 | P95 ≤ 200ms，P99 ≤ 500ms |
| EV-PERF-03 | 消息吞吐 | 500 连接广播 | ≥ 1000 msg/s |
| EV-PERF-04 | 实时方案对比 | 3s 轮询 vs WS 的端到端延迟、请求数 | WS 延迟显著更低，请求数更少 |
| EV-PERF-05 | 文件上传吞吐 | 20MB 单连接 | ≥ 20 MB/s |
| EV-PERF-06 | REST 响应 | 500 连接负载下 /api/users P95 | ≤ 100ms |
| EV-PERF-07 | 内存占用 | 500 连接、`-Xmx512m` | 稳定运行不 OOM |

## 5. 前端验收（浏览器）

| 编号 | 场景 | 期望 |
|---|---|---|
| EV-UI-01 | 登录/注册页 | 渐变背景、表单校验提示、错误提示 |
| EV-UI-02 | 聊天主界面 | 三栏布局、头像、气泡、时间分隔、系统消息 |
| EV-UI-03 | 文件上传 | 进度条、完成提示、接收方通知 |
| EV-UI-04 | 断线重连 | kill 后端后提示重连、恢复后自动拉历史 |
| EV-UI-05 | 响应式/暗色 | 1280×800 与手机宽度可用；暗色模式切换正常 |
| EV-UI-06 | 截图留档 | 关键界面截图存入 `assets/screenshots/` 供报告使用 |

## 6. 门禁与报告映射

- 合并门禁：`mvn test` 全绿 + `npm run build` 成功 + EV-FUNC/EV-SEC P0 全过。
- 报告素材：用例表、压测 CSV、图表、E2E 截图、Swagger 截图。
