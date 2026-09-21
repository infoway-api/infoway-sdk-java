package io.infoway.sdk;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.infoway.sdk.exception.InfowayApiException;
import io.infoway.sdk.exception.InfowayAuthException;
import io.infoway.sdk.exception.InfowayRateLimitException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSocket behaviour against a local server that replays the frames production
 * really sends (design doc §3). Frames come from fixtures/ws, captured from
 * wss://data.infoway.io/ws?business=crypto.
 */
class WebSocketProtocolTest {

    private MockWebServer server;
    private InfowayWebSocket ws;
    private final Gson gson = new Gson();

    /** Records what the client sent and lets the test push frames back. */
    private static final class ServerSide extends WebSocketListener {
        final List<String> received = new CopyOnWriteArrayList<>();
        final CountDownLatch opened = new CountDownLatch(1);
        volatile WebSocket socket;

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            socket = webSocket;
            opened.countDown();
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            received.add(text);
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(1000, null);   // let MockWebServer.shutdown() complete
        }

        void send(String frame) {
            socket.send(frame);
        }

        boolean awaitOpen() throws InterruptedException {
            return opened.await(5, TimeUnit.SECONDS);
        }

        String waitForFrameContaining(String needle) throws InterruptedException {
            for (int i = 0; i < 100; i++) {
                for (String frame : received) {
                    if (frame.contains(needle)) {
                        return frame;
                    }
                }
                Thread.sleep(50);
            }
            return null;
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (ws != null) {
            ws.close();
        }
        server.shutdown();
    }

    private ServerSide upgrade() {
        ServerSide side = new ServerSide();
        server.enqueue(new MockResponse().withWebSocketUpgrade(side));
        return side;
    }

    /** Drain recorded requests; count only WebSocket handshakes. Ignores /json/version probes. */
    private int drainHandshakeCount(String prefix) throws InterruptedException {
        int count = 0;
        okhttp3.mockwebserver.RecordedRequest rec;
        while ((rec = server.takeRequest(0, TimeUnit.MILLISECONDS)) != null) {
            if (rec.getPath() != null && rec.getPath().startsWith(prefix)) {
                count++;
            }
        }
        return count;
    }

    private InfowayWebSocket.Builder clientBuilder() {
        return InfowayWebSocket.builder()
                .apiKey("test-key")
                .business(WsBusiness.CRYPTO)
                .printFrames(false)
                .baseUrl(server.url("/ws").toString());
    }

    // ---- §3.1 the very first frame on business=stock is plain text -------------

    @Test
    void plaintextGreetingIsSkippedAndConnectionSurvives() throws Exception {
        ServerSide side = upgrade();
        CountDownLatch trade = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        ws = clientBuilder()
                .onTrade(msg -> trade.countDown())
                .onError(error::set)
                .build();

        ws.connect();
        assertTrue(side.awaitOpen());
        side.send(Fixtures.load("ws/plaintext_greeting.txt"));
        side.send(Fixtures.load("ws/push_trade.json"));

        assertTrue(trade.await(5, TimeUnit.SECONDS), "push after the plaintext frame must still arrive");
        assertNull(error.get(), "a non-JSON frame must not be reported as an error");
    }

    // ---- §3.2 welcome frame is not an error -----------------------------------

    @Test
    void welcomeFrameIsNotAnError() throws Exception {
        ServerSide side = upgrade();
        AtomicReference<Exception> error = new AtomicReference<>();
        ws = clientBuilder().onError(error::set).build();

        ws.connect();
        assertTrue(side.awaitOpen());
        side.send(Fixtures.load("ws/welcome.json"));   // {"code":200,"msg":"ws connect success"}
        Thread.sleep(300);

        assertNull(error.get());
    }

    // ---- §3.4 callbacks receive data, aligned with the REST layer ---------------

    @Test
    void tradeCallbackReceivesUnwrappedData() throws Exception {
        ServerSide side = upgrade();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<JsonObject> got = new AtomicReference<>();
        ws = clientBuilder().onTrade(msg -> { got.set(msg); latch.countDown(); }).build();

        ws.connect();
        assertTrue(side.awaitOpen());
        side.send(Fixtures.load("ws/push_trade.json"));

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        JsonObject msg = got.get();
        assertEquals("BTCUSDT", msg.get("s").getAsString());
        assertEquals("63039", msg.get("p").getAsString());
        assertEquals(1786775227483L, msg.get("t").getAsLong());
        assertFalse(msg.has("code"), "the transport envelope must be stripped, like REST does");
    }

    @Test
    void depthCallbackReceivesUnwrappedTransposedBook() throws Exception {
        ServerSide side = upgrade();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<JsonObject> got = new AtomicReference<>();
        ws = clientBuilder().onDepth(msg -> { got.set(msg); latch.countDown(); }).build();

        ws.connect();
        assertTrue(side.awaitOpen());
        side.send(Fixtures.load("ws/push_depth.json"));

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        JsonObject msg = got.get();
        assertEquals("BTCUSDT", msg.get("s").getAsString());
        assertEquals("63039.00000000",
                msg.getAsJsonArray("a").get(0).getAsJsonArray().get(0).getAsString());
        assertFalse(msg.has("asks"), "production sends a/b, never asks/bids");
    }

    @Test
    void klineCallbackKeepsIntervalField() throws Exception {
        ServerSide side = upgrade();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<JsonObject> got = new AtomicReference<>();
        ws = clientBuilder().onKline(msg -> { got.set(msg); latch.countDown(); }).build();

        ws.connect();
        assertTrue(side.awaitOpen());
        side.send(Fixtures.load("ws/push_kline.json"));

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        JsonObject msg = got.get();
        assertEquals(1, msg.get("ty").getAsInt(), "kline callbacks must keep the interval");
        assertEquals("1786775220", msg.get("t").getAsString(), "kline t is a string of SECONDS");
        assertEquals("-0.01%", msg.get("pfr").getAsString(), "WS calls change percent pfr, REST calls it pc");
        assertFalse(msg.has("code"));
    }

    // ---- §3.5 unsubscribing a kline must name the interval ---------------------

    @Test
    void unsubscribeKlineSendsKlineTypes() throws Exception {
        ServerSide side = upgrade();
        ws = clientBuilder().build();

        ws.connect();
        assertTrue(side.awaitOpen());
        ws.subscribeKline("BTCUSDT", KlineType.MIN_1);
        assertNotNull(side.waitForFrameContaining("10006"));

        ws.unsubscribeKline("BTCUSDT", KlineType.MIN_1);

        String frame = side.waitForFrameContaining("11002");
        assertNotNull(frame, "unsubscribe frame never sent");
        JsonObject data = gson.fromJson(frame, JsonObject.class).getAsJsonObject("data");
        assertEquals("BTCUSDT", data.get("codes").getAsString());
        assertEquals("1", data.get("klineTypes").getAsString(),
                "without klineTypes the server drops every interval of that symbol");
    }

    // ---- subscriptions issued before the handshake completes --------------------

    @Test
    void subscriptionsIssuedBeforeConnectAreFlushedOnOpen() throws Exception {
        ServerSide side = upgrade();
        ws = clientBuilder().build();

        ws.subscribeTrade("BTCUSDT,ETHUSDT");   // before connect() — used to be dropped silently
        ws.connect();

        assertTrue(side.awaitOpen());
        String frame = side.waitForFrameContaining("10000");
        assertNotNull(frame, "subscription made before connect was silently dropped");
        assertEquals("BTCUSDT,ETHUSDT",
                gson.fromJson(frame, JsonObject.class).getAsJsonObject("data").get("codes").getAsString());
    }

    // ---- §3.3 auth failure happens in the HTTP handshake -----------------------

    @Test
    void handshake401RaisesAuthErrorAndStopsReconnecting() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401)
                .setBody("{\"message\":\"Invalid API key in request\"}"));
        CountDownLatch errored = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        ws = clientBuilder()
                .onError(e -> { error.set(e); errored.countDown(); })
                .build();

        ws.connect();

        assertTrue(errored.await(5, TimeUnit.SECONDS), "a 401 handshake must surface an error");
        assertInstanceOf(InfowayAuthException.class, error.get());
        assertEquals(1, drainHandshakeCount("/ws"), "one handshake then stop");
        Thread.sleep(2500);   // more than the 1s initial backoff
        assertEquals(0, drainHandshakeCount("/ws"),
                "must not keep hammering the gateway with an invalid key");
    }

    // ---- server error frames must not be swallowed -----------------------------

    @Test
    void serverErrorFrameIsReportedToOnError() throws Exception {
        ServerSide side = upgrade();
        CountDownLatch errored = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        ws = clientBuilder().onError(e -> { error.set(e); errored.countDown(); }).build();

        ws.connect();
        assertTrue(side.awaitOpen());
        side.send(Fixtures.load("ws/error_507.json"));

        assertTrue(errored.await(5, TimeUnit.SECONDS), "507 error frame was swallowed");
        InfowayApiException ex = assertInstanceOf(InfowayApiException.class, error.get());
        assertEquals(507, ex.getRet());
        assertEquals("PARAM_LOST", ex.getErrorName());
        assertTrue(ex.getMessage().contains("[507 PARAM_LOST]"));
        assertEquals("408e395555214a2e91b596e351b83c97", ex.getTraceId(),
                "error frames use traceId, acks use trace");
    }

    @Test
    void subscribeFailPrefixIsUnwrapped() throws Exception {
        ServerSide side = upgrade();
        CountDownLatch errored = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        ws = clientBuilder().onError(e -> { error.set(e); errored.countDown(); }).build();

        ws.connect();
        assertTrue(side.awaitOpen());
        side.send("Subscribe fail: {\"traceId\":\"t1\",\"code\":505,\"msg\":\"Single WS subscribe exceeds the limit 600\"}");

        assertTrue(errored.await(5, TimeUnit.SECONDS), "prefixed 505 was swallowed");
        InfowayApiException ex = assertInstanceOf(InfowayApiException.class, error.get());
        assertEquals(505, ex.getRet());
        assertEquals("PRODUCTS_QUANTITY_EXCEED", ex.getErrorName());
    }

    @Test
    void frequencyLimitIsRateLimitException() throws Exception {
        ServerSide side = upgrade();
        CountDownLatch errored = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        ws = clientBuilder().onError(e -> { error.set(e); errored.countDown(); }).build();

        ws.connect();
        assertTrue(side.awaitOpen());
        side.send("{\"traceId\":\"t2\",\"code\":501,\"msg\":\"Request frequency exceed the limit：60 times per minute\"}");

        assertTrue(errored.await(5, TimeUnit.SECONDS));
        InfowayRateLimitException ex = assertInstanceOf(InfowayRateLimitException.class, error.get());
        assertEquals(501, ex.getRet());
        assertEquals("REQUEST_FREQUENCY_MIN_EXCEED", ex.getErrorName());
    }

    @Test
    void ws508IsApikeyExpiredNotRestProductMissing() throws Exception {
        ServerSide side = upgrade();
        CountDownLatch errored = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        ws = clientBuilder().onError(e -> { error.set(e); errored.countDown(); }).build();

        ws.connect();
        assertTrue(side.awaitOpen());
        side.send("{\"traceId\":\"t8\",\"code\":508,\"msg\":\"The token permission has expired\"}");

        assertTrue(errored.await(5, TimeUnit.SECONDS));
        InfowayApiException ex = assertInstanceOf(InfowayApiException.class, error.get());
        assertEquals(508, ex.getRet());
        assertEquals("APIKEY_EXPIRED", ex.getErrorName());
        assertTrue(ex.getMessage().contains("[508 APIKEY_EXPIRED]"));
    }

    @Test
    void onFrameReceivesEveryInboundText() throws Exception {
        ServerSide side = upgrade();
        List<String> frames = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);
        ws = clientBuilder()
                .onFrame(text -> { frames.add(text); latch.countDown(); })
                .build();

        ws.connect();
        assertTrue(side.awaitOpen());
        side.send(Fixtures.load("ws/push_trade.json"));
        side.send("You have permission to subscribe to all market data");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(frames.stream().anyMatch(f -> f.contains("\"code\":10002")), frames.toString());
        assertTrue(frames.stream().anyMatch(f -> f.startsWith("You have permission")), frames.toString());
    }

    @Test
    void heartApplyIsNotAnError() throws Exception {
        ServerSide side = upgrade();
        AtomicReference<Exception> error = new AtomicReference<>();
        ws = clientBuilder().onError(error::set).build();

        ws.connect();
        assertTrue(side.awaitOpen());
        side.send("{\"code\":10011,\"trace\":\"abc\",\"msg\":\"ok\"}");
        Thread.sleep(300);
        assertNull(error.get(), "heartbeat ack 10011 must not be treated as an error");
    }

    // ---- subscribe frames merge codes: one frame, not one per symbol ------------

    @Test
    void subscribeSendsASingleFrameForAllCodes() throws Exception {
        ServerSide side = upgrade();
        ws = clientBuilder().build();

        ws.connect();
        assertTrue(side.awaitOpen());
        ws.subscribeTrade("BTCUSDT,ETHUSDT,SOLUSDT");
        assertNotNull(side.waitForFrameContaining("10000"));
        Thread.sleep(200);

        assertEquals(1, side.received.size(),
                "the gateway allows 60 frames/minute/connection — codes must be merged, got "
                        + side.received);
    }

    @Test
    void subscribeTradeCanAskForTy() throws Exception {
        ServerSide side = upgrade();
        ws = clientBuilder().build();

        ws.connect();
        assertTrue(side.awaitOpen());
        ws.subscribeTrade("BTCUSDT", true);

        String frame = side.waitForFrameContaining("10000");
        assertNotNull(frame);
        JsonObject data = gson.fromJson(frame, JsonObject.class).getAsJsonObject("data");
        assertEquals("BTCUSDT", data.get("codes").getAsString());
        assertTrue(data.get("includeTy").getAsBoolean());
    }
}
