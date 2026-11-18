# LanChat 五分钟录屏讲稿（开发过程 / 架构 / 代码为主）

> 用途：在本机录制一段 5 分钟项目介绍视频，发给课程老师。**不需要上台。**
> 讲解配比：功能速览约 40 秒（前端一句话带过），其余约 4 分钟全部放在**开发过程思路、系统架构、代码走读**上。
> 真实性约束见第 5 节：**不预设假聊天记录、不编造未发生的故事**，每个说法都能在仓库里查到证据。

---

## 1. 录制前准备（一次性，约 20 分钟）

### 1.1 本机环境（已核实）

| 项 | 值 |
|---|---|
| 桌面 | niri（Wayland），工作区按输出独立 |
| 输出 | 笔记本 `eDP-1` 2880×1800@1.75（逻辑 1645×1028）；外接 `HDMI-A-1` 1920×1080@1x |
| 录屏工具 | `wf-recorder`（原生，推荐）、`gpu-screen-recorder`（备选） |
| 麦克风源 | `alsa_input.pci-0000_64_00.6.analog-stereo`（用 `pactl list short sources` 可复查） |
| 编辑器 | VS Code（`code`） |
| 图片查看 | `loupe` |
| 浏览器 | Chrome（flatpak） |
| 服务端口 | http://localhost:8081 |

> **推荐录制 `HDMI-A-1`（1920×1080，16:9，缩放 1）**：窗口字号正常、编码压力小。
> 若只用笔记本屏，录 `eDP-1` 也可以，但录制分辨率会是 2880×1800，建议用 `gpu-screen-recorder` 走 GPU 编码。

### 1.2 真实数据准备（关键：不预置、不编造聊天记录）

视频里的聊天消息**全部在录制时现场输入**。为此先清空数据库，只保留两个账号、零条聊天记录：

```bash
# ① 停掉现有服务（如果正在运行）
pkill -f 'lanchat-backend.jar' || true
sleep 1

# ② 清空运行时数据（只删 gitignored 的 data/，不影响代码）
cd /home/versi/Projects/Java
rm -rf data

# ③ 启动服务（干净库，首次启动自动建表）
nohup java -jar backend/target/lanchat-backend.jar >/tmp/lanchat-demo-server.log 2>&1 &
sleep 6
curl -s -o /dev/null -w '服务状态 %{http_code}（401 表示正常需登录）\n' http://localhost:8081/api/users
```

**账号已预先注册好**（2026-11-18 由助手完成，空历史）：

| 账号 | 密码 | 昵称 | 状态 |
|---|---|---|---|
| `demo_alice` | `demo_alice123` | 爱丽丝 | 已注册，未登录 |
| `demo_bob` | `demo_bob123` | 鲍勃 | 已注册，未登录 |

当前数据库：用户 2 行、消息 0 行、文件 0 行——**没有任何聊天记录**，视频里的每条消息都是现场输入。

录制前只需两个登录动作（不录）：

1. 普通 Chrome 窗口打开 `http://localhost:8081` → 用 `demo_alice / demo_alice123` 登录 → 进入聊天页后 **点右上角「退出」回到登录页**（视频从这里开始录）。
2. 无痕 Chrome 窗口打开 `http://localhost:8081` → 用 `demo_bob / demo_bob123` 登录 → 保持登录状态，停在群聊页。

> 如果换了机器或重新清库，再按下面的原始注册步骤手动注册即可：
> 普通窗口注册 `demo_alice / demo_alice123`（昵称爱丽丝）→ 退出；无痕窗口注册 `demo_bob / demo_bob123`（昵称鲍勃）→ 保持登录。

完成后状态：

- 爱丽丝：已注册、已退出，停在登录页；
- 鲍勃：已登录、在线；
- 群聊历史为空（**没有任何预置对话**，视频里发出的每条消息都是现场真实输入）。

### 1.3 niri 窗口布局（5 个工作区）

> 工作区按输出独立。先把鼠标移到**录制输出**上点一下（确保焦点在该输出），再打开/移动窗口。

| 工作区 | 用途 | 窗口 |
|---|---|---|
| WS1 | 功能速览 | 两个 Chrome 窗口左右两列各 50%：爱丽丝（普通）、鲍勃（无痕） |
| WS2 | 开发过程 | VS Code 窗口（打开 docs 四个文件）+ 终端窗口（可查 git log / 文件树） |
| WS3 | 架构 | `loupe` 打开 `report/figs/fig-05-01-architecture.png`；同目录可左右键切 `fig-08-01-sdd-workflow.png` |
| WS4 | 代码走读 | 另一个 VS Code 窗口，左右分屏：后端两个类 + 前端 ws.js |
| WS5 | 结果 | Chrome 标签页：GitHub Release、报告 PDF（由本地静态服务提供） |

预开窗口命令（在仓库根目录执行）：

```bash
cd /home/versi/Projects/Java
CHROME="$HOME/.local/share/flatpak/exports/bin/com.google.Chrome"

# 功能演示两个窗口（先都打开，登录/注册按 1.2 做）
"$CHROME" --new-window "http://localhost:8081" &
"$CHROME" --incognito --new-window "http://localhost:8081" &

# 开发过程：VS Code 打开文档
code -n docs/SPEC.md docs/api.md skills/lanchat-sdd/SKILL.md docs/ai-devlog.md &

# 代码走读：VS Code 打开三个文件
code -n \
  backend/src/main/java/com/lanchat/adapter/in/ws/ChatWebSocketHandler.java \
  backend/src/main/java/com/lanchat/adapter/out/persistence/JdbcMessageRepository.java \
  frontend/src/api/ws.js &

# 架构图查看器
loupe report/figs/fig-05-01-architecture.png &

# 结果页：报告 PDF 用本地静态服务给 Chrome 看（flatpak Chrome 读不到 ~/Projects）
mkdir -p /tmp/lanchat-showcase
cp "report/课程报告-（姓名）-（学号）.pdf" /tmp/lanchat-showcase/lanchat-report.pdf
(cd /tmp/lanchat-showcase && nohup python3 -m http.server 8000 --bind 127.0.0.1 >/tmp/lanchat-http.log 2>&1 &)
"$CHROME" --new-window "http://localhost:8000/lanchat-report.pdf" &
"$CHROME" --new-window "https://github.com/Versifine/lanchat/releases/tag/v1.0.0" &
```

把窗口放到对应工作区（先聚焦窗口，再执行；也可用你习惯的 `Mod+Shift+1..9`）：

```bash
niri msg action move-window-to-workspace 1   # 把当前窗口移到工作区 1
niri msg action focus-workspace 2            # 切到工作区 2（作用于当前聚焦输出）
niri msg action set-column-width "50%"       # 把当前列调成半屏（WS1 两列各 50%）
```

> WS1 两列等宽：选中 Chrome 窗口 → `set-column-width "50%"`；另一个窗口同样操作。
> WS4 左右分屏：VS Code 里 `Ctrl+\` 拆分编辑器，左放后端类、右放前端 ws.js。
> 键位以你本机 niri 配置为准，`niri msg workspaces` 可确认各工作区状态。

### 1.4 录制命令

```bash
# 推荐：录制外接屏 + 同时录麦克风；Ctrl+C 停止
wf-recorder -o HDMI-A-1 -r 30 \
  --audio="alsa_input.pci-0000_64_00.6.analog-stereo" \
  -f "$HOME/Videos/lanchat-demo-$(date +%H%M).mp4"
```

> 本机已实测：上面的命令产出 **1920×1080 / 30fps / VP9 视频 + Opus 音频** 的 mp4，可直接播放。
> 30fps 对界面录屏足够；60fps 体积更大且编码更重，不必要。
> 如果老师电脑的播放器不支持 VP9，转成通用 H.264（本机已验证 libopenh264 可用）：
> ```bash
> ffmpeg -i 原始.mp4 -c:v libopenh264 -b:v 8M -c:a aac -b:a 128k 输出-H264.mp4
> ```
> （已安装 `gpu-screen-recorder` 可作为备选，但本机未验证，不建议第一次就用它。）

- 先试录 10 秒，`ffprobe -hide_banner 文件.mp4` 确认有画面、有音轨，再正式录。
- 录前把通知静音：`makoctl dismiss -a 2>/dev/null || true`；不想录进顶栏可临时 `pkill waybar`，录完再 `waybar &`。
- 全程用 niri 切工作区（`Mod+2`、`Mod+3`…），鼠标移动放慢，点击前先停 0.5 秒。

---

## 2. 时间分配（总计 5:00）

| 时间 | 内容 | 重点程度 |
|---|---|---|
| 0:00–0:20 | 开场：项目一句话 + 本次讲解重点 | — |
| 0:20–1:00 | 功能速览（前端一句带过，消息现场输入） | 简略 |
| 1:00–2:30 | **开发过程：规格 → 契约 → Skill → 评测集 → Agent → 人工审批** | 重点 |
| 2:30–3:20 | **系统架构：分层、依赖方向、数据与安全** | 重点 |
| 3:20–4:35 | **代码走读：WS 消息处理、离线补发、前端连接管理** | 重点 |
| 4:35–5:00 | 结果与交付：测试、性能、仓库、报告 | — |

---

## 3. 逐段台词与屏幕操作

### 0:00–0:20 开场（WS1）

**屏幕**：停在 WS1，两个浏览器窗口并排。

**台词**：
"老师好，我是计算机科学与技术 24-4 班的（姓名），这是我的课程项目 LanChat 局域网聊天室。左边是爱丽丝，右边无痕窗口是鲍勃，两个账号连同一个 Java 服务端。这个视频里功能只快速过一遍，重点讲三块：**开发过程怎么走的、系统架构怎么设计的、关键代码怎么写的**。"

---

### 0:20–1:00 功能速览（WS1，40 秒）

**屏幕 1（约 10 秒）**：爱丽丝窗口停在登录页，输入 `demo_alice / demo_alice123`，点「LOG IN / 登 录」进入聊天页。

**台词**：
"前端是 Vue 3 + Element Plus 的单页应用，视觉走纸张感的编辑排版风格，没有用彩色渐变。登录用 JWT，登录成功建立 WebSocket 长连接。"

**屏幕 2（约 12 秒）**：爱丽丝在群聊输入框输入「这是现场发送的群聊演示消息」→ 回车；镜头转向鲍勃窗口，消息实时出现；鲍勃回一条「鲍勃窗口收到，实时显示」。

**台词**：
"这是群聊：服务端先落库再广播，两个窗口都能实时看到，时间戳由服务端统一。**这两条消息是我现在当场输入的，没有预置历史记录**，一会儿代码里会看到它确实写进了 SQLite。"

**屏幕 3（约 10 秒）**：爱丽丝点左侧「鲍勃」进入私聊，发送「这是一条现场发送的私聊消息」；鲍勃窗口收到。

**台词**：
"私聊只推给指定用户，其他人收不到；如果对方离线，消息会入库，等对方上线补发一次。"

**屏幕 4（约 8 秒）**：爱丽丝发文件 `report/figs/fig-05-01-architecture.png`，切鲍勃窗口看文件卡片。

**台词**：
"文件通过 HTTP 上传，服务端计算 SHA-256 并入库，再通过 WebSocket 通知接收方。"

---

### 1:00–2:30 开发过程（WS2，90 秒，重点 1）

**屏幕**：切到 WS2，VS Code 里依次展示四个文档（每个停留 10–15 秒），最后切终端看 `git log`。

**展示 1：`docs/SPEC.md`**

**台词**：
"项目不是直接写代码，而是先按规格驱动的方式做。第一步需求分析，写成可验收的规格：功能需求、性能指标、安全要求，每一条都带验收标准，比如 500 连接下 P95 延迟不超过 200 毫秒、密码必须用 PBKDF2 加盐存储。"

**展示 2：`docs/api.md`**

**台词**：
"第二步是把前后端接口写成唯一契约：REST 路径、WebSocket 消息类型、错误码，字段名精确到 camelCase，前端和后端各自按它实现，联调时不需要翻对方代码。"

**展示 3：`skills/lanchat-sdd/SKILL.md`**

**台词**：
"第三步把它落成开发规约，也就是课程说的 skill：分层规则、依赖方向、构建和验收命令、哪些高风险动作必须人工审批。后面所有 AI agent 开工前先读这份规约。"

**展示 4：`docs/eval-plan.md`**

**台词**：
"第四步是评测集，也就是先写验收用例：功能用例、安全用例、性能用例；这样‘做完了’有客观标准，不是自己说了算。"

**屏幕**：切终端，执行 `git log --oneline -12` 和 `ls docs skills`。

**台词**：
"然后才是分工实现：主控把任务拆到共享任务板，限定每个 agent 的写入范围；后端、前端、测试、报告分别由不同的 AI agent 并行产出，我负责规格、审批、验收和关键决策。整个过程记在 AI 开发日志里，包括两次真实的需求回调：最初想做 RPC 框架，考虑到报告评审更看重看得见的功能，改成聊天室；最初是 Swing 桌面端，后来改成前后端分离。还有测试阶段发现的两个真实缺陷——文件名扩展名正则写成了精确 12 位、SQLite 唯一约束异常没被翻译成 409——修完都回归到了 160 个用例全过。这些记录和提交历史都能查，不是事后编的。"

---

### 2:30–3:20 系统架构（WS3，50 秒，重点 2）

**屏幕**：切 WS3，架构图全屏。

**台词**：
"这是系统总体架构。浏览器端是 Vue 3，通过 REST 和 WebSocket 连到 Spring Boot。后端按整洁架构分层：adapter 层做协议转换，in 这一侧有 REST 控制器和 WebSocket Handler 两个适配器；application 层是 Auth、Chat、User、File 四个用例服务；domain 层是纯 Java 的实体和端口接口，不依赖 Spring 和 JDBC；adapter/out 实现端口，落到 SQLite 和本地文件存储。依赖方向只允许由外向内，所以业务逻辑改起来不用动框架。"

**可选操作**：`loupe` 里切到 `fig-08-01-sdd-workflow.png`（若有需要再讲一句 SDD 流程图）。

**台词（继续）**：
"数据层三张表：用户、消息、文件；SQLite 开了 WAL，单文件零部署。安全上是 JWT 加 PBKDF2、全部 SQL 参数化、消息纯文本渲染防 XSS、上传文件名清洗。压测也暴露了真实瓶颈：建连时全量在线列表广播是 O(N²)，500 连接同时建连会广播风暴，报告里如实写了，后续改成增量或合并推送。"

---

### 3:20–4:35 代码走读（WS4，75 秒，重点 3）

**屏幕**：切 WS4，VS Code 左半屏 `ChatWebSocketHandler.java`，讲完切右半屏 `JdbcMessageRepository.java`，最后 `Ctrl+P` 打开 `frontend/src/api/ws.js`。

**代码 1：`ChatWebSocketHandler.java`（约 30 秒）**

**台词**：
"WebSocket 收到一条聊天消息后的主流程在这里：先从会话属性拿 JWT 解出的用户身份，**不信任消息体里的发送者字段**；然后校验消息长度和频率限流；再交给应用层用例落库；最后根据有没有接收人决定群聊广播还是私聊推送。整个过程在业务层完成，Handler 只做协议转换。"

**代码 2：`JdbcMessageRepository.java`（约 25 秒）**

**台词**：
"离线补发的关键在这个方法：用户上线时，在一个事务里先查出所有未投递的私聊消息，再统一把 delivered 置 1，避免并发登录时重复补发。SQL 全部 PreparedStatement 参数化。"

**代码 3：`frontend/src/api/ws.js`（约 20 秒）**

**台词**：
"前端这块只做连接管理：用 token 建连、每 10 秒心跳、断线后按 1、2、4、8、16、30 秒指数退避重连，收到消息按类型分发给状态层。消息渲染是纯文本插值，没有 v-html，避免 XSS。"

**补充一句（约 5 秒）**：
"这些代码是 AI agent 按前面的规格和评测集产出的，我逐块 review 和验收，报告第 8 章写了这个过程。"

---

### 4:35–5:00 结果与交付（WS5，25 秒）

**屏幕**：切 WS5，Chrome 停在 GitHub Release 页面；可快速滚到报告 PDF 第一页。

**台词**：
"结果是：160 个 JUnit 用例全部通过；500 并发连接下消息延迟 P50 10.1、P95 21.4、P99 31.2 毫秒，向 500 个连接广播的吞吐约 1066 条每秒；20MB 文件上传平均 160MB/s。项目已发布在 GitHub 的 Versifine/lanchat，有 v1.0 Release 和 26 页课程报告，压缩包里一条 `java -jar lanchat.jar` 就能复现。以上就是我的项目介绍，谢谢老师。"

---

## 4. 操作速查卡（录制时手边放）

| 操作 | 命令/按键 |
|---|---|
| 切换工作区 | `Mod+2`…`Mod+5`（或 `niri msg action focus-workspace N`） |
| 开始录制 | 终端执行第 1.4 节命令，确认开始后等 2 秒再操作 |
| 停止录制 | 回到录制终端按 `Ctrl+C` |
| 切 VS Code 文件 | `Ctrl+P` 输入文件名 |
| VS Code 左右分屏 | `Ctrl+\` |
| 切浏览器标签 | `Ctrl+Tab` |
| 避免鼠标抖动 | 点击前停 0.5 秒，移动放慢 |

---

## 5. 真实性约束（必须遵守）

1. **不编造聊天记录**：录制前清空 `data/`；视频里所有消息都是现场输入；旁白明确说“现在发送的是演示消息”。不要恢复旧库里的历史对话，也不要为了画面好看预先灌数据。
2. **不编造未发生的故事**：只讲真实发生过的事（方案回调、缺陷、压测、审批），每条都能在 `docs/ai-devlog.md`、git 提交、`assets/bench/`、`assets/test-report/` 里查到。
3. **数字不许美化**：性能数字与 `assets/bench/bench_summary.csv` 一致；同时如实说局限（1000 连接未测、500 连接短时运行、userList 广播瓶颈）。
4. **不冒充他人**：不虚构老师反馈、不虚构团队分工之外的角色；AI agent 的产出就说“AI agent 按规格产出，我验收和修订”。
5. 如果录制中口误说了不准确的话，**停机重录那一段**，不要用剪辑掩盖成“事实”。

### 证据索引（每个说法的出处）

| 视频中的说法 | 证据位置 |
|---|---|
| 160 个测试用例、功能/安全覆盖 | `assets/test-report/summary.txt`、`assets/test-report/surefire-reports/` |
| 500 连接延迟 P50/P95/P99、吞吐 | `assets/bench/bench_summary.csv`、`assets/bench/README.md` |
| 20MB 上传 160MB/s | `assets/bench/file_upload.csv` |
| 轮询 vs WebSocket 对比 | `assets/bench/polling_vs_ws.csv` |
| 规格/契约/评测集/skill | `docs/SPEC.md`、`docs/api.md`、`docs/eval-plan.md`、`skills/lanchat-sdd/SKILL.md` |
| 两次方案回调、两个缺陷 | `docs/ai-devlog.md`、`git log --oneline` |
| 架构分层与依赖方向 | `report/figs/fig-05-01-architecture.png`、`docs/SPEC.md` 第 5 节 |
| 代码行为 | 第 3 节点名的三个源文件 |
| 26 页报告与发布 | `report/课程报告-（姓名）-（学号）.pdf`、GitHub Release |

---

## 6. 录后收尾

1. 检查视频：`ffprobe -hide_banner -show_streams ~/Videos/lanchat-demo-*.mp4 | grep -E 'codec_type|width|height|duration'`
2. 命名：`LanChat项目介绍-（姓名）-（学号）.mp4`（或按老师要求的格式）。
3. 如果时长超 5:10，优先压缩"功能速览"（从 40 秒压到 25 秒），不要压缩代码和架构部分。
4. 上传到课程平台/发给老师；必要时同时附上 `（学号）_（姓名）.zip` 和 GitHub 地址。
5. 录制用的服务与静态服务可以保留或关闭：
   ```bash
   pkill -f 'lanchat-backend.jar'
   pkill -f 'http.server 8000'
   ```

---

## 附：如果老师看完视频后追问（可选准备）

- **为什么前后端分离？**：报告评审和演示需要零安装、界面直接可见；契约用 `docs/api.md` 固定，两端可以独立实现。
- **AI 参与了多少？**：规格、评测集、审批和验收由我负责，代码和测试由多个 AI agent 按规格产出；`docs/ai-devlog.md` 有完整记录，包括两次需求回调和缺陷修复。
- **性能可信吗？**：压测客户端是 JDK 标准库自己写的，独立干净库实例、`-Xmx512m`，原始 CSV 在 `assets/bench/`，报告第 7 章有环境、参数和瓶颈分析。
- **安全做了哪些？**：JWT、PBKDF2 加盐、SQL 参数化、纯文本渲染防 XSS、上传校验与路径清洗、限流、日志脱敏，均有自动化用例。
- **SQLite 撑得住吗？**：WAL 下 1000+ 消息/秒持续写入无锁错误；瓶颈不在数据库，而在建连高峰的全量在线列表广播。
