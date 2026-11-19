# LanChat 录屏讲稿（开发过程 / AI 工程化 / 架构 / 代码为主）

> 用途：在本机录一段项目介绍视频发给课程老师，**不需要上台**。
> 默认时长约 **6:45**（内容比 5 分钟版充实：开场项目介绍、Shell 一条命令拉起服务、完整功能演示、开发思路、AI 开发配合 SDD 流程图、架构图逐节点讲解、代码走读）。若必须压到 5 分钟，见第 7 节删减表。
> 真实性约束见第 6 节：**不预设假聊天记录、不编未发生的故事**，所有说法都有仓库内证据。

---

## 1. 录制前准备（约 20 分钟，一次性）

### 1.1 环境（已核实）

| 项 | 值 |
|---|---|
| 桌面 | niri（Wayland），工作区按输出独立 |
| 输出 | 外接 `HDMI-A-1` 1920×1080@1x（推荐录制）；笔记本 `eDP-1` 2880×1800@1.75 |
| 录屏 | `wf-recorder`（已验证可用） |
| 麦克风源 | `alsa_input.pci-0000_64_00.6.analog-stereo` |
| 编辑器 | VS Code |
| 图片查看 | `loupe` |
| 浏览器 | Chrome（flatpak） |
| 服务 | http://localhost:8081（当前已运行） |

### 1.2 演示账号（已预先注册好，空历史）

| 账号 | 密码 | 昵称 |
|---|---|---|
| `demo_alice` | `demo_alice123` | 爱丽丝 |
| `demo_bob` | `demo_bob123` | 鲍勃 |

当前数据库：用户 2 行，消息 0 行，文件 0 行，**没有任何聊天记录**。视频里的消息全部现场输入。
（如果重新清库/换机器，再手动注册；见文末附录。）

录制前只做两个登录动作（不录）：

1. 普通 Chrome 窗口打开 `http://localhost:8081` → `demo_alice / demo_alice123` 登录 → 进入聊天页后点右上角「退出」回到登录页（视频从这里开始录）。
2. 无痕窗口打开 `http://localhost:8081` → `demo_bob / demo_bob123` 登录 → 保持登录，停在群聊页。

### 1.3 niri 窗口布局（7 个工作区）

> 工作区按输出独立。先把鼠标移到**录制输出（外接屏）**上点一下确保焦点，再打开/移动窗口。

| 工作区 | 用途 | 窗口内容 |
|---|---|---|
| WS1 | 功能演示 | Chrome 普通窗口（爱丽丝）+ Chrome 无痕窗口（鲍勃），左右各 50% |
| WS2 | Shell 拉起服务 | 终端（仓库根目录），开场用一条 `java -jar` 命令拉起服务展示启动日志，后面用于断线重连演示 |
| WS3 | 开发过程 | VS Code：`docs/SPEC.md`、`docs/api.md`、`skills/lanchat-sdd/SKILL.md`、`docs/ai-devlog.md` |
| WS4 | AI 开发图 | `loupe` 打开 `report/figs/fig-08-01-sdd-workflow.png` |
| WS5 | 系统架构图 | `loupe` 打开 `report/figs/fig-05-01-architecture.png` |
| WS6 | 代码走读 | VS Code：`ChatWebSocketHandler.java`、`JdbcMessageRepository.java`、`frontend/src/api/ws.js` |
| WS7 | 结果 | Chrome：GitHub Release 页 + 本地报告 PDF（静态服务提供） |

预开窗口命令（在仓库根目录执行）：

```bash
cd /home/versi/Projects/Java
CHROME="$HOME/.local/share/flatpak/exports/bin/com.google.Chrome"

# WS1 功能演示两个窗口
"$CHROME" --new-window "http://localhost:8081" &
"$CHROME" --incognito --new-window "http://localhost:8081" &

# WS3 开发过程文档
code -n docs/SPEC.md docs/api.md skills/lanchat-sdd/SKILL.md docs/ai-devlog.md &

# WS6 代码走读
code -n \
  backend/src/main/java/com/lanchat/adapter/in/ws/ChatWebSocketHandler.java \
  backend/src/main/java/com/lanchat/adapter/out/persistence/JdbcMessageRepository.java \
  frontend/src/api/ws.js &

# WS4 AI 开发流程图 / WS5 系统架构图
loupe report/figs/fig-08-01-sdd-workflow.png &
loupe report/figs/fig-05-01-architecture.png &

# WS7 结果页：报告 PDF 用本地静态服务（flatpak Chrome 读不到 ~/Projects）
mkdir -p /tmp/lanchat-showcase
cp "report/课程报告.pdf" /tmp/lanchat-showcase/lanchat-report.pdf
(cd /tmp/lanchat-showcase && nohup python3 -m http.server 8000 --bind 127.0.0.1 >/tmp/lanchat-http.log 2>&1 &)
"$CHROME" --new-window "http://localhost:8000/lanchat-report.pdf" &
"$CHROME" --new-window "https://github.com/Versifine/lanchat/releases/tag/v1.0.0" &
```

摆窗口（先聚焦窗口再执行，或用你习惯的 `Mod+Shift+1..7`）：

```bash
niri msg action move-window-to-workspace 1     # 移到工作区 1
niri msg action focus-workspace 2              # 切到工作区 2（作用于当前聚焦输出）
niri msg action set-column-width "50%"         # 当前列半屏（WS1 两列各 50%）
```

> WS6 代码分屏：VS Code 里 `Ctrl+\` 拆分，左放后端两个类，右放前端 `ws.js`。
> `niri msg workspaces` 可以确认各工作区状态。

### 1.4 录制命令（已在本机验证）

```bash
# 录制外接屏 + 同录麦克风；Ctrl+C 停止
wf-recorder -o HDMI-A-1 -r 30 \
  --audio="alsa_input.pci-0000_64_00.6.analog-stereo" \
  -f "$HOME/Videos/lanchat-demo-$(date +%H%M).mp4"
```

> 实测产出：1920×1080 / 30fps / VP9 视频 + Opus 音频。
> 若老师播放器不支持 VP9，转通用 H.264（libopenh264 已验证可用）：
> `ffmpeg -i 原始.mp4 -c:v libopenh264 -b:v 8M -c:a aac -b:a 128k 输出-H264.mp4`

- 先试录 10 秒，`ffprobe -hide_banner 文件.mp4` 确认有画面、有音轨。
- 录前静音通知：`makoctl dismiss -a 2>/dev/null || true`；不想录顶栏可临时 `pkill waybar`，录完 `waybar &`。

---

## 2. 时间轴（总计 6:45）

| 时间 | 内容 | 工作区 | 详略 |
|---|---|---|---|
| 0:00–0:40 | 开场：项目一段话 + 技术栈 + 规模 | WS1 | 必留 |
| 0:40–1:05 | Shell 拉起服务：一条 `java -jar` 命令启动，展示启动日志 | WS2 | 必留 |
| 1:05–2:55 | 功能全展示（注册入口/登录/在线/群聊/历史/私聊/离线补发/文件/暗色） | WS1 | 必留（断线重连可选 +20s） |
| 2:55–4:10 | 开发过程思路（为什么规格先行、契约、架构选型、数据库、实时方案、安全） | WS3 | 重点 |
| 4:10–4:50 | AI 开发：对照 SDD 流程图讲（规格→Agent→审批→回需求→验证→发布） | WS4 | 重点 |
| 4:50–6:00 | 系统架构：对照架构图逐层/逐箭头讲清楚 | WS5 | 重点 |
| 6:00–6:30 | 代码走读：WS 消息处理 / 离线补发 / 前端连接管理 | WS6 | 重点 |
| 6:30–6:45 | 结果与收尾 | WS7 | 必留 |

---

## 3. 逐段台词与操作

### 0:00–0:40 开场：项目与技术栈（WS1）

**屏幕**：WS1，两个 Chrome 窗口并排（爱丽丝停在登录页，鲍勃在群聊）。

**台词（一段完整介绍，可直接念）**：
"老师好，我是本课程的学生。我的课程项目是 **LanChat 局域网聊天室**，一个前后端分离的 B/S 即时通信系统。浏览器端用 Vue 3、Vite 和 Element Plus；服务端是 Java 21 加 Spring Boot 3.5，数据存 SQLite，登录用 JWT，实时消息用 WebSocket，另外还有 REST 接口和 Swagger 文档。后端目前 72 个 Java 文件，前端 18 个源文件，自动化测试 160 个用例。项目已经发到 GitHub 的 Versifine/lanchat，有 v1.0 Release，配套 26 页课程报告。下面我分四部分讲：**功能全貌、开发过程的思路、AI 工程化怎么用，最后是系统架构和关键代码**。"

---

### 0:40–1:05 Shell 拉起服务（WS2）

**屏幕**：切到 WS2 终端（仓库根目录）。

**操作 1（约 5 秒）**：先看仓库里的启动脚本：

```bash
cd /home/versi/Projects/Java
cat scripts/run.sh
```

**台词**：
"服务端的启动入口就是这几行，本质上是一条 `java -jar` 命令。为了不影响正在演示的实例，我换个端口现场拉起来，看一下启动日志。"

**操作 2（约 15 秒，启动后等日志打印完立即 Ctrl+C）：**

```bash
java -jar backend/target/lanchat-backend.jar --server.port=8082
```

启动日志里会出现（约 2 秒）：

```
Tomcat started on port 8082 (http) with context path '/'
Started LanChatApplication in 1.993 seconds
LanChat 后端启动完成: REST=http://localhost:8082/api, WebSocket=ws://localhost:8082/ws/chat, Swagger=http://localhost:8082/swagger-ui.html
```

**台词**：
"这个 jar 里同时包含前端静态页面、REST 接口和 WebSocket 服务。启动完，日志会直接打印三个地址。生产形态用 `scripts/run.sh` 也是同一条命令，所以老师拿到压缩包后，一句 `java -jar lanchat.jar` 就能复现整套系统。"

**操作 3**：`Ctrl+C` 停掉 8082 这个演示实例，确认回到终端提示符，然后切到 WS1。

---

### 1:05–2:55 功能全展示（WS1）

> 前端只在这里出现，后面不再展开；每条消息都是现场输入，没有预置聊天记录。

**① 登录页与注册（约 12 秒）**

**操作**：爱丽丝窗口停在登录页。先点「注册」tab 展示注册表单字段（用户名/昵称/密码），再点回「登录」；输入 `demo_alice / demo_alice123` 登录。

**台词**：
"这是登录页。前端是 Vue 3 加 Element Plus，视觉上走纸张感的编辑排版风格，没有用彩色渐变。登录和注册共用一套表单校验。登录成功后，服务端签发 JWT，前端建立 WebSocket 长连接。"

**② 聊天主界面与在线列表（约 10 秒）**

**操作**：登录后进入三栏界面，指向顶栏 `ONLINE 2` 和左侧在线用户。

**台词**：
"主界面分三栏。左边是群聊入口和在线用户，中间是消息区，下面是输入区。顶栏显示在线人数和连接状态，这些状态由服务端通过 WebSocket 推送过来。"

**③ 群聊 + 历史持久化（约 20 秒）**

**操作**：爱丽丝发「这是现场发送的群聊演示消息」→ 指向鲍勃窗口（实时收到）→ 鲍勃回「鲍勃窗口收到，实时显示」→ 爱丽丝窗口刷新页面（`Ctrl+R`）→ 历史消息还在。

**台词**：
"群聊消息先写入数据库，再广播给在线用户。两个窗口看到的消息顺序一致，时间戳由服务端统一生成。刷新页面之后消息还在，说明历史是真的落库了，不依赖浏览器内存。"

**④ 私聊 + 未读（约 12 秒）**

**操作**：鲍勃给爱丽丝发一条私聊（或爱丽丝点左侧鲍勃进入私聊发一条）→ 指向未读红点/私聊会话。

**台词**：
"私聊只推送给指定的接收者，其他在线用户收不到。消息不属于当前打开的会话时，侧栏会显示未读计数。"

**⑤ 离线消息补发（约 25 秒，重点）**

**操作**：
1. 鲍勃窗口点右上角「退出」→ 确认退出；
2. 爱丽丝给鲍勃发一条私聊：「这是一条离线消息，鲍勃现在不在线」；
3. 鲍勃重新登录 `demo_bob / demo_bob123`；
4. 指向鲍勃收到的消息，带 `OFFLINE` 标记。

**台词**：
"这一步是离线补发。接收方不在线时，私聊消息先入库。等对方重新登录，服务端把未投递的消息补发一次，前端标上 OFFLINE。这条消息现在确实写在数据库里，不是临时显示。"

**⑥ 文件传输（约 15 秒）**

**操作**：爱丽丝在私聊里点回形针 → 选 `report/figs/fig-05-01-architecture.png` → 发送 → 指向鲍勃收到的文件卡片（大小、SHA-256、DOWNLOAD）。

**台词**：
"文件通过 HTTP multipart 上传。服务端会清洗文件名、限制大小，流式写入磁盘同时计算 SHA-256，再把元数据入库。如果指定了接收人，服务端通过 WebSocket 通知对方，接收方点 DOWNLOAD 就能下载。"

**⑦ 暗色模式（约 5 秒）**

**操作**：点顶栏 `LIGHT / DARK` 切换。

**台词**：
"界面支持深色模式。同一套设计变量切换过去，偏好也会保存在本地。"

**⑧（可选 +20 秒）断线重连**

**操作**：切到 WS2 终端 `Ctrl+C` 停服务 → 两个窗口出现「连接断开，正在重连…」→ 重新启动服务（`java -jar backend/target/lanchat-backend.jar &`）→ 约 2–5 秒后自动恢复。

**台词**：
"断线重连这块，前端每 10 秒发一次心跳。服务端重启时页面会提示正在重连，重试间隔从 1 秒逐步退到 30 秒，连接恢复后自动重新拉取历史。"

---

### 2:55–4:10 开发过程思路（WS3，重点）

**屏幕**：WS3，VS Code 依次展示四个文档；终端可辅助 `git log`。

**展示 `docs/SPEC.md`（约 15 秒）**

**台词（讲思路，不只是念文件）**：
"我先说开发思路。一开始我没有让 AI 直接写代码，而是先想清楚'怎样才算做完'。需求分析拆成三类：功能需求、可量化的性能需求，比如 500 连接下 P95 延迟不超过 200 毫秒，还有安全需求，比如密码存储、上传校验、限流。每条需求都写验收标准，这样后面判断代码是不是真的完成，不用靠感觉。"

**展示 `docs/api.md`（约 15 秒）**

**台词**：
"接下来是契约先行。前后端是两套技术栈，不能共享运行时代码，所以我把 REST 路径、WebSocket 消息类型、错误码写成一份唯一契约，字段名精确到大小写。两边并行开发时不用读对方代码，联调只对契约。这是前后端分离的代价，也是它带来的工程收益。"

**展示 `skills/lanchat-sdd/SKILL.md`（约 15 秒）**

**台词**：
"再往下，我把契约和规约落成一份 skill，里面写清项目结构、分层规则、构建和验收命令，还有哪些高风险动作必须人工审批。所有 AI agent 开工前先读这份 skill，相当于它们的开发手册。"

**展示 `docs/eval-plan.md`（约 15 秒）**

**台词**：
"最后是评测集。我先写验收用例，功能用例、安全用例、性能用例都写，然后再写实现。这样'做完了'有客观标准，AI 生成的代码也必须过这道门。"

**切终端 `git log --oneline -8`（约 10 秒）+ 回到 VS Code `docs/ai-devlog.md`**

**台词（开发思路总结）**：
"接着是几个关键的设计决策。架构上选整洁架构，因为业务代码不该依赖 Spring 和数据库；模块用单个 Maven 模块加包分层，项目规模不大，简单优先。数据库选 SQLite，零部署、单文件，开 WAL 处理并发读写。实时方案选 WebSocket，因为轮询延迟太高。报告里有实测对比：WebSocket 的 P95 约 327 毫秒，3 秒轮询基本就是 3 秒级。文件用 multipart 加 SHA-256，简单可靠，分片续传留作以后做。安全上用 JWT 做无状态认证，密码 PBKDF2 加盐，SQL 全部参数化，消息按纯文本渲染防 XSS，另外还有上传校验和限流。整个过程记在 AI 开发日志里，包括两次真实的需求回调：最初想做 RPC 框架，考虑到报告评审更看重看得见的功能，改成聊天室；最初是 Swing 桌面端，后来改成前后端分离。"

---

### 4:10–4:50 AI 开发：对照 SDD 流程图（WS4，重点）

**屏幕**：WS4，`fig-08-01-sdd-workflow.png` 全屏。

**台词（按图走）**：
"这张图是项目的 AI 工程化流程。最上面是'需求与规格'：课程需求先变成 SPEC，再把开发规约写成 Skill。中间是'AI Agent 执行'：编码 Agent 和测试 Agent 并行，后端、前端、测试各是一个独立 agent，有自己的共享任务和写入范围，避免互相踩文件。右边是'人工审批与纠偏'：建公开仓库、发布、改契约这类高风险动作必须有人确认；方向不对就走'回需求重定义边界'，回头改规格。这个项目真实发生了两次，图里也标了：RPC 框架改成聊天室，Swing 改成前后端分离。最下面是'验证与交付'：160 个用例加真实压测数据做自动化验证，最后才是报告和 GitHub 发布。另外测试阶段还发现两个真实缺陷：扩展名正则写错、SQLite 唯一约束没翻译成 409。修完回归全过，日志和提交里都能查到。"

---

### 4:50–6:00 系统架构：对照架构图逐层讲（WS5，重点）

**屏幕**：WS5，`fig-05-01-architecture.png` 全屏。按从左到右、再上下讲。

**台词（按图逐块）**：
"这张是系统总体架构，我按数据流讲一遍。最左边是浏览器用户。中间蓝色块是 Vue 3 前端，用 Element Plus 做组件、Pinia 做状态、Axios 做请求。前端有两条路进后端：一条是 REST 接口 `/api/**`，走 JSON；另一条是 WebSocket 服务 `/ws/chat`，走实时消息。两条路都落在 Spring Boot 这个单体服务里，端口 8081。

进入服务端，是整洁架构的分层。上面这层是适配器：REST 接口由 Spring MVC 的控制器处理，WebSocket 由 Handler 处理，它们只做协议转换。中间是应用层用例，Auth、Chat、User、File 四个服务，承载业务逻辑。右边是领域层，放实体和端口接口，它不依赖 Spring，也不依赖数据库。再往右是适配器实现：JdbcTemplate 写 SQLite，文件存储用本地磁盘流式读写。依赖方向只允许由外向内：adapter 依赖 application，application 依赖 domain，domain 不依赖任何东西，所以换框架、换数据库都不影响业务代码。上面的红色节点是认证：登录时校验密码并签发 JWT，之后 REST 用 Bearer token，WebSocket 握手用 token 参数，服务端校验通过后拿到用户身份。

数据上是三张表：用户、消息、文件。SQLite 开了 WAL，压测中 1000 多条每秒持续写入，没有出现锁错误。安全方面还有：密码 PBKDF2 加盐、SQL 全部参数化、消息纯文本渲染、上传文件名清洗，以及消息和登录限流。图下面四张卡片是前端、后端分层、安全与数据、部署形态。部署上就是一个进程，前端构建产物由后端静态托管，一条 `java -jar` 命令就能跑起来。

还有一个诚实的局限：压测暴露了建连高峰时全量在线列表广播的 O(N²) 问题。报告里写了，后续会改成增量或合并推送。"

---

### 6:00–6:30 代码走读（WS6，重点）

**屏幕**：WS6，VS Code 左半屏后端，右半屏前端。

**代码 1：`ChatWebSocketHandler.java`（约 15 秒）**

**台词**：
"WebSocket 收到聊天消息后，主流程在这里。先从会话属性里拿到 JWT 解出的用户身份，不信任消息体里的发送者字段。然后校验消息长度和频率限流。再交给应用层落库。最后根据有没有接收人，决定群聊广播还是私聊推送。Handler 只做协议转换。"

**代码 2：`JdbcMessageRepository.java`（约 10 秒）**

**台词**：
"离线补发的关键在这段。用户上线时，服务端在一个事务里查出所有未投递的私聊，统一把 delivered 置 1，避免并发登录时重复补发。SQL 全部参数化。"

**代码 3：`frontend/src/api/ws.js`（约 10 秒）**

**台词**：
"前端只做连接管理：用 token 建连，10 秒心跳，断线后按 1 到 30 秒指数退避重连，消息按类型分发给状态层。消息按纯文本渲染，防 XSS。这些代码是 AI agent 按前面的规格和评测集产出的，我逐块 review 和验收，报告第 8 章有记录。"

---

### 6:30–6:45 结果与收尾（WS7）

**屏幕**：切 WS7，Chrome 停在 GitHub Release 页；可切到报告 PDF 第一页。

**台词**：
"最后是结果。160 个用例全部通过。500 并发连接下，消息延迟 P50 10.1、P95 21.4、P99 31.2 毫秒，投递率 100%。20MB 文件上传平均 160MB/s。轮询对比实验里，WebSocket 的延迟比 3 秒轮询低一个数量级。项目已经发在 GitHub 的 Versifine/lanchat，有 v1.0 Release 和 26 页课程报告，压缩包里一条 `java -jar lanchat.jar` 就能复现。以上是我的项目介绍，谢谢老师。"

---

## 4. 操作速查卡

| 操作 | 命令/按键 |
|---|---|
| 切工作区 | `Mod+1` … `Mod+7`（或 `niri msg action focus-workspace N`） |
| 开始/停止录制 | 终端执行 1.4 命令；停止按 `Ctrl+C` |
| 切 VS Code 文件 | `Ctrl+P` |
| VS Code 分屏 | `Ctrl+\` |
| 断线重连演示 | WS2 终端 `Ctrl+C` 停服务，2 秒后 `java -jar backend/target/lanchat-backend.jar &` 重启 |
| 避免鼠标抖动 | 点击前停 0.5 秒，移动放慢 |

---

## 5. 需求覆盖对照（功能全展示的依据）

| 功能 | 视频中展示位置 |
|---|---|
| 注册 / 登录 / JWT / 登出 | 功能①，离线补发时也出现登出再登录 |
| 在线状态与用户列表 | 功能②，顶栏 `ONLINE 2` |
| 群聊、历史消息持久化 | 功能③，含刷新页面后历史仍在 |
| 私聊、未读计数 | 功能④ |
| 离线消息补发（OFFLINE 标记） | 功能⑤ |
| 文件上传/下载、SHA-256 校验 | 功能⑥ |
| 暗色模式 | 功能⑦ |
| 心跳与断线自动重连 | 功能⑧（可选现场演示）+ 代码走读 |
| 消息长度/频率限制、登录限流 | 不现场展示，测试用例与架构讲解中说明 |
| 重复登录踢下线、Swagger 接口文档 | 不现场展示，报告中说明；Swagger 地址 `http://localhost:8081/swagger-ui.html` |

---

## 6. 真实性约束（必须遵守）

1. **不编造聊天记录**：当前数据库为空（用户 2、消息 0、文件 0）；视频里所有消息现场输入；旁白说明"这是现场发送的演示消息"。不要恢复旧库或预灌数据。
2. **不编未发生的故事**：只讲真实发生过的事（两次方案回调、两个缺陷、压测数据、审批），证据见下表。
3. **数字不美化**：性能数字与 `assets/bench/bench_summary.csv` 一致；同时如实说局限（1000 连接未测、500 连接短时运行、userList 广播瓶颈）。
4. **不虚构他人**：不编老师反馈、不编不存在的团队成员；AI agent 产出就说"AI agent 按规格产出，我验收修订"。
5. 口误了停机重录那一段，不用剪辑掩盖成"事实"。

### 证据索引

| 说法 | 证据位置 |
|---|---|
| 72 个 Java 文件 / 18 个前端源文件 | 仓库根目录执行 `find backend/src/main/java -name "*.java" \| wc -l`、`find frontend/src -type f \| wc -l` |
| 160 个用例全过 | `assets/test-report/summary.txt`、`assets/test-report/surefire-reports/` |
| 500 连接延迟、吞吐、轮询对比、上传速度 | `assets/bench/bench_summary.csv`、`assets/bench/README.md`、`assets/bench/polling_vs_ws.csv`、`assets/bench/file_upload.csv` |
| 规格/契约/评测集/skill | `docs/SPEC.md`、`docs/api.md`、`docs/eval-plan.md`、`skills/lanchat-sdd/SKILL.md` |
| 两次方案回调、两个缺陷、审批 | `docs/ai-devlog.md`、`git log --oneline` |
| 架构分层、数据表、安全设计 | `report/figs/fig-05-01-architecture.png`、`docs/SPEC.md` |
| SDD 流程 | `report/figs/fig-08-01-sdd-workflow.png`、`docs/ai-devlog.md` |
| 代码行为 | 代码走读点名的三个源文件 |
| 报告与发布 | 课程报告 PDF、GitHub 仓库 |

---

## 7. 压缩到 5 分钟的删减表

| 砍掉 | 节省 | 说明 |
|---|---|---|
| Shell 启动日志完整展示压到 15 秒（只留启动完成那几行） | 10s | 启动演示保留，减少等待镜头 |
| 功能⑤离线补发 | 25s | 功能表里注明"离线补发已由测试覆盖"，或只在报告里讲 |
| 功能⑧断线重连现场演示 | 20s | 默认就是可选项，不演示 |
| 开发过程思路压到 45s | 30s | 只讲规格先行 + 契约 + 两个选型理由 |
| 架构讲解压到 45s | 25s | 只讲分层与依赖方向，卡片略过 |
| 代码走读压到 20s | 10s | 只讲 `ChatWebSocketHandler` 一个类 |

按上表删减后约 **5:00**（6:45 − 2:00）；保留标"重点"的段落和开场这段话即可。

---

## 附：如果重新清库时的手动注册步骤

```bash
pkill -f 'lanchat-backend.jar' || true
cd /home/versi/Projects/Java && rm -rf data
nohup java -jar backend/target/lanchat-backend.jar >/tmp/lanchat-demo-server.log 2>&1 &
sleep 6
# 普通 Chrome 注册 demo_alice / demo_alice123（昵称 爱丽丝）→ 退出
# 无痕 Chrome 注册 demo_bob / demo_bob123（昵称 鲍勃）→ 保持登录
```

## 附：录后收尾

1. 检查：`ffprobe -hide_banner ~/Videos/lanchat-demo-*.mp4 | grep -E 'Stream|Duration'`
2. 命名：`LanChat项目介绍.mp4`
3. 如超过 7:00，按第 7 节删减重录或剪辑（优先压前两段）。
4. 需要转码：`ffmpeg -i 原始.mp4 -c:v libopenh264 -b:v 8M -c:a aac -b:a 128k 最终.mp4`
5. 与服务一起提交：按【学号_姓名】命名的压缩包与 GitHub 地址。
6. 关闭录制服务：`pkill -f 'lanchat-backend.jar'`、`pkill -f 'http.server 8000'`

---

## 8. 文本处理记录（academic-humanizer）

本稿台词按 academic-humanizer 的规则做过一遍清理，只改表达，不改事实：

- 删除全部破折号（改为逗号、冒号或断句）；
- 把超过 60 个汉字的台词拆成短句，一句一个意思，方便口播；
- 去掉"最大""显然"之类无证据的强化词和"不是……而是……"的对比句式；
- 去掉"第一步/第二步"式机械编号，改成"我先说……接下来……再往下……最后"这类口语衔接；
- 保留全部数字、指标、命令、文件名、账号与结论（160 用例、P50/P95/P99、1066 msg/s、160MB/s、26 页等均未改动）；
- 保留必要的技术限定与真实局限说明（1000 连接未测、userList 广播瓶颈）。
