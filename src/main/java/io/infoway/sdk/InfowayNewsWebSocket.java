package io.infoway.sdk;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.infoway.sdk.exception.InfowayApiException;
import io.infoway.sdk.exception.InfowayAuthException;
import io.infoway.sdk.model.NewsItem;
import io.infoway.sdk.model.Normalizer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Real-time news WebSocket client.
 *
 * <p>News is <b>not</b> carried on the market-data connection. It has its own endpoint,
 * its own codes and its own entitlement:</p>
 *
 * <pre>{@code
 * InfowayNewsWebSocket news = InfowayNewsWebSocket.builder()
 *     .apiKey("YOUR_API_KEY")
 *     .lang(NewsLang.EN)
 *     .printFrames(true)   // optional; default is off
 *     .onNews(item -> System.out.println(item.get("title").getAsString()))
 *     .onNewsParsed(item -> System.out.println(item.title()))
 *     .onError(e -> System.err.println(e.getMessage()))
 *     .build();
 *
 * news.connect();
 * // later
 * news.close();
 * }</pre>
 *
 * <ul>
 *   <li>URL {@code wss://data.infoway.io/news?apikey=<key>} — no {@code business} parameter</li>
 *   <li>subscribe {@code 10020} → ack {@code 10021} → push {@code 10022}</li>
 *   <li>re-subscribing replaces the language; {@link #unsubscribe()} sends code {@code 11020}</li>
 *   <li>heartbeat {@code 10010} every 30s; {@code 10011} is returned only when the
 *       client also sends {@code ack=1}</li>
 *   <li>one connection per API key</li>
 *   <li>needs a separate entitlement — a key without it is rejected with HTTP <b>401</b>
 *       during the handshake, which this client reports as {@link InfowayAuthException}
 *       instead of reconnecting forever</li>
 * </ul>
 *
 * <p>Push payload fields: {@code dk} (dedup key), {@code country}, {@code lang}, {@code route},
 * {@code title}, {@code published} (epoch <b>seconds</b>), {@code urgency} (lower = more urgent),
 * {@code provider}, {@code symbols[]}, {@code link}, {@code content}, {@code sd} (summary).</p>
 */
public class InfowayNewsWebSocket {

    private static final Logger log = LoggerFactory.getLogger(InfowayNewsWebSocket.class);
    private static final String DEFAULT_NEWS_URL = "wss://data.infoway.io/news";
    private static final String DEFAULT_LANG = "en";
    private static final int WELCOME_CODE = 200;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final long INITIAL_BACKOFF_MS = 1000;
    private static final long MAX_BACKOFF_MS = 30000;

    private final String url;
    private final OkHttpClient okClient;
    private final Gson gson = new Gson();
    private final ScheduledExecutorService scheduler;
    private final int maxReconnectAttempts;

    /** Guards "record the language, then send it" against a concurrent onOpen replay. */
    private final Object connectionLock = new Object();
    private volatile WebSocket webSocket;
    private volatile boolean running;
    private volatile boolean everConnected;
    /** Set on handshake 401 so {@code onClosed} cannot schedule another connect. */
    private volatile boolean authRejected;
    private volatile long backoffMs = INITIAL_BACKOFF_MS;
    /** Desired language; replayed on every (re)connect, so subscribing before connect works. */
    private volatile String lang;
    private ScheduledFuture<?> heartbeatFuture;
    private ScheduledFuture<?> reconnectFuture;
    /** True after onFailure or onClosed has claimed this drop — the other must not schedule again. */
    private volatile boolean dropHandled;
    private int connectGeneration;
    private volatile int reconnectAttempt;
    private long heartbeatIntervalMs = HEARTBEAT_INTERVAL_SECONDS * 1000;
    private long initialBackoffMs = INITIAL_BACKOFF_MS;
    int heartbeatStarts;

    private Consumer<JsonObject> onNews;
    private Consumer<NewsItem> onNewsParsed;
    private Consumer<String> onFrame;
    private Consumer<Exception> onError;
    private Runnable onReconnect;
    private Runnable onDisconnect;
    private final boolean printFrames;

    private InfowayNewsWebSocket(String apiKey, String baseUrl, String lang, int maxReconnectAttempts,
                                 Consumer<JsonObject> onNews, Consumer<NewsItem> onNewsParsed,
                                 Consumer<String> onFrame,
                                 Consumer<Exception> onError,
                                 Runnable onReconnect, Runnable onDisconnect,
                                 boolean printFrames) {
        this.url = baseUrl + "?apikey=" + apiKey;
        this.lang = lang;
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.onNews = onNews;
        this.onNewsParsed = onNewsParsed;
        this.onFrame = onFrame;
        this.onError = onError;
        this.onReconnect = onReconnect;
        this.onDisconnect = onDisconnect;
        this.printFrames = printFrames;
        this.okClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "infoway-news-ws-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /** Connect and (re)subscribe the configured language. */
    public void connect() {
        running = true;
        authRejected = false;
        doConnect(0);
    }

    /**
     * Subscribe to a language. Safe to call before {@link #connect()}: the request is
     * remembered and sent as soon as the socket is open. Re-subscribing replaces the
     * previous language on the same connection.
     *
     * @param lang one of {@code en zh-Hans zh-Hant ja ko de fr es pt ru tr}
     */
    public void subscribe(String lang) {
        synchronized (connectionLock) {
            this.lang = lang;
            sendSubscribe();
        }
    }

    public void subscribe(NewsLang lang) {
        subscribe(lang != null ? lang.value() : null);
    }

    /**
     * Drop the current language subscription (code {@code 11020}). Safe if the
     * socket is not yet open — the desired language is cleared so reconnect will
     * not resubscribe.
     */
    public void unsubscribe() {
        synchronized (connectionLock) {
            this.lang = null;
            sendUnsubscribe();
        }
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
                log.info("News WebSocket connected");
                synchronized (connectionLock) {
                    webSocket = ws;
                    backoffMs = initialBackoffMs;
                    dropHandled = false;
                    reconnectAttempt = 0;
                    startHeartbeatLocked();
                    sendSubscribe();
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
                    if (text != null && text.startsWith("Param not json")) {
                        log.warn("News error frame code=515 msg={}", truncate(text));
                        safeAcceptError(InfowayApiException.ofWs(
                                WsErrorCode.PARAM_NOT_JSON.getCode(), text, null));
                        return;
                    }
                    log.debug("Non-JSON frame skipped: {}", truncate(text));
                    return;
                }
                int code = msg.get("code").getAsInt();

                if (code == WELCOME_CODE || code == WsCode.SUB_NEWS_ACK.getCode()) {
                    log.debug("News ack/welcome: {}", truncate(text));
                    return;
                }
                if (code == WsCode.PUSH_NEWS.getCode()) {
                    JsonElement data = msg.get("data");
                    JsonObject payload = data != null && data.isJsonObject() ? data.getAsJsonObject() : msg;
                    safeAccept(onNews, payload);
                    if (onNewsParsed != null && payload != null) {
                        try {
                            safeAcceptNews(onNewsParsed, Normalizer.news(payload));
                        } catch (Throwable t) {
                            log.error("User callback onNewsParsed threw — connection preserved", t);
                        }
                    }
                    return;
                }
                if (code == WsCode.UNSUB_ACK.getCode()
                        || code == WsCode.HEARTBEAT.getCode()
                        || code == WsCode.HEART_APPLY.getCode()) {
                    log.debug("News control frame code={}", code);
                    return;
                }
                Exception error = WsFrames.toError(msg, text);
                log.warn("News error frame code={} msg={}", code, error.getMessage());
                safeAcceptError(error);
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                if (response != null && response.code() == 401) {
                    String detail = readBody(response);
                    authRejected = true;
                    running = false;
                    clearSocket();
                    cancelReconnect();
                    okClient.dispatcher().cancelAll();
                    log.error("News WebSocket authentication failed (HTTP 401){}",
                            detail.isEmpty() ? "" : ": " + detail);
                    safeRun(onDisconnect, "onDisconnect");
                    safeAcceptError(new InfowayAuthException(
                            "News channel rejected the handshake with HTTP 401 — this API key has no "
                                    + "news entitlement (newsFlag), or the key is invalid"
                                    + (detail.isEmpty() ? "" : " (" + detail + ")")));
                    return;
                }
                if (!running) {
                    clearSocket();
                    return;
                }
                log.warn("News connection lost ({}), reconnecting...", t.getMessage());
                handleDrop(gen, t);
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                ws.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
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

    private void sendSubscribe() {
        WebSocket ws = webSocket;
        String language = lang;
        if (ws == null || language == null || language.isEmpty()) {
            log.debug("News subscribe deferred until the connection is established");
            return;
        }
        JsonObject msg = baseMessage(WsCode.SUB_NEWS.getCode());
        JsonObject data = new JsonObject();
        data.addProperty("lang", language);
        msg.add("data", data);
        ws.send(gson.toJson(msg));
    }

    private void sendUnsubscribe() {
        WebSocket ws = webSocket;
        if (ws == null) {
            log.debug("News unsubscribe deferred — socket not open");
            return;
        }
        ws.send(gson.toJson(baseMessage(WsCode.UNSUB_NEWS.getCode())));
    }

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
            safeAcceptError(new Exception("Max reconnect attempts reached", cause));
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
            log.warn("News reconnecting in {}ms (attempt {})...", delay, attempt);
            reconnectFuture = scheduler.schedule(() -> doConnect(attempt), delay, TimeUnit.MILLISECONDS);
            backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
        }
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
            try {
                WebSocket ws = webSocket;
                if (ws != null && running) {
                    ws.send("{\"code\":10010,\"trace\":\"" + Long.toHexString(System.nanoTime()) + "\"}");
                    log.debug("News heartbeat sent");
                }
            } catch (Throwable t) {
                log.warn("News heartbeat send failed: {}", t.getMessage());
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

    private JsonObject baseMessage(int code) {
        JsonObject msg = new JsonObject();
        msg.addProperty("code", code);
        msg.addProperty("trace", UUID.randomUUID().toString().replace("-", ""));
        return msg;
    }

    /** Close the connection and release resources. */
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

    private void emitInbound(String text) {
        if (printFrames) {
            log.info("News WS << {}", text);
            System.out.println(text);
        } else {
            log.debug("News WS << {}", truncate(text));
        }
        if (onFrame != null) {
            try {
                onFrame.accept(text);
            } catch (Throwable t) {
                log.error("User callback onFrame threw — swallowed", t);
            }
        }
    }

    public void setOnNews(Consumer<JsonObject> onNews) { this.onNews = onNews; }
    public void setOnNewsParsed(Consumer<NewsItem> onNewsParsed) { this.onNewsParsed = onNewsParsed; }
    public void setOnFrame(Consumer<String> onFrame) { this.onFrame = onFrame; }
    public void setOnError(Consumer<Exception> onError) { this.onError = onError; }
    public void setOnReconnect(Runnable onReconnect) { this.onReconnect = onReconnect; }
    public void setOnDisconnect(Runnable onDisconnect) { this.onDisconnect = onDisconnect; }

    private void safeAccept(Consumer<JsonObject> cb, JsonObject msg) {
        if (cb == null) return;
        try {
            cb.accept(msg);
        } catch (Throwable t) {
            log.error("User callback onNews threw — connection preserved", t);
        }
    }

    private static void safeAcceptNews(Consumer<NewsItem> cb, NewsItem item) {
        if (cb == null || item == null) {
            return;
        }
        cb.accept(item);
    }

    private void safeAcceptError(Exception e) {
        if (onError == null) return;
        try {
            onError.accept(e);
        } catch (Throwable t) {
            log.error("User callback onError threw — swallowed", t);
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

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() > 200 ? text.substring(0, 200) : text;
    }

    private static String readBody(Response response) {
        try {
            return response.body() != null ? truncate(response.body().string().trim()) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Create a new news WebSocket builder.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link InfowayNewsWebSocket}. */
    public static class Builder {
        private String apiKey;
        private String baseUrl = DEFAULT_NEWS_URL;
        private String lang = DEFAULT_LANG;
        private int maxReconnectAttempts = 0;
        private Consumer<JsonObject> onNews;
        private Consumer<NewsItem> onNewsParsed;
        private Consumer<String> onFrame;
        private Consumer<Exception> onError;
        private Runnable onReconnect;
        private Runnable onDisconnect;
        private boolean printFrames = false;

        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder lang(String lang) { this.lang = lang; return this; }
        public Builder lang(NewsLang lang) {
            this.lang = lang != null ? lang.value() : null;
            return this;
        }
        public Builder maxReconnectAttempts(int max) { this.maxReconnectAttempts = max; return this; }
        public Builder onNews(Consumer<JsonObject> cb) { this.onNews = cb; return this; }
        public Builder onNewsParsed(Consumer<NewsItem> cb) { this.onNewsParsed = cb; return this; }
        public Builder onFrame(Consumer<String> cb) { this.onFrame = cb; return this; }
        public Builder onError(Consumer<Exception> cb) { this.onError = cb; return this; }
        public Builder onReconnect(Runnable cb) { this.onReconnect = cb; return this; }
        public Builder onDisconnect(Runnable cb) { this.onDisconnect = cb; return this; }
        /** Print every inbound frame to stdout. Default {@code false}. */
        public Builder printFrames(boolean print) { this.printFrames = print; return this; }

        public InfowayNewsWebSocket build() {
            this.apiKey = ApiKeys.require(apiKey);
            return new InfowayNewsWebSocket(apiKey, baseUrl, lang, maxReconnectAttempts,
                    onNews, onNewsParsed, onFrame, onError, onReconnect, onDisconnect, printFrames);
        }
    }
}
