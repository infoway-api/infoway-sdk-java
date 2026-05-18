package io.infoway.sdk;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
 *     .business("stock")
 *     .onTrade(msg -> System.out.println("Trade: " + msg))
 *     .build();
 *
 * ws.connect();
 * ws.subscribeTrade("AAPL.US,TSLA.US");
 *
 * // later...
 * ws.close();
 * }</pre>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Auto-reconnect with exponential backoff (1s to 30s cap)</li>
 *   <li>Auto-resubscribe on reconnect</li>
 *   <li>Heartbeat keepalive (30s interval)</li>
 *   <li>Event callbacks: onTrade, onDepth, onKline, onError, onReconnect, onDisconnect</li>
 *   <li>User callbacks are wrapped in try/catch — a user-thrown exception will not kill the connection</li>
 * </ul>
 */
public class InfowayWebSocket {

    private static final Logger log = LoggerFactory.getLogger(InfowayWebSocket.class);
    private static final String DEFAULT_WS_URL = "wss://data.infoway.io/ws";
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final long INITIAL_BACKOFF_MS = 1000;
    private static final long MAX_BACKOFF_MS = 30000;

    private final String url;
    private final OkHttpClient okClient;
    private final Gson gson;
    private final ScheduledExecutorService scheduler;

    private final Set<Subscription> subscriptions = new CopyOnWriteArraySet<>();
    private volatile WebSocket webSocket;
    private volatile boolean running;
    private volatile long backoffMs = INITIAL_BACKOFF_MS;
    private ScheduledFuture<?> heartbeatFuture;
    private final int maxReconnectAttempts;

    // Callbacks
    private Consumer<JsonObject> onTrade;
    private Consumer<JsonObject> onDepth;
    private Consumer<JsonObject> onKline;
    private Consumer<Exception> onError;
    private Runnable onReconnect;
    private Runnable onDisconnect;

    private InfowayWebSocket(String apiKey, String business, String baseUrl,
                             int maxReconnectAttempts,
                             Consumer<JsonObject> onTrade, Consumer<JsonObject> onDepth,
                             Consumer<JsonObject> onKline, Consumer<Exception> onError,
                             Runnable onReconnect, Runnable onDisconnect) {
        this.url = baseUrl + "?business=" + business + "&apikey=" + apiKey;
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.onTrade = onTrade;
        this.onDepth = onDepth;
        this.onKline = onKline;
        this.onError = onError;
        this.onReconnect = onReconnect;
        this.onDisconnect = onDisconnect;
        this.gson = new Gson();
        this.okClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
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
        doConnect(0);
    }

    private void doConnect(int attempt) {
        if (!running) return;

        Request request = new Request.Builder().url(url).build();
        okClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                log.info("WebSocket connected");
                webSocket = ws;
                backoffMs = INITIAL_BACKOFF_MS;
                startHeartbeat();
                if (!subscriptions.isEmpty()) {
                    resubscribe();
                    safeRun(onReconnect, "onReconnect");
                }
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                JsonObject msg;
                int code;
                try {
                    msg = gson.fromJson(text, JsonObject.class);
                    if (msg == null || !msg.has("code")) {
                        log.debug("Non-code message: {}", truncate(text));
                        return;
                    }
                    code = msg.get("code").getAsInt();
                } catch (Exception e) {
                    // Server sends a plaintext greeting on connect — silently ignore.
                    log.debug("Non-JSON message: {}", truncate(text));
                    return;
                }

                WsCode wsCode = WsCode.fromCode(code);
                if (wsCode == null) {
                    log.debug("Unknown code {}: {}", code, truncate(text));
                    return;
                }
                switch (wsCode) {
                    case PUSH_TRADE:
                        safeAccept(onTrade, msg, "onTrade");
                        break;
                    case PUSH_DEPTH:
                        safeAccept(onDepth, msg, "onDepth");
                        break;
                    case PUSH_KLINE:
                        safeAccept(onKline, msg, "onKline");
                        break;
                    case SUB_TRADE_ACK:
                    case SUB_DEPTH_ACK:
                    case SUB_KLINE_ACK:
                    case UNSUB_ACK:
                        log.debug("Sub/unsub ack: code={}", code);
                        break;
                    case HEARTBEAT:
                        log.debug("Heartbeat received");
                        break;
                    default:
                        log.debug("Server-only code {} arrived unexpectedly: {}", code, truncate(text));
                        break;
                }
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                stopHeartbeat();
                webSocket = null;
                if (!running) return;
                safeRun(onDisconnect, "onDisconnect");
                int nextAttempt = attempt + 1;
                if (maxReconnectAttempts > 0 && nextAttempt > maxReconnectAttempts) {
                    log.error("Max reconnect attempts ({}) reached", maxReconnectAttempts);
                    safeAccept(onError, new Exception("Max reconnect attempts reached", t), "onError");
                    return;
                }
                log.warn("Connection lost ({}), reconnecting in {}ms (attempt {})...",
                        t.getMessage(), backoffMs, nextAttempt);
                scheduler.schedule(() -> doConnect(nextAttempt), backoffMs, TimeUnit.MILLISECONDS);
                backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                ws.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                stopHeartbeat();
                webSocket = null;
                safeRun(onDisconnect, "onDisconnect");
                if (running) {
                    scheduler.schedule(() -> doConnect(0), backoffMs, TimeUnit.MILLISECONDS);
                }
            }
        });
    }

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() > 200 ? text.substring(0, 200) : text;
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

    private void safeRun(Runnable cb, String name) {
        if (cb == null) return;
        try {
            cb.run();
        } catch (Throwable t) {
            log.error("User callback {} threw — swallowed", name, t);
        }
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatFuture = scheduler.scheduleAtFixedRate(() -> {
            // wrap in try/catch — scheduleAtFixedRate cancels the future if the task throws
            try {
                WebSocket ws = webSocket;
                if (ws != null && running) {
                    ws.send(buildSimpleMessage(WsCode.HEARTBEAT.getCode()));
                    log.debug("Heartbeat sent");
                }
            } catch (Throwable t) {
                log.warn("Heartbeat send failed (will retry next interval): {}", t.getMessage());
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
            heartbeatFuture = null;
        }
    }

    private void resubscribe() {
        for (Subscription sub : subscriptions) {
            switch (sub.type) {
                case "trade" -> sendCodesMessage(WsCode.SUB_TRADE.getCode(), sub.codes);
                case "depth" -> sendCodesMessage(WsCode.SUB_DEPTH.getCode(), sub.codes);
                case "kline" -> sendKlineMessage(WsCode.SUB_KLINE.getCode(), sub.codes, sub.klineType);
            }
            log.info("Re-subscribed {}: {}", sub.type, sub.codes);
        }
    }

    /**
     * Subscribe to real-time trade data.
     *
     * @param codes comma-separated symbol codes
     */
    public void subscribeTrade(String codes) {
        subscriptions.add(new Subscription("trade", codes, 0));
        sendCodesMessage(WsCode.SUB_TRADE.getCode(), codes);
    }

    /**
     * Subscribe to real-time depth data.
     *
     * @param codes comma-separated symbol codes
     */
    public void subscribeDepth(String codes) {
        subscriptions.add(new Subscription("depth", codes, 0));
        sendCodesMessage(WsCode.SUB_DEPTH.getCode(), codes);
    }

    /**
     * Subscribe to real-time kline data with a specific interval.
     *
     * @param codes comma-separated symbol codes
     * @param klineType kline interval (e.g. MIN_1, DAY)
     */
    public void subscribeKline(String codes, KlineType klineType) {
        int t = klineType.getValue();
        subscriptions.add(new Subscription("kline", codes, t));
        sendKlineMessage(WsCode.SUB_KLINE.getCode(), codes, t);
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
        subscriptions.remove(new Subscription("trade", codes, 0));
        sendCodesMessage(WsCode.UNSUB_TRADE.getCode(), codes);
    }

    /**
     * Unsubscribe from depth data.
     *
     * @param codes comma-separated symbol codes
     */
    public void unsubscribeDepth(String codes) {
        subscriptions.remove(new Subscription("depth", codes, 0));
        sendCodesMessage(WsCode.UNSUB_DEPTH.getCode(), codes);
    }

    /**
     * Unsubscribe from kline data.
     *
     * @param codes comma-separated symbol codes
     * @param klineType kline interval used at subscribe time
     */
    public void unsubscribeKline(String codes, KlineType klineType) {
        subscriptions.remove(new Subscription("kline", codes, klineType.getValue()));
        sendCodesMessage(WsCode.UNSUB_KLINE.getCode(), codes);
    }

    /**
     * Unsubscribe from kline data subscribed without an explicit interval.
     */
    public void unsubscribeKline(String codes) {
        unsubscribeKline(codes, KlineType.MIN_1);
    }

    private void sendCodesMessage(int code, String codes) {
        WebSocket ws = webSocket;
        if (ws == null) return;
        JsonObject msg = baseMessage(code);
        JsonObject data = new JsonObject();
        data.addProperty("codes", codes);
        msg.add("data", data);
        ws.send(gson.toJson(msg));
    }

    private void sendKlineMessage(int code, String codes, int klineType) {
        WebSocket ws = webSocket;
        if (ws == null) return;
        JsonObject msg = baseMessage(code);
        JsonObject data = new JsonObject();
        JsonArray arr = new JsonArray();
        JsonObject item = new JsonObject();
        item.addProperty("codes", codes);
        item.addProperty("type", klineType);
        arr.add(item);
        data.add("arr", arr);
        msg.add("data", data);
        ws.send(gson.toJson(msg));
    }

    private String buildSimpleMessage(int code) {
        return gson.toJson(baseMessage(code));
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
        stopHeartbeat();
        WebSocket ws = webSocket;
        if (ws != null) {
            ws.close(1000, "Client closing");
            webSocket = null;
        }
        scheduler.shutdown();
        okClient.dispatcher().executorService().shutdown();
        okClient.connectionPool().evictAll();
    }

    public void setOnTrade(Consumer<JsonObject> onTrade) { this.onTrade = onTrade; }
    public void setOnDepth(Consumer<JsonObject> onDepth) { this.onDepth = onDepth; }
    public void setOnKline(Consumer<JsonObject> onKline) { this.onKline = onKline; }
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
        private Consumer<Exception> onError;
        private Runnable onReconnect;
        private Runnable onDisconnect;

        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder business(String business) { this.business = business; return this; }
        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder maxReconnectAttempts(int max) { this.maxReconnectAttempts = max; return this; }
        public Builder onTrade(Consumer<JsonObject> cb) { this.onTrade = cb; return this; }
        public Builder onDepth(Consumer<JsonObject> cb) { this.onDepth = cb; return this; }
        public Builder onKline(Consumer<JsonObject> cb) { this.onKline = cb; return this; }
        public Builder onError(Consumer<Exception> cb) { this.onError = cb; return this; }
        public Builder onReconnect(Runnable cb) { this.onReconnect = cb; return this; }
        public Builder onDisconnect(Runnable cb) { this.onDisconnect = cb; return this; }

        public InfowayWebSocket build() {
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalArgumentException("apiKey is required");
            }
            if (business == null || business.isEmpty()) {
                throw new IllegalArgumentException("business is required");
            }
            return new InfowayWebSocket(apiKey, business, baseUrl, maxReconnectAttempts,
                    onTrade, onDepth, onKline, onError, onReconnect, onDisconnect);
        }
    }

    /**
     * Internal subscription record for resubscribing on reconnect.
     * klineType is the kline interval (KlineType.value); 0 for non-kline subscriptions.
     */
    private record Subscription(String type, String codes, int klineType) {}
}
