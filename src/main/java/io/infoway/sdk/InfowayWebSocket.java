package io.infoway.sdk;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.infoway.sdk.exception.InfowayApiException;
import io.infoway.sdk.exception.InfowayAuthException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * WebSocket client with auto-reconnect, heartbeat, and subscription management.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * InfowayWebSocket ws = InfowayWebSocket.builder()
 *     .apiKey("YOUR_API_KEY")
 *     .business(WsBusiness.CRYPTO)
 *     .printFrames(true)   // optional; default is off
 *     .onTrade(data -> System.out.println(data.get("s") + " " + data.get("p")))
 *     .build();
 *
 * ws.connect();
 * ws.subscribeTrade("BTCUSDT,ETHUSDT");
 *
 * // later...
 * ws.close();
 * }</pre>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Auto-reconnect with exponential backoff (1s to 30s cap)</li>
 *   <li>Auto-resubscribe on reconnect; subscriptions made before the socket is open are
 *       replayed once it is, never dropped</li>
 *   <li>Heartbeat keepalive (30s interval). A reply is not required; the server sends
 *       {@code 10011} only when the client also sets {@code ack=1}. This client does not.</li>
 *   <li>{@code printFrames(true)} logs every inbound frame at INFO and prints
 *       it to stdout. Default is debug-only. {@code onFrame} always receives the raw text.</li>
 *   <li>Event callbacks: onTrade, onDepth, onKline, onFrame, onError, onReconnect, onDisconnect</li>
 *   <li>User callbacks are wrapped in try/catch — a user-thrown exception will not kill the connection</li>
 * </ul>
 *
 * <p><b>Callbacks receive the {@code data} payload</b> ({@code {"s","p","v","vw","t","td"}}),
 * not the {@code {"code":10002,"data":{…}}} envelope, so WS and REST hand you the same object.
 * Kline pushes keep {@code ty}, the interval.</p>
 *
 * <p>Two server behaviours worth knowing:</p>
 * <ul>
 *   <li>A subscribe ack ({@code msg:"ok"}) only means <i>accepted</i>. A wrong {@code business},
 *       an unknown symbol and a closed market all ack and then push nothing, forever.</li>
 *   <li>The gateway allows <b>60 frames per minute per connection</b> (subscribes, unsubscribes
 *       and heartbeats together) — always merge symbols into one comma-separated {@code codes}
 *       string instead of sending one frame per symbol.</li>
 * </ul>
 *
 * <p>News lives on a different endpoint: see {@link InfowayNewsWebSocket}.</p>
 */
public class InfowayWebSocket {

    private static final Logger log = LoggerFactory.getLogger(InfowayWebSocket.class);
    private static final String DEFAULT_WS_URL = "wss://data.infoway.io/ws";
    /** {"code":200,"msg":"ws connect success"} — sent on every connection, on every business. */
    private static final int WELCOME_CODE = 200;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final long INITIAL_BACKOFF_MS = 1000;
    private static final long MAX_BACKOFF_MS = 30000;

    private final String url;
    private final OkHttpClient okClient;
    private final Gson gson;
    private final ScheduledExecutorService scheduler;

    private final Set<Subscription> subscriptions = new CopyOnWriteArraySet<>();
    /**
     * Guards "register the subscription, then send it". Without it a subscribe racing with
     * onOpen is sent twice — once by the caller, once by the reconnect replay — and every
     * duplicate frame counts against the 60 frames/minute/connection budget.
     */
    private final Object connectionLock = new Object();
    private volatile WebSocket webSocket;
    private volatile boolean running;
    private volatile boolean everConnected;
    /** Set on handshake 401 so {@code onClosed} cannot schedule another connect. */
    private volatile boolean authRejected;
    private volatile long backoffMs = INITIAL_BACKOFF_MS;
    private ScheduledFuture<?> heartbeatFuture;
    private ScheduledFuture<?> reconnectFuture;
    /** True after onFailure or onClosed has claimed this drop — the other must not schedule again. */
    private volatile boolean dropHandled;
    /** Bumped on every doConnect so a late onClosed from the previous socket is ignored. */
    private int connectGeneration;
    /** Consecutive drops since the last successful open. Reset on onOpen. */
    private volatile int reconnectAttempt;
    private long heartbeatIntervalMs = HEARTBEAT_INTERVAL_SECONDS * 1000;
    private long initialBackoffMs = INITIAL_BACKOFF_MS;
    /** How many heartbeat timers have been armed (one per live session). */
    int heartbeatStarts;
    private final int maxReconnectAttempts;

    // Callbacks
    private Consumer<JsonObject> onTrade;
    private Consumer<JsonObject> onDepth;
    private Consumer<JsonObject> onKline;
    private Consumer<String> onFrame;
    private Consumer<Exception> onError;
    private Runnable onReconnect;
    private Runnable onDisconnect;
    private final boolean printFrames;

    private InfowayWebSocket(String apiKey, String business, String baseUrl,
                             int maxReconnectAttempts,
                             Consumer<JsonObject> onTrade, Consumer<JsonObject> onDepth,
                             Consumer<JsonObject> onKline, Consumer<String> onFrame,
                             Consumer<Exception> onError,
                             Runnable onReconnect, Runnable onDisconnect,
                             boolean printFrames) {
        this.url = baseUrl + "?business=" + business + "&apikey=" + apiKey;
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.onTrade = onTrade;
        this.onDepth = onDepth;
        this.onKline = onKline;
        this.onFrame = onFrame;
        this.onError = onError;
        this.onReconnect = onReconnect;
        this.onDisconnect = onDisconnect;
        this.printFrames = printFrames;
        this.gson = new Gson();
        this.okClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "infoway-ws-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Connect to the WebSocket endpoint. Starts the heartbeat loop.
     */
    public void connect() {
        running = true;
        authRejected = false;
        doConnect(0);
    }

    private void doConnect(int attempt) {
        if (!running || authRejected) return;
        final int gen;
        synchronized (connectionLock) {
            connectGeneration++;
            gen = connectGeneration;
            dropHandled = false;
        }

        Request request = new Request.Builder().url(url).build();
        okClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                log.info("WebSocket connected");
                synchronized (connectionLock) {
                    webSocket = ws;
                    backoffMs = initialBackoffMs;
                    dropHandled = false;
                    reconnectAttempt = 0;
                    startHeartbeatLocked();
                    // Flush everything the caller asked for while we were offline. Subscriptions
                    // requested before connect() used to be dropped on the floor.
                    if (!subscriptions.isEmpty()) {
                        resubscribe();
                    }
                }
                if (!everConnected) {
                    everConnected = true;
                } else {
                    safeRun(onReconnect, "onReconnect");
                }
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                emitInbound(text);
                JsonObject msg = WsFrames.parseObject(gson, text);
                if (msg == null || !msg.has("code")) {
                    // business=stock opens with a PLAIN TEXT greeting. Stock also
                    // sometimes sends PARAM_NOT_JSON as a bare string (no JSON).
                    if (text != null && text.startsWith("Param not json")) {
                        log.warn("Server error frame code=515 msg={}", truncate(text));
                        safeAccept(onError, InfowayApiException.ofWs(
                                WsErrorCode.PARAM_NOT_JSON.getCode(), text, null), "onError");
                        return;
                    }
                    log.debug("Non-JSON frame skipped: {}", truncate(text));
                    return;
                }
                int code = msg.get("code").getAsInt();

                if (code == WELCOME_CODE) {
                    // {"code":200,"msg":"ws connect success"} — success, not an error
                    log.debug("Welcome frame: {}", truncate(text));
                    return;
                }

                WsCode wsCode = WsCode.fromCode(code);
                if (wsCode == null) {
                    Exception error = WsFrames.toError(msg, text);
                    log.warn("Server error frame code={} msg={}", code, error.getMessage());
                    safeAccept(onError, error, "onError");
                    return;
                }
                switch (wsCode) {
                    case PUSH_TRADE:
                        safeAccept(onTrade, payload(msg), "onTrade");
                        break;
                    case PUSH_DEPTH:
                        safeAccept(onDepth, payload(msg), "onDepth");
                        break;
                    case PUSH_KLINE:
                        // payload keeps "ty" (the interval) — callers need it to route pushes
                        safeAccept(onKline, payload(msg), "onKline");
                        break;
                    case SUB_TRADE_ACK:
                    case SUB_DEPTH_ACK:
                    case SUB_KLINE_ACK:
                    case UNSUB_ACK:
                    case HEART_APPLY:
                        // "ok" only means the request was accepted. A wrong business or an
                        // unknown code is also acked and then stays silent forever.
                        log.debug("Sub/unsub/heart ack: code={}", code);
                        break;
                    case HEARTBEAT:
                        log.debug("Heartbeat frame received (the server normally sends none)");
                        break;
                    default:
                        log.debug("Server-only code {} arrived unexpectedly: {}", code, truncate(text));
                        break;
                }
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                // Auth is rejected during the HTTP handshake (401 + www-authenticate: apikey),
                // never as a WS frame. Reconnecting cannot fix it and risks getting the key banned.
                if (response != null && response.code() == 401) {
                    rejectAuth(readBody(response));
                    return;
                }
                if (!running) {
                    clearSocket();
                    return;
                }
                log.warn("Connection lost ({}), reconnecting...", t.getMessage());
                handleDrop(gen, t);
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                ws.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                // Handshake 401 / user close: do not fire onDisconnect or schedule another socket.
                // A clean server close never calls onFailure — that path must still reconnect.
                if (!running || authRejected) {
                    clearSocket();
                    return;
                }
                if (everConnected) {
                    handleDrop(gen, null);
                } else {
                    clearSocket();
                }
            }
        });
    }

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() > 200 ? text.substring(0, 200) : text;
    }

    /**
     * Unwrap {@code {"code":10002,"data":{...}}} to {@code {...}} so WS callbacks and REST
     * responses carry the same object. Defensive: frames without data are passed through.
     */
    private static JsonObject payload(JsonObject msg) {
        JsonElement data = msg.get("data");
        return data != null && data.isJsonObject() ? data.getAsJsonObject() : msg;
    }

    private static String readBody(Response response) {
        try {
            return response.body() != null ? truncate(response.body().string().trim()) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String authErrorMessage(String detail) {
        return "WebSocket handshake rejected with HTTP 401 — the API key is invalid or has no "
                + "access to this channel" + (detail.isEmpty() ? "" : " (" + detail + ")");
    }

    private void safeAccept(Consumer<JsonObject> cb, JsonObject msg, String name) {
        if (cb == null) return;
        try {
            cb.accept(msg);
        } catch (Throwable t) {
            log.error("User callback {} threw — connection preserved", name, t);
        }
    }

    private void safeAccept(Consumer<Exception> cb, Exception e, String name) {
        if (cb == null) return;
        try {
            cb.accept(e);
        } catch (Throwable t) {
            log.error("User callback {} threw — swallowed", name, t);
        }
    }

    private void emitInbound(String text) {
        if (printFrames) {
            log.info("WS << {}", text);
            System.out.println(text);
        } else {
            log.debug("WS << {}", truncate(text));
        }
        if (onFrame != null) {
            try {
                onFrame.accept(text);
            } catch (Throwable t) {
                log.error("User callback onFrame threw — swallowed", t);
            }
        }
    }

    private void safeRun(Runnable cb, String name) {
        if (cb == null) return;
        try {
            cb.run();
        } catch (Throwable t) {
            log.error("User callback {} threw — swallowed", name, t);
        }
    }

    /**
     * One drop produces at most one reconnect. OkHttp often calls both
     * {@code onFailure} and {@code onClosed}; scheduling twice would open two
     * sockets and two heartbeat timers against the 60 frames/min budget.
     */
    private void handleDrop(int gen, Throwable cause) {
        int nextAttempt;
        synchronized (connectionLock) {
            if (gen != connectGeneration) {
                return;
            }
            stopHeartbeatLocked();
            webSocket = null;
            if (dropHandled || !running || authRejected) {
                return;
            }
            dropHandled = true;
            reconnectAttempt++;
            nextAttempt = reconnectAttempt;
        }
        safeRun(onDisconnect, "onDisconnect");
        if (maxReconnectAttempts > 0 && nextAttempt > maxReconnectAttempts) {
            log.error("Max reconnect attempts ({}) reached", maxReconnectAttempts);
            safeAccept(onError, new Exception("Max reconnect attempts reached", cause), "onError");
            return;
        }
        scheduleReconnect(nextAttempt);
    }

    private void scheduleReconnect(int attempt) {
        synchronized (connectionLock) {
            if (!running || authRejected) {
                return;
            }
            cancelReconnectLocked();
            long delay = backoffMs;
            log.warn("Reconnecting in {}ms (attempt {})...", delay, attempt);
            reconnectFuture = scheduler.schedule(() -> doConnect(attempt), delay, TimeUnit.MILLISECONDS);
            backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
        }
    }

    private void rejectAuth(String detail) {
        authRejected = true;
        running = false;
        clearSocket();
        cancelReconnect();
        okClient.dispatcher().cancelAll();
        log.error("WebSocket authentication failed (HTTP 401){}",
                detail.isEmpty() ? "" : ": " + detail);
        safeRun(onDisconnect, "onDisconnect");
        safeAccept(onError, new InfowayAuthException(authErrorMessage(detail)), "onError");
    }

    private void clearSocket() {
        synchronized (connectionLock) {
            stopHeartbeatLocked();
            webSocket = null;
        }
    }

    private void startHeartbeatLocked() {
        stopHeartbeatLocked();
        heartbeatStarts++;
        heartbeatFuture = scheduler.scheduleAtFixedRate(() -> {
            // wrap in try/catch — scheduleAtFixedRate cancels the future if the task throws
            try {
                WebSocket ws = webSocket;
                if (ws != null && running) {
                    ws.send(heartbeatPayload());
                    log.debug("Heartbeat sent");
                }
            } catch (Throwable t) {
                log.warn("Heartbeat send failed (will retry next interval): {}", t.getMessage());
            }
        }, heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void stopHeartbeatLocked() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
            heartbeatFuture = null;
        }
    }

    private void cancelReconnect() {
        synchronized (connectionLock) {
            cancelReconnectLocked();
        }
    }

    private void cancelReconnectLocked() {
        if (reconnectFuture != null) {
            reconnectFuture.cancel(false);
            reconnectFuture = null;
        }
    }

    /** Avoid Gson + UUID on the 30s keepalive path. */
    private static String heartbeatPayload() {
        return "{\"code\":10010,\"trace\":\"" + Long.toHexString(System.nanoTime()) + "\"}";
    }

    private void resubscribe() {
        for (Subscription sub : subscriptions) {
            switch (sub.type) {
                case "trade" -> sendCodesMessage(WsCode.SUB_TRADE.getCode(), sub.codes, sub.includeTy);
                case "depth" -> sendCodesMessage(WsCode.SUB_DEPTH.getCode(), sub.codes, false);
                case "kline" -> sendKlineMessage(WsCode.SUB_KLINE.getCode(), sub.codes, sub.klineType);
            }
            log.info("Re-subscribed {}: {}", sub.type, sub.codes);
        }
    }

    /**
     * Subscribe to real-time trade data.
     *
     * <p>Merge every symbol into one comma-separated string: the gateway allows
     * 60 frames per minute per connection (subscribes, unsubscribes and heartbeats
     * combined) and disconnects abusers. One frame per symbol will get you kicked.</p>
     *
     * <p>Safe to call before {@link #connect()} or while reconnecting — the request is
     * remembered and sent as soon as the socket is open.</p>
     *
     * @param codes comma-separated symbol codes
     */
    public void subscribeTrade(String codes) {
        subscribeTrade(codes, false);
    }

    /**
     * Subscribe to real-time trade data.
     *
     * @param codes     comma-separated symbol codes
     * @param includeTy when {@code true}, equity pushes may include trade-type {@code ty};
     *                  crypto ticks omit it even when this flag is set
     */
    public void subscribeTrade(String codes, boolean includeTy) {
        synchronized (connectionLock) {
            subscriptions.add(new Subscription("trade", codes, 0, includeTy));
            sendCodesMessage(WsCode.SUB_TRADE.getCode(), codes, includeTy);
        }
    }

    /**
     * Subscribe to real-time depth data.
     *
     * @param codes comma-separated symbol codes
     */
    public void subscribeDepth(String codes) {
        synchronized (connectionLock) {
            subscriptions.add(new Subscription("depth", codes, 0, false));
            sendCodesMessage(WsCode.SUB_DEPTH.getCode(), codes, false);
        }
    }

    /**
     * Subscribe to real-time kline data with a specific interval.
     *
     * @param codes comma-separated symbol codes
     * @param klineType kline interval (e.g. MIN_1, DAY)
     */
    public void subscribeKline(String codes, KlineType klineType) {
        int t = klineType.getValue();
        synchronized (connectionLock) {
            subscriptions.add(new Subscription("kline", codes, t, false));
            sendKlineMessage(WsCode.SUB_KLINE.getCode(), codes, t);
        }
    }

    /**
     * Subscribe to real-time kline data (defaults to 1-minute interval).
     *
     * @param codes comma-separated symbol codes
     */
    public void subscribeKline(String codes) {
        subscribeKline(codes, KlineType.MIN_1);
    }

    /**
     * Unsubscribe from trade data.
     *
     * @param codes comma-separated symbol codes
     */
    public void unsubscribeTrade(String codes) {
        synchronized (connectionLock) {
            subscriptions.removeIf(s -> "trade".equals(s.type) && codes.equals(s.codes));
            sendCodesMessage(WsCode.UNSUB_TRADE.getCode(), codes, false);
        }
    }

    /**
     * Unsubscribe from depth data.
     *
     * @param codes comma-separated symbol codes
     */
    public void unsubscribeDepth(String codes) {
        synchronized (connectionLock) {
            subscriptions.removeIf(s -> "depth".equals(s.type) && codes.equals(s.codes));
            sendCodesMessage(WsCode.UNSUB_DEPTH.getCode(), codes, false);
        }
    }

    /**
     * Unsubscribe from kline data.
     *
     * <p>The interval is sent as {@code klineTypes}. Omitting it (as versions before 0.2.0 did)
     * makes the server drop <b>every</b> interval subscribed for those symbols.</p>
     *
     * @param codes comma-separated symbol codes
     * @param klineType kline interval used at subscribe time
     */
    public void unsubscribeKline(String codes, KlineType klineType) {
        synchronized (connectionLock) {
            subscriptions.removeIf(s -> "kline".equals(s.type) && codes.equals(s.codes)
                    && s.klineType == klineType.getValue());
            sendUnsubKlineMessage(codes, String.valueOf(klineType.getValue()));
        }
    }

    /**
     * Unsubscribe several kline intervals at once.
     *
     * @param codes      comma-separated symbol codes
     * @param klineTypes comma-separated interval values, e.g. {@code "1,2,8"}
     */
    public void unsubscribeKline(String codes, String klineTypes) {
        synchronized (connectionLock) {
            for (String value : klineTypes.split(",")) {
                try {
                    subscriptions.removeIf(s -> "kline".equals(s.type) && codes.equals(s.codes)
                            && s.klineType == Integer.parseInt(value.trim()));
                } catch (NumberFormatException ignored) {
                    // keep going: an unparseable entry only affects local bookkeeping
                }
            }
            sendUnsubKlineMessage(codes, klineTypes);
        }
    }

    /**
     * Unsubscribe from kline data subscribed without an explicit interval.
     */
    public void unsubscribeKline(String codes) {
        unsubscribeKline(codes, KlineType.MIN_1);
    }

    private void sendCodesMessage(int code, String codes, boolean includeTy) {
        JsonObject msg = baseMessage(code);
        JsonObject data = new JsonObject();
        data.addProperty("codes", codes);
        if (includeTy) {
            data.addProperty("includeTy", true);
        }
        msg.add("data", data);
        send(msg);
    }

    private void sendKlineMessage(int code, String codes, int klineType) {
        JsonObject msg = baseMessage(code);
        JsonObject data = new JsonObject();
        JsonArray arr = new JsonArray();
        JsonObject item = new JsonObject();
        item.addProperty("codes", codes);
        item.addProperty("type", klineType);
        arr.add(item);
        data.add("arr", arr);
        msg.add("data", data);
        send(msg);
    }

    /** Unsubscribe payload for klines: {@code {"codes":"...","klineTypes":"1,2"}}. */
    private void sendUnsubKlineMessage(String codes, String klineTypes) {
        JsonObject msg = baseMessage(WsCode.UNSUB_KLINE.getCode());
        JsonObject data = new JsonObject();
        data.addProperty("codes", codes);
        data.addProperty("klineTypes", klineTypes);
        msg.add("data", data);
        send(msg);
    }

    /**
     * Send a frame if the socket is up. When it is not, the frame is not lost:
     * {@link #subscriptions} is the desired state and is replayed by {@code onOpen}.
     */
    private void send(JsonObject msg) {
        WebSocket ws = webSocket;
        if (ws == null) {
            log.debug("Socket not open — frame deferred until the connection is established: {}",
                    truncate(gson.toJson(msg)));
            return;
        }
        ws.send(gson.toJson(msg));
    }

    private JsonObject baseMessage(int code) {
        JsonObject msg = new JsonObject();
        msg.addProperty("code", code);
        msg.addProperty("trace", UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        return msg;
    }

    /**
     * Close the WebSocket connection and release resources.
     */
    public void close() {
        running = false;
        cancelReconnect();
        WebSocket ws;
        synchronized (connectionLock) {
            stopHeartbeatLocked();
            ws = webSocket;
            webSocket = null;
        }
        if (ws != null) {
            ws.close(1000, "Client closing");
        }
        scheduler.shutdown();
        okClient.dispatcher().executorService().shutdown();
        okClient.connectionPool().evictAll();
    }

    /** Test hook: shorten keepalive / backoff so lifecycle tests finish quickly. */
    void testTiming(long heartbeatMs, long backoffMs) {
        this.heartbeatIntervalMs = heartbeatMs;
        this.initialBackoffMs = backoffMs;
        this.backoffMs = backoffMs;
    }

    public void setOnTrade(Consumer<JsonObject> onTrade) { this.onTrade = onTrade; }
    public void setOnDepth(Consumer<JsonObject> onDepth) { this.onDepth = onDepth; }
    public void setOnKline(Consumer<JsonObject> onKline) { this.onKline = onKline; }
    public void setOnFrame(Consumer<String> onFrame) { this.onFrame = onFrame; }
    public void setOnError(Consumer<Exception> onError) { this.onError = onError; }
    public void setOnReconnect(Runnable onReconnect) { this.onReconnect = onReconnect; }
    public void setOnDisconnect(Runnable onDisconnect) { this.onDisconnect = onDisconnect; }

    /**
     * Create a new InfowayWebSocket builder.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for InfowayWebSocket.
     */
    public static class Builder {
        private String apiKey;
        private String business;
        private String baseUrl = DEFAULT_WS_URL;
        private int maxReconnectAttempts = 0;
        private Consumer<JsonObject> onTrade;
        private Consumer<JsonObject> onDepth;
        private Consumer<JsonObject> onKline;
        private Consumer<String> onFrame;
        private Consumer<Exception> onError;
        private Runnable onReconnect;
        private Runnable onDisconnect;
        private boolean printFrames = false;

        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder business(String business) { this.business = business; return this; }
        public Builder business(WsBusiness business) {
            this.business = business != null ? business.value() : null;
            return this;
        }
        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder maxReconnectAttempts(int max) { this.maxReconnectAttempts = max; return this; }
        public Builder onTrade(Consumer<JsonObject> cb) { this.onTrade = cb; return this; }
        public Builder onDepth(Consumer<JsonObject> cb) { this.onDepth = cb; return this; }
        public Builder onKline(Consumer<JsonObject> cb) { this.onKline = cb; return this; }
        /** Raw inbound text, including welcome / ack / error / plaintext. */
        public Builder onFrame(Consumer<String> cb) { this.onFrame = cb; return this; }
        public Builder onError(Consumer<Exception> cb) { this.onError = cb; return this; }
        public Builder onReconnect(Runnable cb) { this.onReconnect = cb; return this; }
        public Builder onDisconnect(Runnable cb) { this.onDisconnect = cb; return this; }
        /** Print every inbound frame to stdout. Default {@code false}. */
        public Builder printFrames(boolean print) { this.printFrames = print; return this; }

        public InfowayWebSocket build() {
            this.apiKey = ApiKeys.require(apiKey);
            if (business == null || business.isEmpty()) {
                throw new IllegalArgumentException("business is required");
            }
            return new InfowayWebSocket(apiKey, business, baseUrl, maxReconnectAttempts,
                    onTrade, onDepth, onKline, onFrame, onError, onReconnect, onDisconnect,
                    printFrames);
        }
    }

    /**
     * Internal subscription record for resubscribing on reconnect.
     * klineType is the kline interval (KlineType.value); 0 for non-kline subscriptions.
     */
    private record Subscription(String type, String codes, int klineType, boolean includeTy) {}
}
