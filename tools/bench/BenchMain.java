package com.lanchat.bench;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

/**
 * LanChat 压测客户端（只用 JDK 标准库：HttpClient + java.net.http.WebSocket，JSON 手写）。
 *
 * <p>流程：REST 批量注册压测用户 → 并发建立 WebSocket 连接 → 每连接按 rate 发送群聊消息 →
 * 统计连接成功率、消息端到端延迟（发送→自己回显）P50/P95/P99、吞吐、错误数、客户端/后端内存，
 * 输出逐秒时间线 CSV 与汇总 CSV（assets/bench/）。</p>
 *
 * <p>用法（scripts/bench.sh 会先 javac 再执行）：</p>
 * <pre>
 * javac -d tools/bench/out tools/bench/*.java
 * java -cp tools/bench/out com.lanchat.bench.BenchMain \
 *     --url ws://localhost:8081/ws/chat --http http://localhost:8081 \
 *     --connections 500 --duration 60 --rate 1
 * </pre>
 *
 * <p>参数：--url --http --connections（1..1000）--duration（秒，1..600）--rate（每连接每秒消息数，0=只建连保活）
 * --out --tag --pid --no-rest-probe --probe-interval-ms --help。</p>
 */
public final class BenchMain {

    private static final DateTimeFormatter RUN_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int MAX_CONNECTIONS = 1000;
    /** WebSocket 接收窗口：一次性请求多个消息额度，避免逐帧 request(1) 造成接收停顿和 TCP 背压。 */
    private static final int RECEIVE_WINDOW = 256;
    private static final int MAX_DURATION_SECONDS = 600;

    private BenchMain() {
    }

    public static void main(String[] args) {
        Config config;
        try {
            config = Config.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("[bench] 参数错误: " + e.getMessage());
            printUsage();
            System.exit(2);
            return;
        }
        if (config.help) {
            printUsage();
            return;
        }
        int exitCode = 0;
        try {
            new BenchRun(config).execute();
        } catch (Throwable throwable) {
            System.err.println("[bench] 压测失败: " + throwable);
            throwable.printStackTrace();
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    private static void printUsage() {
        System.out.println("""
                LanChat 压测客户端（仅 JDK 标准库）
                用法: java -cp tools/bench/out com.lanchat.bench.BenchMain [参数]
                  --url <ws>            WebSocket 地址，默认 ws://localhost:8081/ws/chat
                  --http <base>         REST base，默认 http://localhost:8081
                  --connections <n>     并发连接数（1-1000），默认 100
                  --duration <s>        发送持续秒数（1-600），默认 30
                  --rate <r>            每连接每秒消息数（小数可用；0=只建连+ping 保活），默认 1
                  --out <path>          时间线 CSV 路径，默认 assets/bench/bench_<n>conn[_tag].csv
                  --tag <name>          本轮标签（写入文件名与汇总）
                  --pid <pid>           后端进程 pid（默认自动探测 lanchat-backend.jar）
                  --senders <n>         只让前 n 个连接发送消息（默认全部；用于定位广播瓶颈）
                  --jitter              发送相位随机抖动（避免所有连接同一毫秒齐发）
                  --settle-ms <ms>      压测结束后等待最后回显的毫秒数，默认 1500
                  --warmup-seconds <s>  建连后先等待 s 秒（排空连接风暴）再开始发送，默认 0
                  --connect-rate <n>    每秒建立 n 个连接（默认 0=全部并发；500 连接建议 20-50 避免 userList 风暴）
                  --no-rest-probe       压测期间不采样 GET /api/users
                  --probe-interval-ms   REST 采样间隔，默认 1000
                  --help                显示帮助
                汇总数据追加写入 assets/bench/bench_summary.csv""");
    }

    // ================================================================== 配置

    private static final class Config {

        private String wsUrl;
        private String httpBase = "http://localhost:8081";
        private int connections = 100;
        private int durationSeconds = 30;
        private double rate = 1.0;
        private String outPath;
        private String tag = "";
        private long backendPid = -1L;
        private boolean restProbe = true;
        private boolean jitter;
        private int senderCount = -1;
        private long settleMillis = 1_500L;
        private int warmupSeconds = 0;
        private int connectRate = 0;
        private int probeIntervalMs = 1000;
        private boolean help;

        private static Config parse(String[] args) {
            Config config = new Config();
            for (int i = 0; i < args.length; i++) {
                String raw = args[i];
                if ("--help".equals(raw) || "-h".equals(raw)) {
                    config.help = true;
                    continue;
                }
                if ("--no-rest-probe".equals(raw)) {
                    config.restProbe = false;
                    continue;
                }
                if ("--jitter".equals(raw)) {
                    config.jitter = true;
                    continue;
                }
                String key = raw;
                String value = null;
                int equalsIndex = raw.indexOf('=');
                if (raw.startsWith("--") && equalsIndex > 0) {
                    key = raw.substring(0, equalsIndex);
                    value = raw.substring(equalsIndex + 1);
                }
                if (value == null) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException(key + " 缺少参数值");
                    }
                    value = args[++i];
                }
                switch (key) {
                    case "--url" -> config.wsUrl = value.strip();
                    case "--http" -> config.httpBase = stripTrailingSlash(value.strip());
                    case "--connections" -> config.connections = parseInt(key, value);
                    case "--duration" -> config.durationSeconds = parseInt(key, value);
                    case "--rate" -> config.rate = parseDouble(key, value);
                    case "--out" -> config.outPath = value;
                    case "--tag" -> config.tag = value.replaceAll("[^A-Za-z0-9_-]", "_");
                    case "--pid" -> config.backendPid = Long.parseLong(value);
                    case "--probe-interval-ms" -> config.probeIntervalMs = parseInt(key, value);
                    case "--senders" -> config.senderCount = parseInt(key, value);
                    case "--settle-ms" -> config.settleMillis = parseInt(key, value);
                    case "--warmup-seconds" -> config.warmupSeconds = parseInt(key, value);
                    case "--connect-rate" -> config.connectRate = parseInt(key, value);
                    default -> throw new IllegalArgumentException("未知参数: " + key);
                }
            }
            if (config.connections < 1) {
                throw new IllegalArgumentException("--connections 必须 ≥ 1");
            }
            if (config.connections > MAX_CONNECTIONS) {
                System.err.println("[bench] 警告: --connections 上限 " + MAX_CONNECTIONS + "，已自动收敛");
                config.connections = MAX_CONNECTIONS;
            }
            if (config.durationSeconds < 1) {
                throw new IllegalArgumentException("--duration 必须 ≥ 1 秒");
            }
            if (config.durationSeconds > MAX_DURATION_SECONDS) {
                System.err.println("[bench] 警告: --duration 上限 " + MAX_DURATION_SECONDS + " 秒，已自动收敛");
                config.durationSeconds = MAX_DURATION_SECONDS;
            }
            if (config.rate < 0) {
                throw new IllegalArgumentException("--rate 不能为负");
            }
            if (config.wsUrl == null) {
                config.wsUrl = config.httpBase.replaceFirst("^http", "ws") + "/ws/chat";
            }
            if (config.probeIntervalMs < 100) {
                config.probeIntervalMs = 100;
            }
            if (config.senderCount <= 0 || config.senderCount > config.connections) {
                config.senderCount = config.connections;
            }
            if (config.settleMillis < 0) {
                config.settleMillis = 0;
            }
            if (config.warmupSeconds < 0) {
                config.warmupSeconds = 0;
            }
            return config;
        }

        private static String stripTrailingSlash(String value) {
            return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        }

        private static int parseInt(String key, String value) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(key + " 需要整数: " + value);
            }
        }

        private static double parseDouble(String key, String value) {
            try {
                return Double.parseDouble(value.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(key + " 需要数字: " + value);
            }
        }
    }

    // ================================================================== 计数器

    private static final class Counters {
        private final AtomicInteger connectSuccess = new AtomicInteger();
        private final AtomicInteger connectFailed = new AtomicInteger();
        private final AtomicLong messagesSent = new AtomicLong();
        private final AtomicLong sendErrors = new AtomicLong();
        private final AtomicLong framesReceived = new AtomicLong();
        private final AtomicLong ownEchoes = new AtomicLong();
        private final AtomicLong errorFrames = new AtomicLong();
        private final AtomicLong messageFrames = new AtomicLong();
        private final AtomicLong userListFrames = new AtomicLong();
        private final AtomicLong pongFrames = new AtomicLong();
        private final AtomicLong otherFrames = new AtomicLong();
        private final AtomicLong bytesReceived = new AtomicLong();
        private final AtomicLong closedConnections = new AtomicLong();
        private final AtomicLong wsErrors = new AtomicLong();
        private final AtomicLong restRequests = new AtomicLong();
        private final AtomicLong restFailures = new AtomicLong();
        private final ConcurrentLinkedQueue<Long> latenciesMicros = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<Long> restLatenciesMicros = new ConcurrentLinkedQueue<>();
        private volatile long restLastMicros = -1L;
    }

    // ================================================================== 主流程

    private static final class BenchRun {

        private final Config config;
        private final Counters counters = new Counters();
        private final List<WsClient> clients = new CopyOnWriteArrayList<>();
        private final List<String> timelineRows = new ArrayList<>();

        private final String[] tokens;
        private final long[] userIds;
        private final String userPrefix;

        private HttpClient httpClient;
        private ExecutorService httpExecutor;
        private volatile boolean running;
        private long backendPid = -1L;
        private long backendRssStartMb = -1L;
        private long backendRssEndMb = -1L;

        private BenchRun(Config config) {
            this.config = config;
            this.tokens = new String[config.connections];
            this.userIds = new long[config.connections];
            String stamp = Long.toString(System.currentTimeMillis(), 36);
            String prefix = "bench_" + stamp + "_";
            // 用户名契约 3-20 位：bench_(6) + base36 时间戳(约 8) + _(1) + 3 位序号 = 18
            this.userPrefix = prefix.length() + 3 <= 20 ? prefix : "bench_" + stamp + "_";
        }

        private void execute() throws Exception {
            printEnvironment();
            httpExecutor = Executors.newVirtualThreadPerTaskExecutor();
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .executor(httpExecutor)
                    .build();
            backendPid = config.backendPid > 0 ? config.backendPid : findBackendPid();
            backendRssStartMb = readRssMb(backendPid);

            long registerStart = System.nanoTime();
            registerUsers();
            long registerMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - registerStart);
            System.out.println("[bench] 批量注册耗时: " + registerMillis + " ms");
            long connectStart = System.nanoTime();
            connectClients();
            long connectMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - connectStart);
            int connected = clients.size();
            printConnectionSummary(connectMillis);

            if (connected == 0) {
                throw new IllegalStateException("没有任何 WebSocket 连接建立成功，终止压测");
            }

            if (config.warmupSeconds > 0) {
                System.out.println("[bench] 预热等待 " + config.warmupSeconds
                        + " s（排空连接建立阶段的全量 userList 广播积压）...");
                Thread.sleep(config.warmupSeconds * 1_000L);
            }

            long loadStart = System.nanoTime();
            runLoad();
            double actualDurationSeconds = (System.nanoTime() - loadStart) / 1_000_000_000.0;
            backendRssEndMb = readRssMb(backendPid);

            RunResult result = summarize(actualDurationSeconds);
            writeTimelineCsv();
            writeSummaryCsv(result);
            printResult(result);
            cleanup();
        }

        private void printEnvironment() {
            System.out.println("==================== LanChat 压测客户端 ====================");
            System.out.println("[bench] 时间        : " + LocalDateTime.now().format(RUN_TIME_FORMAT));
            System.out.println("[bench] 机器        : " + System.getProperty("os.name") + " "
                    + System.getProperty("os.arch") + " / CPU 核数 "
                    + Runtime.getRuntime().availableProcessors()
                    + " / JVM " + System.getProperty("java.version"));
            System.out.println("[bench] WS 地址     : " + config.wsUrl);
            System.out.println("[bench] REST 地址   : " + config.httpBase);
            System.out.println("[bench] 连接数      : " + config.connections);
            System.out.println("[bench] 每连接速率  : " + config.rate + " msg/s（目标总发送速率 "
                    + String.format(Locale.ROOT, "%.1f", config.rate * config.connections) + " msg/s）");
            System.out.println("[bench] 持续时间    : " + config.durationSeconds + " s");
            System.out.println("[bench] 建连速率    : "
                    + (config.connectRate > 0 ? config.connectRate + " 连接/s（渐进建连）" : "全部并发"));
            System.out.println("[bench] 发送连接    : " + config.senderCount + " / " + config.connections
                    + "，相位抖动: " + (config.jitter ? "开启" : "关闭"));
            System.out.println("[bench] 标签        : " + (config.tag.isEmpty() ? "(无)" : config.tag));
            System.out.println("===========================================================");
        }

        // ---------------------------------------------------------- 注册用户

        private void registerUsers() throws Exception {
            AtomicInteger success = new AtomicInteger();
            AtomicInteger failed = new AtomicInteger();
            List<String> errors = Collections.synchronizedList(new ArrayList<>());
            int threads = Math.min(32, Math.max(4, config.connections / 8));
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < config.connections; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    String username = userPrefix + String.format(Locale.ROOT, "%03d", index);
                    if (username.length() > 20) {
                        username = username.substring(0, 17) + String.format(Locale.ROOT, "%03d", index);
                    }
                    String body = "{\"username\":\"" + username + "\",\"password\":\"bench123\","
                            + "\"nickname\":\"压测" + index + "\"}";
                    try {
                        HttpResponse<String> response = postJson("/api/auth/register", body);
                        if (response.statusCode() != 200) {
                            failed.incrementAndGet();
                            errors.add(username + " -> HTTP " + response.statusCode() + " " + response.body());
                            return;
                        }
                        String token = extractString(response.body(), "token");
                        long userId = extractLongAfter(response.body(), "\"user\":{\"id\":");
                        if (token == null || token.isEmpty() || userId <= 0) {
                            failed.incrementAndGet();
                            errors.add(username + " -> 响应解析失败: " + response.body());
                            return;
                        }
                        tokens[index] = token;
                        userIds[index] = userId;
                        success.incrementAndGet();
                    } catch (Exception e) {
                        failed.incrementAndGet();
                        errors.add(username + " -> " + e.getMessage());
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
            pool.shutdownNow();
            if (failed.get() > 0) {
                errors.stream().limit(3).forEach(error -> System.err.println("[bench] 注册失败样例: " + error));
                throw new IllegalStateException("压测用户注册失败 " + failed.get() + " 个，终止压测");
            }
            System.out.println("[bench] 注册测试用户完成: " + success.get() + " 个（前缀 " + userPrefix + "）");
        }

        private HttpResponse<String> postJson(String path, String body) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.httpBase + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        // ---------------------------------------------------------- 建立连接

        private void connectClients() throws Exception {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            long intervalNanos = config.connectRate > 0
                    ? TimeUnit.SECONDS.toNanos(1) / config.connectRate
                    : 0;
            long nextStartNanos = System.nanoTime();
            for (int i = 0; i < config.connections; i++) {
                if (intervalNanos > 0) {
                    long wait = nextStartNanos - System.nanoTime();
                    if (wait > 0) {
                        LockSupport.parkNanos(wait);
                    }
                    nextStartNanos += intervalNanos;
                }
                final int index = i;
                final long userId = userIds[i];
                final String token = tokens[i];
                final WsClient client = new WsClient(index, userId, counters, config.tag);
                URI uri = URI.create(withToken(config.wsUrl, token));
                CompletableFuture<WebSocket> building = httpClient.newWebSocketBuilder()
                        .connectTimeout(Duration.ofSeconds(20))
                        .buildAsync(uri, client);
                CompletableFuture<Void> handled = building.handle((webSocket, error) -> {
                    if (error != null) {
                        counters.connectFailed.incrementAndGet();
                        client.failReason = rootMessage(error);
                        if (counters.connectFailed.get() <= 3) {
                            System.err.println("[bench] 连接失败样例(" + index + "): " + client.failReason);
                        }
                    } else {
                        client.socket = webSocket;
                        clients.add(client);
                        counters.connectSuccess.incrementAndGet();
                    }
                    return null;
                });
                futures.add(handled);
                if (intervalNanos > 0) {
                    // 渐进建连：前一个连接建立完成后再进入下一个，避免同一时刻批量建连
                    handled.get(30, TimeUnit.SECONDS);
                }
            }
            if (intervalNanos == 0) {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(180, TimeUnit.SECONDS);
            }
        }

        private void printConnectionSummary(long connectMillis) {
            int success = counters.connectSuccess.get();
            int failed = counters.connectFailed.get();
            double rate = success + failed == 0 ? 0.0 : (success * 100.0 / (success + failed));
            System.out.printf(Locale.ROOT,
                    "[bench] WS 连接: 成功 %d / 失败 %d（成功率 %.1f%%），耗时 %d ms%n",
                    success, failed, rate, connectMillis);
        }

        // ---------------------------------------------------------- 负载阶段

        private void runLoad() throws Exception {
            running = true;
            long startNanos = System.nanoTime();
            long endNanos = startNanos + TimeUnit.SECONDS.toNanos(config.durationSeconds);

            Thread probeThread = null;
            if (config.restProbe && clients.size() > 0) {
                probeThread = new Thread(this::restProbeLoop, "bench-rest-probe");
                probeThread.setDaemon(true);
                probeThread.start();
            }

            List<Thread> senderThreads = new ArrayList<>();
            for (WsClient client : clients) {
                if (client.index >= config.senderCount) {
                    continue;
                }
                Thread thread = Thread.ofVirtual()
                        .name("bench-sender-" + client.index)
                        .unstarted(() -> client.sendLoop(endNanos, config.rate, config.jitter));
                senderThreads.add(thread);
                thread.start();
            }

            long nextSampleNanos = startNanos + TimeUnit.SECONDS.toNanos(1);
            long previousSent = 0;
            long previousReceived = 0;
            long previousEchoes = 0;
            long previousErrors = 0;
            long previousBytes = 0;
            while (System.nanoTime() < endNanos) {
                long now = System.nanoTime();
                if (now < nextSampleNanos) {
                    LockSupport.parkNanos(Math.min(nextSampleNanos - now, TimeUnit.MILLISECONDS.toNanos(200)));
                    continue;
                }
                long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(now - startNanos);
                long sent = counters.messagesSent.get();
                long received = counters.framesReceived.get();
                long echoes = counters.ownEchoes.get();
                long errors = counters.errorFrames.get();
                long bytes = counters.bytesReceived.get();
                long restMicros = counters.restLastMicros;
                timelineRows.add(String.format(Locale.ROOT, "%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d",
                        elapsedSeconds,
                        clients.size(),
                        sent - previousSent,
                        received - previousReceived,
                        echoes - previousEchoes,
                        errors - previousErrors,
                        counters.closedConnections.get(),
                        counters.wsErrors.get(),
                        bytes - previousBytes,
                        counters.messageFrames.get(),
                        restMicros < 0 ? -1 : Math.round(restMicros / 1000.0),
                        usedHeapMb(),
                        readRssMb(backendPid)));
                previousSent = sent;
                previousReceived = received;
                previousEchoes = echoes;
                previousErrors = errors;
                previousBytes = bytes;
                nextSampleNanos += TimeUnit.SECONDS.toNanos(1);
                if (nextSampleNanos < now) {
                    nextSampleNanos = now + TimeUnit.SECONDS.toNanos(1);
                }
            }

            for (Thread thread : senderThreads) {
                thread.join(15_000);
            }
            running = false;
            if (probeThread != null) {
                probeThread.interrupt();
            }
            // 给最后一批回显一点落地时间
            Thread.sleep(config.settleMillis);
        }

        private void restProbeLoop() {
            String token = tokens[0];
            while (running) {
                long probeStart = System.nanoTime();
                try {
                    HttpRequest request = HttpRequest.newBuilder(
                                    URI.create(config.httpBase + "/api/users"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + token)
                            .GET()
                            .build();
                    HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                    long micros = (System.nanoTime() - probeStart) / 1_000L;
                    counters.restLastMicros = micros;
                    if (response.statusCode() == 200) {
                        counters.restRequests.incrementAndGet();
                        counters.restLatenciesMicros.add(micros);
                    } else {
                        counters.restFailures.incrementAndGet();
                    }
                } catch (Exception e) {
                    counters.restFailures.incrementAndGet();
                }
                try {
                    Thread.sleep(config.probeIntervalMs);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        // ---------------------------------------------------------- 汇总 & 输出

        private RunResult summarize(double actualDurationSeconds) {
            List<Long> latencies = new ArrayList<>(counters.latenciesMicros);
            Collections.sort(latencies);
            List<Long> restLatencies = new ArrayList<>(counters.restLatenciesMicros);
            Collections.sort(restLatencies);

            long sent = counters.messagesSent.get();
            long received = counters.framesReceived.get();
            long echoes = counters.ownEchoes.get();
            double msgPerSecond = actualDurationSeconds <= 0 ? 0 : sent / actualDurationSeconds;
            double framesPerSecond = actualDurationSeconds <= 0 ? 0 : received / actualDurationSeconds;
            int success = counters.connectSuccess.get();
            int failed = counters.connectFailed.get();
            double successRate = success + failed == 0 ? 0 : success * 100.0 / (success + failed);

            return new RunResult(
                    success, failed, successRate,
                    sent, counters.sendErrors.get(),
                    echoes, received, counters.errorFrames.get(),
                    counters.messageFrames.get(), counters.userListFrames.get(),
                    counters.pongFrames.get(), counters.otherFrames.get(),
                    counters.bytesReceived.get(),
                    counters.closedConnections.get(), counters.wsErrors.get(),
                    msgPerSecond, framesPerSecond,
                    percentile(latencies, 0.50), percentile(latencies, 0.95),
                    percentile(latencies, 0.99), percentile(latencies, 1.00),
                    counters.restRequests.get(), counters.restFailures.get(),
                    percentile(restLatencies, 0.50), percentile(restLatencies, 0.95),
                    percentile(restLatencies, 0.99),
                    usedHeapMb(), (int) (Runtime.getRuntime().maxMemory() / (1024L * 1024L)),
                    actualDurationSeconds);
        }

        private void printResult(RunResult result) {
            System.out.println();
            System.out.println("==================== LanChat 压测结果 ====================");
            System.out.printf(Locale.ROOT, "连接: 成功 %d / 失败 %d（成功率 %.1f%%）%n",
                    result.connectSuccess(), result.connectFailed(), result.connectSuccessRate());
            System.out.printf(Locale.ROOT, "负载: 持续 %.1f s，每连接 %.2f msg/s，目标总速率 %.1f msg/s%n",
                    result.actualDurationSeconds(), config.rate, config.rate * config.connections);
            System.out.printf(Locale.ROOT,
                    "消息: 发送 %d（发送失败 %d），己方回显 %d，收到帧 %d（error 帧 %d），连接中断 %d，WS 错误 %d%n",
                    result.sent(), result.sendErrors(), result.echoes(), result.framesReceived(),
                    result.errorFrames(), result.closedConnections(), result.wsErrors());
            System.out.printf(Locale.ROOT,
                    "帧分类: message %d / userList %d / pong %d / error %d / 其他 %d，接收字节 %.1f MB%n",
                    result.messageFrames(), result.userListFrames(), result.pongFrames(),
                    result.errorFrames(), result.otherFrames(),
                    result.bytesReceived() / (1024.0 * 1024.0));
            System.out.printf(Locale.ROOT,
                    "端到端延迟(ms): P50 %.2f / P95 %.2f / P99 %.2f / Max %.2f（样本 %d）%n",
                    result.p50Ms(), result.p95Ms(), result.p99Ms(), result.maxMs(), result.echoes());
            System.out.printf(Locale.ROOT, "吞吐: 发送 %.1f msg/s，广播投递 %.1f 帧/s%n",
                    result.msgPerSecond(), result.framesPerSecond());
            if (result.restRequests() + result.restFailures() > 0) {
                System.out.printf(Locale.ROOT,
                        "REST /api/users: 请求 %d（失败 %d），延迟(ms) P50 %.2f / P95 %.2f / P99 %.2f%n",
                        result.restRequests(), result.restFailures(),
                        result.restP50Ms(), result.restP95Ms(), result.restP99Ms());
            } else {
                System.out.println("REST /api/users: 未采样");
            }
            System.out.printf(Locale.ROOT,
                    "内存: 客户端堆 %.0f MB / 上限 %d MB；后端 pid=%d RSS %s→%s MB%n",
                    result.clientHeapUsedMb(), result.clientHeapMaxMb(), backendPid,
                    mbText(backendRssStartMb), mbText(backendRssEndMb));
            System.out.println("CSV : " + outputPath());
            System.out.println("汇总: " + summaryPath());
            System.out.println("=========================================================");
        }

        private void writeTimelineCsv() throws IOException {
            Path path = Path.of(outputPath());
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            List<String> lines = new ArrayList<>(timelineRows.size() + 1);
            lines.add("elapsed_s,connections,sent_delta,frames_delta,echo_delta,error_frames_delta,"
                    + "closed_connections_total,ws_errors_total,bytes_delta,message_frames_total,"
                    + "rest_last_ms,client_heap_used_mb,backend_rss_mb");
            lines.addAll(timelineRows);
            Files.write(path, lines);
            System.out.println("[bench] 已写入时间线 CSV: " + path.toAbsolutePath());
        }

        private void writeSummaryCsv(RunResult result) throws IOException {
            Path path = Path.of(summaryPath());
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            boolean newFile = !Files.exists(path);
            List<String> lines = new ArrayList<>();
            if (newFile) {
                lines.add(SUMMARY_HEADER);
            }
            lines.add(summaryRow(result));
            Files.write(path, lines,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
            System.out.println("[bench] 已追加汇总 CSV: " + path.toAbsolutePath());
        }

        private static final String SUMMARY_HEADER = "run_time,run_tag,ws_url,http_base,connections,rate_per_conn,"
                + "duration_s,actual_duration_s,connect_success,connect_failed,connect_success_rate_pct,"
                + "sent_total,send_errors,echo_total,frames_received,error_frames,closed_connections,ws_errors,"
                + "msg_per_sec,frames_per_sec,p50_ms,p95_ms,p99_ms,max_ms,"
                + "rest_requests,rest_failures,rest_p50_ms,rest_p95_ms,rest_p99_ms,"
                + "client_heap_used_mb,client_heap_max_mb,backend_pid,backend_rss_start_mb,backend_rss_end_mb,"
                + "cpu_cores,os_arch,java_version,jvm_args";

            private String summaryRow(RunResult result) {
                String jvmArgs = String.join(" ", java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
                return String.join(",",
                        LocalDateTime.now().format(RUN_TIME_FORMAT),
                        config.tag.isEmpty() ? "-" : config.tag,
                        config.wsUrl,
                        config.httpBase,
                        Integer.toString(config.connections),
                        trimNumber(config.rate),
                        Integer.toString(config.durationSeconds),
                        String.format(Locale.ROOT, "%.2f", result.actualDurationSeconds()),
                        Integer.toString(result.connectSuccess()),
                        Integer.toString(result.connectFailed()),
                        String.format(Locale.ROOT, "%.2f", result.connectSuccessRate()),
                        Long.toString(result.sent()),
                        Long.toString(result.sendErrors()),
                        Long.toString(result.echoes()),
                        Long.toString(result.framesReceived()),
                        Long.toString(result.errorFrames()),
                        Long.toString(result.closedConnections()),
                        Long.toString(result.wsErrors()),
                        String.format(Locale.ROOT, "%.2f", result.msgPerSecond()),
                        String.format(Locale.ROOT, "%.2f", result.framesPerSecond()),
                        number(result.p50Ms()), number(result.p95Ms()), number(result.p99Ms()), number(result.maxMs()),
                        Long.toString(result.restRequests()),
                        Long.toString(result.restFailures()),
                        number(result.restP50Ms()), number(result.restP95Ms()), number(result.restP99Ms()),
                        String.format(Locale.ROOT, "%.0f", result.clientHeapUsedMb()),
                        Integer.toString(result.clientHeapMaxMb()),
                        Long.toString(backendPid),
                        mbText(backendRssStartMb),
                        mbText(backendRssEndMb),
                        Integer.toString(Runtime.getRuntime().availableProcessors()),
                        System.getProperty("os.arch"),
                        System.getProperty("java.version"),
                        "\"" + jvmArgs.replace("\"", "'") + "\"");
            }

        private String outputPath() {
            if (config.outPath != null) {
                return config.outPath;
            }
            String tagPart = config.tag.isEmpty() ? "" : "_" + config.tag;
            return "assets/bench/bench_" + config.connections + "conn" + tagPart + ".csv";
        }

        private static String summaryPath() {
            return "assets/bench/bench_summary.csv";
        }

        private void cleanup() {
            for (WsClient client : clients) {
                WebSocket webSocket = client.socket;
                if (webSocket == null) {
                    continue;
                }
                try {
                    if (!webSocket.isOutputClosed()) {
                        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bench done");
                    }
                } catch (Exception ignored) {
                    // 压测结束清理阶段忽略异常
                }
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (httpExecutor != null) {
                httpExecutor.shutdownNow();
            }
        }
    }

    // ================================================================== WebSocket 客户端

    private static final class WsClient implements WebSocket.Listener {

        private final int index;
        private final long userId;
        private final Counters counters;
        private final String tag;
        private final ArrayDeque<Long> pendingSendNanos = new ArrayDeque<>();
        private final StringBuilder partial = new StringBuilder();

        private volatile WebSocket socket;
        private volatile String failReason;

        private WsClient(int index, long userId, Counters counters, String tag) {
            this.index = index;
            this.userId = userId;
            this.counters = counters;
            this.tag = tag;
        }

        // ---------------------------------------------------------- 发送

        private void sendLoop(long endNanos, double rate, boolean jitter) {
            WebSocket webSocket = waitForSocket(endNanos);
            if (webSocket == null) {
                return;
            }
            if (rate <= 0) {
                sendKeepaliveLoop(webSocket, endNanos);
                return;
            }
            long intervalNanos = Math.max(1L, (long) (1_000_000_000L / rate));
            long nextSendNanos = System.nanoTime();
            if (jitter) {
                // 随机相位：把首个发送时间打散到 [0, interval) 内，避免 N 个连接同一毫秒齐发
                long offset = java.util.concurrent.ThreadLocalRandom.current().nextLong(intervalNanos);
                nextSendNanos += offset - intervalNanos;
            }
            long sequence = 0;
            while (System.nanoTime() < endNanos) {
                nextSendNanos += intervalNanos;
                long delay = nextSendNanos - System.nanoTime();
                if (delay > 0) {
                    LockSupport.parkNanos(delay);
                }
                if (System.nanoTime() >= endNanos) {
                    break;
                }
                if (webSocket.isOutputClosed()) {
                    break;
                }
                String payload = "{\"type\":\"chat\",\"to\":null,\"content\":\"bench-"
                        + (tag.isEmpty() ? "run" : tag) + "-" + index + "-" + sequence + "\"}";
                long sentAtNanos = System.nanoTime();
                synchronized (pendingSendNanos) {
                    pendingSendNanos.addLast(sentAtNanos);
                }
                counters.messagesSent.incrementAndGet();
                webSocket.sendText(payload, true).whenComplete((ignored, error) -> {
                    if (error != null) {
                        counters.sendErrors.incrementAndGet();
                    }
                });
                sequence++;
            }
        }

        private void sendKeepaliveLoop(WebSocket webSocket, long endNanos) {
            while (System.nanoTime() < endNanos && !webSocket.isOutputClosed()) {
                webSocket.sendText("{\"type\":\"ping\"}", true);
                LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));
            }
        }

        private WebSocket waitForSocket(long endNanos) {
            while (System.nanoTime() < endNanos) {
                WebSocket current = socket;
                if (current != null) {
                    return current;
                }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            }
            return socket;
        }

        // ---------------------------------------------------------- 接收

        @Override
        public void onOpen(WebSocket webSocket) {
            this.socket = webSocket;
            webSocket.request(RECEIVE_WINDOW);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (data != null && data.length() > 0) {
                partial.append(data);
            }
            if (last) {
                String text = partial.toString();
                partial.setLength(0);
                handleFrame(text);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            counters.closedConnections.incrementAndGet();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            counters.wsErrors.incrementAndGet();
        }

        private void handleFrame(String text) {
            counters.framesReceived.incrementAndGet();
            counters.bytesReceived.addAndGet(text.length());
            if (text.contains("\"type\":\"error\"")) {
                counters.errorFrames.incrementAndGet();
                return;
            }
            if (text.contains("\"type\":\"userList\"")) {
                counters.userListFrames.incrementAndGet();
                return;
            }
            if (text.contains("\"type\":\"pong\"")) {
                counters.pongFrames.incrementAndGet();
                return;
            }
            if (!text.contains("\"type\":\"message\"")) {
                counters.otherFrames.incrementAndGet();
                return;
            }
            counters.messageFrames.incrementAndGet();
            int senderMarker = text.indexOf("\"sender\":{\"id\":");
            if (senderMarker < 0) {
                return;
            }
            long senderId = parseLongAt(text, senderMarker + 15);
            if (senderId != userId) {
                return;
            }
            Long sentAtNanos = pollPending();
            if (sentAtNanos != null) {
                counters.ownEchoes.incrementAndGet();
                counters.latenciesMicros.add((System.nanoTime() - sentAtNanos) / 1_000L);
            }
        }

        private Long pollPending() {
            synchronized (pendingSendNanos) {
                return pendingSendNanos.pollFirst();
            }
        }
    }

    // ================================================================== 结果与工具

    private record RunResult(int connectSuccess,
                             int connectFailed,
                             double connectSuccessRate,
                             long sent,
                             long sendErrors,
                             long echoes,
                             long framesReceived,
                             long errorFrames,
                             long messageFrames,
                             long userListFrames,
                             long pongFrames,
                             long otherFrames,
                             long bytesReceived,
                             long closedConnections,
                             long wsErrors,
                             double msgPerSecond,
                             double framesPerSecond,
                             double p50Ms,
                             double p95Ms,
                             double p99Ms,
                             double maxMs,
                             long restRequests,
                             long restFailures,
                             double restP50Ms,
                             double restP95Ms,
                             double restP99Ms,
                             double clientHeapUsedMb,
                             int clientHeapMaxMb,
                             double actualDurationSeconds) {
    }

    private static double percentile(List<Long> sortedMicros, double percentile) {
        if (sortedMicros.isEmpty()) {
            return -1;
        }
        int index = (int) Math.ceil(percentile * sortedMicros.size()) - 1;
        index = Math.max(0, Math.min(sortedMicros.size() - 1, index));
        return sortedMicros.get(index) / 1_000.0;
    }

    private static long parseLongAt(String text, int start) {
        int index = start;
        while (index < text.length() && !Character.isDigit(text.charAt(index))) {
            index++;
        }
        long value = 0;
        boolean found = false;
        while (index < text.length() && Character.isDigit(text.charAt(index))) {
            value = value * 10 + (text.charAt(index) - '0');
            index++;
            found = true;
        }
        return found ? value : -1;
    }

    private static String extractString(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            return null;
        }
        start += marker.length();
        int end = json.indexOf('"', start);
        return end < 0 ? null : json.substring(start, end);
    }

    private static long extractLongAfter(String json, String marker) {
        int index = json.indexOf(marker);
        return index < 0 ? -1 : parseLongAt(json, index + marker.length());
    }

    private static String withToken(String wsUrl, String token) {
        String separator = wsUrl.contains("?") ? "&" : "?";
        return wsUrl + separator + "token=" + token;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }

    private static long usedHeapMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
    }

    private static long findBackendPid() {
        Path proc = Path.of("/proc");
        if (!Files.isDirectory(proc)) {
            return -1;
        }
        long self = ProcessHandle.current().pid();
        try (Stream<Path> entries = Files.list(proc)) {
            return entries
                    .filter(path -> path.getFileName().toString().chars().allMatch(Character::isDigit))
                    .map(path -> {
                        try {
                            return Long.parseLong(path.getFileName().toString());
                        } catch (NumberFormatException e) {
                            return -1L;
                        }
                    })
                    .filter(pid -> pid > 0 && pid != self)
                    .filter(BenchMain::looksLikeLanChatProcess)
                    .findFirst()
                    .orElse(-1L);
        } catch (IOException e) {
            return -1L;
        }
    }

    private static boolean looksLikeLanChatProcess(long pid) {
        try {
            String cmdline = Files.readString(Path.of("/proc/" + pid + "/cmdline"))
                    .replace('\0', ' ');
            return cmdline.contains("lanchat-backend.jar") || cmdline.contains("LanChatApplication");
        } catch (Exception e) {
            return false;
        }
    }

    private static long readRssMb(long pid) {
        if (pid <= 0) {
            return -1;
        }
        try {
            for (String line : Files.readAllLines(Path.of("/proc/" + pid + "/status"))) {
                if (line.startsWith("VmRSS:")) {
                    String value = line.substring("VmRSS:".length()).trim().split("\\s+")[0];
                    return Long.parseLong(value) / 1024L;
                }
            }
        } catch (Exception ignored) {
            // 进程结束时读取失败
        }
        return -1;
    }

    private static String mbText(long mb) {
        return mb < 0 ? "?" : Long.toString(mb);
    }

    private static String number(double value) {
        return value < 0 ? "" : String.format(Locale.ROOT, "%.2f", value);
    }

    private static String trimNumber(double value) {
        return value == Math.rint(value)
                ? Long.toString((long) value)
                : String.format(Locale.ROOT, "%.3f", value);
    }

}
