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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * 轮询 vs WebSocket 实时方案对比实验（EV-PERF-04）。
 *
 * <p>同一台机器上注册「发送者」与「观察者」两个用户，发送者每隔 {@code --send-interval-ms}
 * 发一条群聊消息，持续 {@code --duration} 秒，两种接收方式分别测量：</p>
 * <ol>
 *   <li><b>WebSocket</b>：观察者建立 WS，统计消息推送到达的端到端延迟、收到的字节数、HTTP 请求数（仅握手 1 次）；</li>
 *   <li><b>3s 轮询</b>：观察者不建 WS，每 3s 调一次 {@code GET /api/messages/group?limit=200}，
 *       通过消息内容里的序号识别新消息并计算延迟，统计请求数、响应字节数。</li>
 * </ol>
 *
 * <p>结果写入 assets/bench/polling_vs_ws.csv，并在控制台打印对比表。仅使用 JDK 标准库。</p>
 */
public final class PollingVsWsMain {

    private static final String RUN_TAG = "pollws";
    private static final long CONNECT_TIMEOUT_SECONDS = 15;

    private PollingVsWsMain() {
    }

    public static void main(String[] args) {
        Options options = Options.parse(args);
        if (options.help) {
            System.out.println("""
                    轮询 vs WebSocket 对比实验（仅 JDK 标准库）
                    用法: java -cp tools/bench/out com.lanchat.bench.PollingVsWsMain [参数]
                      --http <base>              REST base，默认 http://localhost:8081
                      --duration <s>             每个场景持续秒数，默认 30
                      --send-interval-ms <ms>    发送间隔，默认 1000
                      --poll-interval-ms <ms>    轮询间隔，默认 3000
                      --out <path>               CSV 输出，默认 assets/bench/polling_vs_ws.csv
                      --help                     帮助""");
            return;
        }
        try {
            new Experiment(options).run();
        } catch (Throwable throwable) {
            System.err.println("[pollws] 实验失败: " + throwable);
            throwable.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    // ================================================================== 参数

    private static final class Options {
        private String httpBase = "http://localhost:8081";
        private int durationSeconds = 30;
        private int sendIntervalMs = 1000;
        private int pollIntervalMs = 3000;
        private String outPath = "assets/bench/polling_vs_ws.csv";
        private boolean help;

        private static Options parse(String[] args) {
            Options options = new Options();
            for (int i = 0; i < args.length; i++) {
                String raw = args[i];
                if ("--help".equals(raw) || "-h".equals(raw)) {
                    options.help = true;
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
                    case "--http" -> options.httpBase = stripTrailingSlash(value.trim());
                    case "--duration" -> options.durationSeconds = Integer.parseInt(value.trim());
                    case "--send-interval-ms" -> options.sendIntervalMs = Integer.parseInt(value.trim());
                    case "--poll-interval-ms" -> options.pollIntervalMs = Integer.parseInt(value.trim());
                    case "--out" -> options.outPath = value;
                    default -> throw new IllegalArgumentException("未知参数: " + key);
                }
            }
            return options;
        }

        private static String stripTrailingSlash(String value) {
            return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        }
    }

    // ================================================================== 实验

    private static final class Experiment {

        private final Options options;
        private final HttpClient httpClient;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final long tagSuffix = System.currentTimeMillis();
        private final String wsTag = RUN_TAG + "-ws-" + tagSuffix;
        private final String pollTag = RUN_TAG + "-poll-" + tagSuffix;

        private String senderToken;
        private String observerToken;
        private long senderId;
        private long observerId;

        private Experiment(Options options) {
            this.options = options;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .executor(executor)
                    .build();
        }

        private void run() throws Exception {
            System.out.println("==================== 轮询 vs WebSocket 对比实验 ====================");
            System.out.println("[pollws] 目标      : " + options.httpBase);
            System.out.println("[pollws] 每场景时长: " + options.durationSeconds + " s，发送间隔 "
                    + options.sendIntervalMs + " ms，轮询间隔 " + options.pollIntervalMs + " ms");
            registerUsers();

            ScenarioResult wsResult = runWebSocketScenario();
            ScenarioResult pollResult = runPollingScenario();

            writeCsv(wsResult, pollResult);
            printComparison(wsResult, pollResult);
            executor.shutdownNow();
        }

        private void registerUsers() throws Exception {
            String stamp = Long.toString(System.currentTimeMillis(), 36);
            String senderUsername = "pwsnd_" + stamp;
            String observerUsername = "pwobs_" + stamp;
            if (senderUsername.length() > 20) {
                senderUsername = senderUsername.substring(0, 20);
            }
            if (observerUsername.length() > 20) {
                observerUsername = observerUsername.substring(0, 20);
            }
            Registration sender = register(senderUsername);
            Registration observer = register(observerUsername);
            senderToken = sender.token();
            senderId = sender.userId();
            observerToken = observer.token();
            observerId = observer.userId();
            System.out.println("[pollws] 测试用户: sender=" + senderId + ", observer=" + observerId);
        }

        private Registration register(String username) throws Exception {
            String body = "{\"username\":\"" + username + "\",\"password\":\"bench123\",\"nickname\":\"对比实验\"}";
            HttpRequest request = HttpRequest.newBuilder(URI.create(options.httpBase + "/api/auth/register"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("注册失败: HTTP " + response.statusCode() + " " + response.body());
            }
            String token = extractString(response.body(), "token");
            long userId = extractLongAfter(response.body(), "\"user\":{\"id\":");
            if (token == null || userId <= 0) {
                throw new IllegalStateException("注册响应解析失败: " + response.body());
            }
            return new Registration(userId, token);
        }

        // ---------------------------------------------------------- WebSocket 场景

        private ScenarioResult runWebSocketScenario() throws Exception {
            FrameQueue observerQueue = new FrameQueue();
            WebSocket observerSocket = connectWebSocket(observerToken, observerQueue);
            WebSocket senderSocket = connectWebSocket(senderToken, new FrameQueue());
            List<Long> latenciesMicros = new ArrayList<>();
            AtomicLong bytesReceived = new AtomicLong();
            AtomicLong messages = new AtomicLong();
            long startNanos = System.nanoTime();
            long deadline = startNanos + TimeUnit.SECONDS.toNanos(options.durationSeconds);

            int sequence = 0;
            long nextSend = startNanos;
            try {
                while (System.nanoTime() < deadline) {
                    long wait = nextSend - System.nanoTime();
                    if (wait > 0) {
                        LockSupport.parkNanos(wait);
                    }
                    String content = wsTag + "--" + sequence;
                    long sentAtNanos = System.nanoTime();
                    senderSocket.sendText(chatFrame(content), true).get(5, TimeUnit.SECONDS);
                    String frame = observerQueue.awaitContent(content, 10_000);
                    long latencyMicros = (System.nanoTime() - sentAtNanos) / 1_000L;
                    latenciesMicros.add(latencyMicros);
                    bytesReceived.addAndGet(frame.getBytes(StandardCharsets.UTF_8).length);
                    messages.incrementAndGet();
                    sequence++;
                    nextSend += TimeUnit.MILLISECONDS.toNanos(options.sendIntervalMs);
                    if (nextSend < System.nanoTime()) {
                        nextSend = System.nanoTime();
                    }
                }
            } finally {
                closeQuietly(senderSocket);
                closeQuietly(observerSocket);
            }
            return new ScenarioResult("WebSocket 推送", options.durationSeconds, messages.get(), latenciesMicros,
                    0, 2, bytesReceived.get(), 1, "观察者 1 次 WS 握手后服务端主动推送");
        }

        // ---------------------------------------------------------- 轮询场景

        private ScenarioResult runPollingScenario() throws Exception {
            WebSocket senderSocket = connectWebSocket(senderToken, new FrameQueue());
            List<Long> latenciesMicros = Collections.synchronizedList(new ArrayList<>());
            AtomicLong bytesReceived = new AtomicLong();
            AtomicLong requests = new AtomicLong();
            AtomicLong messages = new AtomicLong();
            AtomicBoolean running = new AtomicBoolean(true);
            ConcurrentHashMap<Integer, Long> sendTimes = new ConcurrentHashMap<>();
            Set<Integer> detected = ConcurrentHashMap.newKeySet();
            long startNanos = System.nanoTime();
            long deadline = startNanos + TimeUnit.SECONDS.toNanos(options.durationSeconds);

            // 发送线程：与 WS 场景相同的发送节奏
            Thread senderThread = Thread.ofVirtual().name("pollws-sender").start(() -> {
                int sequence = 0;
                long nextSend = startNanos;
                try {
                    while (running.get() && System.nanoTime() < deadline) {
                        long wait = nextSend - System.nanoTime();
                        if (wait > 0) {
                            LockSupport.parkNanos(wait);
                        }
                        if (System.nanoTime() >= deadline) {
                            break;
                        }
                        String content = pollTag + "--" + sequence;
                        long sentAtNanos = System.nanoTime();
                        sendTimes.put(sequence, sentAtNanos);
                        senderSocket.sendText(chatFrame(content), true).get(5, TimeUnit.SECONDS);
                        sequence++;
                        nextSend += TimeUnit.MILLISECONDS.toNanos(options.sendIntervalMs);
                    }
                } catch (Exception e) {
                    System.err.println("[pollws] 轮询场景发送异常: " + e.getMessage());
                }
            });

            // 轮询线程：每 pollIntervalMs 拉一次群聊历史
            long nextPoll = startNanos + TimeUnit.MILLISECONDS.toNanos(options.pollIntervalMs);
            while (System.nanoTime() < deadline) {
                long now = System.nanoTime();
                if (now < nextPoll) {
                    LockSupport.parkNanos(Math.min(nextPoll - now, TimeUnit.MILLISECONDS.toNanos(200)));
                    continue;
                }
                Long detectedAt = null;
                try {
                    HttpRequest request = HttpRequest.newBuilder(
                                    URI.create(options.httpBase + "/api/messages/group?limit=200"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + observerToken)
                            .GET()
                            .build();
                    HttpResponse<String> response = httpClient.send(request,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    detectedAt = System.nanoTime();
                    requests.incrementAndGet();
                    if (response.statusCode() == 200) {
                        String body = response.body();
                        bytesReceived.addAndGet(body.getBytes(StandardCharsets.UTF_8).length);
                        int lastSequence = maxSequenceInHistory(body, pollTag);
                        for (int seq = lastSequence; seq >= 0; seq--) {
                            if (detected.contains(seq)) {
                                continue;
                            }
                            if (!body.contains("\"content\":\"" + pollTag + "--" + seq + "\"")) {
                                continue;
                            }
                            detected.add(seq);
                            Long sentAtNanos = sendTimes.get(seq);
                            if (sentAtNanos != null) {
                                latenciesMicros.add((detectedAt - sentAtNanos) / 1_000L);
                                messages.incrementAndGet();
                            }
                        }
                    }
                } catch (Exception e) {
                    requests.incrementAndGet();
                    System.err.println("[pollws] 轮询请求异常: " + e.getMessage());
                }
                nextPoll += TimeUnit.MILLISECONDS.toNanos(options.pollIntervalMs);
                if (nextPoll < System.nanoTime()) {
                    nextPoll = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(options.pollIntervalMs);
                }
            }
            running.set(false);
            senderThread.join(10_000);
            closeQuietly(senderSocket);
            return new ScenarioResult("3s REST 轮询", options.durationSeconds, messages.get(),
                    new ArrayList<>(latenciesMicros), requests.get(), 1, bytesReceived.get(),
                    Math.max(1, options.durationSeconds * 1000 / options.pollIntervalMs),
                    "观察者每 " + options.pollIntervalMs + " ms 拉取 /api/messages/group?limit=200");
        }

        private static int maxSequenceInHistory(String body, String tag) {
            int max = -1;
            int from = 0;
            String marker = "\"content\":\"" + tag + "--";
            while (true) {
                int index = body.indexOf(marker, from);
                if (index < 0) {
                    return max;
                }
                int numberStart = index + marker.length();
                int numberEnd = numberStart;
                while (numberEnd < body.length() && Character.isDigit(body.charAt(numberEnd))) {
                    numberEnd++;
                }
                if (numberEnd > numberStart) {
                    max = Math.max(max, Integer.parseInt(body.substring(numberStart, numberEnd)));
                }
                from = numberEnd;
            }
        }

        // ---------------------------------------------------------- 输出

        private void printComparison(ScenarioResult ws, ScenarioResult poll) {
            System.out.println();
            System.out.println("==================== 对比结果 ====================");
            System.out.printf(Locale.ROOT, "%-16s %8s %10s %10s %10s %10s %10s%n",
                    "场景", "消息数", "平均延迟ms", "P95ms", "最大延迟ms", "请求数", "接收字节");
            printRow(ws);
            printRow(poll);
            System.out.println("--------------------------------------------------");
            if (!ws.latenciesMicros().isEmpty() && !poll.latenciesMicros().isEmpty()) {
                double wsP95 = percentile(ws.latenciesMicros(), 0.95);
                double pollP95 = percentile(poll.latenciesMicros(), 0.95);
                System.out.printf(Locale.ROOT,
                        "结论: WS P95 %.2f ms 比轮询 %.2f ms 低 %.1f 倍；轮询请求数 %d 次/%.0fs ≈ %.1f 次/分钟，WS 仅 %d 次握手%n",
                        wsP95, pollP95, pollP95 / Math.max(0.001, wsP95),
                        poll.httpRequests(), (double) options.durationSeconds,
                        poll.httpRequests() * 60.0 / Math.max(1, options.durationSeconds),
                        ws.wsHandshakes());
            }
            System.out.println("CSV: " + options.outPath);
            System.out.println("==================================================");
        }

        private static void printRow(ScenarioResult result) {
            System.out.printf(Locale.ROOT, "%-16s %8d %10.2f %10.2f %10.2f %10d %10d%n",
                    result.scenario(),
                    result.messages(),
                    average(result.latenciesMicros()),
                    percentile(result.latenciesMicros(), 0.95),
                    max(result.latenciesMicros()),
                    result.httpRequests(),
                    result.bytesReceived());
        }

        private void writeCsv(ScenarioResult ws, ScenarioResult poll) throws IOException {
            Path path = Path.of(options.outPath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            List<String> lines = new ArrayList<>();
            lines.add("scenario,duration_s,messages,avg_latency_ms,min_latency_ms,p50_ms,p95_ms,max_latency_ms,"
                    + "http_requests,ws_handshakes,requests_per_minute,bytes_received,bytes_per_message,msg_per_sec,notes");
            lines.add(csvRow(ws));
            lines.add(csvRow(poll));
            Files.write(path, lines);
        }

        private static String csvRow(ScenarioResult result) {
            List<Long> latencies = result.latenciesMicros();
            double duration = Math.max(1, result.durationSeconds());
            return String.join(",",
                    result.scenario(),
                    Integer.toString(result.durationSeconds()),
                    Long.toString(result.messages()),
                    number(average(latencies)),
                    number(min(latencies)),
                    number(percentile(latencies, 0.50)),
                    number(percentile(latencies, 0.95)),
                    number(max(latencies)),
                    Long.toString(result.httpRequests()),
                    Integer.toString(result.wsHandshakes()),
                    String.format(Locale.ROOT, "%.2f", result.httpRequests() * 60.0 / duration),
                    Long.toString(result.bytesReceived()),
                    String.format(Locale.ROOT, "%.1f",
                            result.messages() == 0 ? 0 : result.bytesReceived() / (double) result.messages()),
                    String.format(Locale.ROOT, "%.2f", result.messages() / duration),
                    "\"" + result.notes().replace("\"", "'") + "\"");
        }

        // ---------------------------------------------------------- 工具

        private WebSocket connectWebSocket(String token, FrameQueue queue) throws Exception {
            String url = options.httpBase.replaceFirst("^http", "ws") + "/ws/chat?token=" + token;
            return httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                    .buildAsync(URI.create(url), queue)
                    .get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        private static String chatFrame(String content) {
            return "{\"type\":\"chat\",\"to\":null,\"content\":\"" + content + "\"}";
        }

        private static void closeQuietly(WebSocket webSocket) {
            if (webSocket == null) {
                return;
            }
            try {
                if (!webSocket.isOutputClosed()) {
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                }
            } catch (Exception ignored) {
                // 清理阶段忽略
            }
        }
    }

    // ================================================================== 辅助类型

    private record Registration(long userId, String token) {
    }

    private record ScenarioResult(String scenario,
                                  int durationSeconds,
                                  long messages,
                                  List<Long> latenciesMicros,
                                  long httpRequests,
                                  int wsHandshakes,
                                  long bytesReceived,
                                  long expectedPolls,
                                  String notes) {
    }

    /** 简单 WS 帧队列：测试里只需要按内容等待目标消息帧。 */
    private static final class FrameQueue implements WebSocket.Listener {

        private final LinkedBlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private final StringBuilder partial = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (data != null && data.length() > 0) {
                partial.append(data);
            }
            if (last) {
                frames.add(partial.toString());
                partial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        private String awaitContent(String content, long timeoutMillis) throws InterruptedException {
            String marker = "\"content\":\"" + content + "\"";
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new IllegalStateException("等待 WebSocket 消息超时: " + content);
                }
                String frame = frames.poll(remaining, TimeUnit.NANOSECONDS);
                if (frame == null) {
                    throw new IllegalStateException("等待 WebSocket 消息超时: " + content);
                }
                if (frame.contains(marker)) {
                    return frame;
                }
            }
        }
    }

    private static double average(List<Long> micros) {
        if (micros.isEmpty()) {
            return -1;
        }
        long sum = 0;
        for (long value : micros) {
            sum += value;
        }
        return sum / (double) micros.size() / 1_000.0;
    }

    private static double min(List<Long> micros) {
        if (micros.isEmpty()) {
            return -1;
        }
        long minimum = Long.MAX_VALUE;
        for (long value : micros) {
            minimum = Math.min(minimum, value);
        }
        return minimum / 1_000.0;
    }

    private static double max(List<Long> micros) {
        if (micros.isEmpty()) {
            return -1;
        }
        long maximum = Long.MIN_VALUE;
        for (long value : micros) {
            maximum = Math.max(maximum, value);
        }
        return maximum / 1_000.0;
    }

    private static double percentile(List<Long> micros, double percentile) {
        if (micros.isEmpty()) {
            return -1;
        }
        List<Long> sorted = new ArrayList<>(micros);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(index) / 1_000.0;
    }

    private static String number(double value) {
        return value < 0 ? "" : String.format(Locale.ROOT, "%.2f", value);
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
        if (index < 0) {
            return -1;
        }
        int start = index + marker.length();
        long value = 0;
        boolean found = false;
        while (start < json.length() && Character.isDigit(json.charAt(start))) {
            value = value * 10 + (json.charAt(start) - '0');
            start++;
            found = true;
        }
        return found ? value : -1;
    }

}
