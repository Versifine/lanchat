# LanChat 压测数据（assets/bench）

> 原始数据目录，供课程报告「性能测试与压测」章节直接引用。工具与复现脚本见 [`tools/bench/`](../../tools/bench)。
> 本目录数据采集于 2026-11-18，验收用例对应 [`docs/eval-plan.md`](../../docs/eval-plan.md) 的 EV-PERF-01～07。

## 1. 测试环境

| 项 | 值 |
|---|---|
| 机器 | Linux（Fedora，kernel 7.1.5）16 核 / 30 GB 内存 |
| 运行时 | OpenJDK 21.0.13 (Temurin)，Maven 3.9.16 |
| 服务端 A（8081） | 交付时 Lead 启动的正式实例：`java -jar backend/target/lanchat-backend.jar`（默认堆，未显式 -Xmx） |
| 服务端 B（8082） | 压测用临时实例：最新构建 `-Xmx512m`，空库 `/tmp/qa-bench/lanchat.db`，用于排除 8081 库被压测用户污染的影响 |
| 压测客户端 | 同机 JDK 21 进程（JDK HttpClient + java.net.http.WebSocket，仅标准库），与服务器共享 16 核 |
| 后台负载 | 采集时 load average ≈ 3～5（桌面/浏览器等进程），非完全空闲机器 |

> **数据可信度说明**：8081 的 SQLite 库在压测过程中被注册了数千个压测用户（最终 4533 个），
> 而服务端每次 WS 建连都会广播**全量** `userList`，帧体随用户总数线性增长（4533 用户时约 369 KB/帧），
> 全并发建连时产生 O(N²) 广播风暴，导致 8081 上多轮 500 连接数据不可用（`bench_summary.csv` 中 11:27–11:35 的
> `main500`、`diag_*`、`perf01_500` 行仅作过程记录）。**最终有效数据使用 8082 干净实例 + 渐进建连**，
> 见 `perf02_*` / `perf03_*` 行。正式演示前建议由 Lead 清空 `data/lanchat.db` 后重新执行本目录复现命令。

## 2. 文件清单

| 文件 | 内容 |
|---|---|
| `bench_summary.csv` | 所有压测运行的汇总（每次运行追加一行）：环境、连接数、rate、时长、连接成功率、发送/接收帧数、P50/P95/P99/Max、REST 延迟、内存、JVM 参数等。**最后 3 行为最终有效数据**。 |
| `bench_100conn_perf02_100x100.csv` | 100 连接 × 100 发送者 × 1 msg/s × 20 s 的逐秒时间线（对照基线，全部成功）。 |
| `bench_500conn_perf02_500full2.csv` | **EV-PERF-02/03 主数据**：500 连接、500 发送者、1 msg/s、30 s、渐进建连 25 连接/s、`-Xmx512m`。 |
| `bench_500conn_perf03_500x2.csv` | **EV-PERF-03 吞吐数据**：500 连接、每连接 2.3 msg/s（≈1067 msg/s）、25 s、渐进建连、`-Xmx512m`。 |
| `bench_500conn_perf01_500.csv` | 8081 上 500 连接「只建连 + 保活 60 s」的时间线：连接成功率 100%，用于观察建连/断开广播风暴的排空过程。 |
| `bench_500conn_perf02_500.csv` | 反例：干净实例但**500 连接全并发建立**（未 ramp），触发 userList 广播风暴 → 发送超时踢连接（close 4500/1011），延迟 P50 4 s。 |
| `bench_500conn_perf02_500full.csv` | 反例：干净实例、全并发建连 + 500 发送者，307/500 连接被踢、消息帧投递为 0。 |
| `bench_100conn_retest_default.csv` | 反例：100 连接全并发建立（未 ramp）同样出现 close 1011 踢连接，说明瓶颈与连接数无关、与建连突发有关。 |
| `backend_memory_500conn.csv` | 8081 实例在压测期间的 RSS 时间线（默认堆；受污染库放大，RSS 峰值约 1.8 GB）。 |
| `backend_memory_500conn_clean.csv` | 8082 `-Xmx512m` 实例在 500 连接主压测期间 RSS / JVM heap 时间线（heap used 最高约 200 MB）。 |
| `file_upload.csv` | 20 MB 文件上传 5 轮吞吐 + 21 MB 超限 413/1006 校验（EV-PERF-05 / EV-SEC-06）。 |
| `polling_vs_ws.csv` | 3 s 轮询 vs WebSocket 推送对比（EV-PERF-04）：延迟、请求数、字节数。 |

文件名中的 `perf02_*` / `perf03_*` / `perf01_*` 与 `docs/eval-plan.md` 的 EV-PERF 编号对应。

## 3. 关键结果（最终有效数据）

### 3.1 500 连接群聊延迟（EV-PERF-02）

条件：500 连接（渐进 25 连接/s）、500 发送者、每连接 1 msg/s、发送 30 s、`-Xmx512m`、干净库。

| 指标 | 实测 | 目标 | 结论 |
|---|---|---|---|
| 连接成功率 | 500/500 = **100%** | 100% | ✅ |
| 消息发送 | 14 999 条（461.5 msg/s） | — | — |
| 消息投递 | 7 499 500 帧 = 发送数 × 500，**100% 投递** | — | ✅ |
| 端到端延迟 P50 | **10.10 ms** | — | — |
| 端到端延迟 P95 | **21.40 ms** | ≤200 ms | ✅ |
| 端到端延迟 P99 | **31.23 ms** | ≤500 ms | ✅ |
| 端到端延迟 Max | 176.84 ms | — | 长尾 |
| 广播吞吐 | 234 610 帧/s | — | — |
| 错误/踢连接 | 0 / 0 | — | ✅ |
| REST `/api/users` | P50 18.33 / P95 **39.88** / P99 44.25 ms | P95 ≤100 ms | ✅（EV-PERF-06） |
| 服务端 heap used | ≈171 MB / `-Xmx512m`；RSS 554 MB | 不 OOM | ✅（EV-PERF-07） |

### 3.2 500 连接广播吞吐（EV-PERF-03）

条件：500 连接、每连接 2.3 msg/s（实测 **1 066.75 msg/s**）、25 s、`-Xmx512m`、干净库。

| 指标 | 实测 | 目标 | 结论 |
|---|---|---|---|
| 消息吞吐 | **1 066.75 msg/s** | ≥1000 msg/s | ✅ |
| 广播投递 | 14 487 753 帧（=28 725×500，**100% 投递**），538 025 帧/s | — | ✅ |
| 端到端延迟 P50/P95/P99 | 1713 / 2394 / 2613 ms | — | ❌ 该运行点延迟不达标 |
| REST P95 | 920.82 ms | ≤100 ms | ❌ 该运行点不达标 |
| 错误/踢连接 | 0 / 0 | — | ✅ |

> 结论：**吞吐与延迟两个目标不在同一运行点达成**。1 msg/s/连接时延迟达标、吞吐 461 msg/s；
> 2.3 msg/s/连接时吞吐达标（1067 msg/s、100% 投递），但延迟/REST 恶化。
> 客户端与服务端同机共享 16 核，压测客户端单个 JVM 需每秒解析/处理 53.8 万入站帧，
> 客户端侧饱和是延迟升高的重要原因（服务端 heap 仅 218 MB，未出现 OOM/踢连接）。

### 3.3 100 连接对照基线

| 指标 | 实测 |
|---|---|
| 连接成功率 / 踢连接 | 100% / 0 |
| 消息投递 | 200 000 帧 = 2000×100，100% |
| 延迟 P50 / P95 / P99 / Max | 1.53 / **3.07** / 4.13 / 17.32 ms |
| 广播吞吐 | 9 134 帧/s |
| REST P95 | 6.45 ms |

### 3.4 轮询 vs WebSocket（EV-PERF-04，详见 polling_vs_ws.csv）

| 场景 | 消息数 | 平均延迟 | P95 | 最大延迟 | HTTP 请求 | 接收字节 |
|---|---|---|---|---|---|---|
| WebSocket 推送 | 31 | 34.46 ms | 327.37 ms* | 416.07 ms | 0 次（仅 2 次 WS 握手） | 6.2 KB |
| 3 s REST 轮询 | 27 | 2005.15 ms | 3006.03 ms | 3006.88 ms | 9 次 / 30 s（18 次/分钟） | 212.7 KB |

\* WebSocket P95 采样时后台有其它进程负载；空载单连接实测 P50 ≈ 1.5–10 ms。
结论：**WS 延迟低约 9 倍（P95）/ 58 倍（平均），请求数少 100%，接收字节约为轮询的 1/34**（每消息 199.7 B vs 7 877.8 B）。

### 3.5 文件上传吞吐（EV-PERF-05）

20 MB 单文件、5 轮 multipart 上传：145.16 / 160.77 / 166.87 / 163.28 / 164.98 MB/s，
**平均 160.21 MB/s**（目标 ≥20 MB/s，✅）；SHA-256 全部一致；21 MB 上传 → HTTP 413 / code 1006 ✅。

### 3.6 EV-PERF 验收对照

| 编号 | 指标 | 结论 |
|---|---|---|
| EV-PERF-01 | 100/500/1000 并发连接成功率 100% | **部分达标**：100、500 均 100%；1000 未测（时间预算 + 全量 userList 的 O(N²) 建连风暴） |
| EV-PERF-02 | 500 连接、1 msg/s，P95 ≤200 / P99 ≤500 ms | ✅ P95 21.4 / P99 31.2 ms |
| EV-PERF-03 | ≥1000 msg/s | ✅ 1066.75 msg/s（代价：该运行点延迟 P95 2.4 s） |
| EV-PERF-04 | 轮询 vs WS 延迟/请求数 | ✅ WS 显著更低（见 3.4） |
| EV-PERF-05 | 20 MB 上传 ≥20 MB/s | ✅ 160.21 MB/s |
| EV-PERF-06 | 500 连接下 REST P95 ≤100 ms | ✅ 39.88 ms（1 msg/s 运行点）；高吞吐运行点 920.82 ms ❌ |
| EV-PERF-07 | 500 连接 `-Xmx512m` 不 OOM | ✅ 渐进建连下 heap used ≈171–218 MB、0 踢连接；全并发建连时因发送超时踢连接而非 OOM |

## 4. 瓶颈观察与建议

1. **全量 `userList` 广播是 O(N²) 建连风暴（首要瓶颈）**
   契约要求「握手成功 → 广播新的 userList」，当前实现每次都序列化并广播**全部注册用户**。
   500 个连接全并发建立时，广播总量 ≈ Σk·(k 用户列表) ≈ 3.8–4.4 GB（干净库）/ 数十 GB（4533 用户库），
   服务端每会话 256 KB 发送缓冲 + 5 s 发送超时（`ConcurrentWebSocketSessionDecorator`）被撑爆，
   出现大量 `close 4500 / 1011` 踢连接、消息帧投递为 0（见 `bench_500conn_perf02_500full.csv`）。
   **渐进建连（25 连接/s）后同样的 500 连接完全正常**（`bench_500conn_perf02_500full2.csv`）。
   *建议*：改为增量帧（userJoined/userLeft）或对 userList 做 100–200 ms 合并/节流、按需全量拉取；
   也可在网关层限制建连速率。报告可将此作为「实时方案在人数多时的开销」分析素材。
2. **广播 O(N) 扇出与高吞吐下的客户端/服务端容量**
   每条群聊消息要顺序向所有在线会话 `sendMessage`，500 连接即 500 次发送/条。
   1 msg/s/连接（≈46 万帧/s 目标）实测稳定（P95 21 ms）；2.3 msg/s/连接（≈53.8 万帧/s 投递）
   服务端仍 100% 投递并 0 错误，但同机压测客户端单 JVM 处理 53.8 万帧/s 时出现 1.7 s 级排队。
   *建议*：广播改为线程池异步扇出 / 每连接有界队列（满了丢弃或合并 userList），避免广播线程被慢连接拖住；
   高并发数据建议客户端多进程分布到多机测量，区分客户端瓶颈。
3. **延迟长尾**：主数据 Max 176.84 ms（P99 仅 31 ms），来自同机 GC/调度毛刺；
   100 连接时 P99 4.13 ms。报告可注明「同机压测、后台负载 load≈3–5」。
4. **SQLite 写锁未见瓶颈**：WAL + busy_timeout 下，1067 msg/s 持续写入无 SQLITE_BUSY/错误，
   `-Xmx512m` 下 heap 稳定；单写者上限需在更长时间/更高写入下继续压（当前不是瓶颈）。
5. **内存**：`-Xmx512m` 可支撑 500 连接（渐进建连）主运行；峰值 heap used ≈200 MB。
   但全并发建连时排队帧会让 RSS/堆快速膨胀（8081 污染库实测 RSS 峰值约 1.8–4 GB），
   与 userList 风暴是同一问题，清理数据库并限制建连速率后消失。
6. **测试数据污染放大效应**：每轮压测注册 500 个用户且不清库，`userList` 帧从 ~35 KB 涨到 ~369 KB，
   是全并发建连失败的主要「放大器」；正式演示前需清库（Lead 负责）。

## 5. 复现命令

```bash
cd /home/versi/Projects/Java
source scripts/env.sh

# 1) 编译压测客户端（仅 JDK 标准库，无第三方依赖）
javac -encoding UTF-8 -d tools/bench/out tools/bench/*.java

# 2) 干净临时实例（8082，-Xmx512m，数据在 /tmp/qa-bench）
tools/bench/temp_backend.sh start 8082 -Xmx512m

# 3) 500 连接群聊延迟（EV-PERF-02，渐进建连 25/s；约 90 s）
java -cp tools/bench/out com.lanchat.bench.BenchMain \
  --http http://localhost:8082 --connections 500 --duration 30 --rate 1 \
  --senders 500 --connect-rate 25 --jitter --warmup-seconds 10 --tag perf02_500

# 4) 500 连接吞吐（EV-PERF-03，约 1067 msg/s；约 90 s）
java -cp tools/bench/out com.lanchat.bench.BenchMain \
  --http http://localhost:8082 --connections 500 --duration 25 --rate 2.3 \
  --senders 500 --connect-rate 25 --jitter --warmup-seconds 10 --tag perf03_500x2

# 5) 轮询 vs WS（约 70 s）
tools/bench/polling_vs_ws.sh --duration 30

# 6) 20 MB 文件上传吞吐（数秒）
tools/bench/file_upload_bench.sh

# 7) 清理临时实例与 /tmp 数据
tools/bench/temp_backend.sh stop 8082
```

> `BenchMain` 参数含义见 `--help`；`--connect-rate` 是 500 连接稳定性的关键（避免全并发建连触发 userList 风暴）。
> 汇总数据统一追加到 `assets/bench/bench_summary.csv`，单次时间线 CSV 由 `--out`/默认 `bench_<n>conn[_tag].csv` 指定。

## 6. 局限

- 压测客户端与服务端**同机**，16 核被两者共享且后台有桌面进程负载，数据偏保守。
- 总压测时间受任务约束控制在 10 分钟量级，未做「500 连接稳定 10 分钟」长稳；50–60 s 短跑内连接零中断。
- 1000 连接仅完成协议设计验证（O(N²) userList 风暴下不具备一次性建连条件），未采集正式数据。
- 轮询对比中 WS 场景 P95 受影响于后台负载，绝对值按同文件内两场景对比使用。
