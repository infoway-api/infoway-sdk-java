package io.infoway.sdk;

import com.google.gson.JsonObject;
import io.infoway.sdk.exception.InfowayApiException;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reconnect / subscribe / disconnect cases the protocol tests never drove:
 * one reconnect per drop, unsubscribe not replayed, user close stays down,
 * first open is not onReconnect, one heartbeat timer per session.
 */
class WebSocketLifecycleTest {

    private MockWebServer server;
    private InfowayWebSocket ws;

    private static final class ServerSide extends WebSocketListener {
        final List<String> received = new CopyOnWriteArrayList<>();
        final CountDownLatch opened = new CountDownLatch(1);
        volatile WebSocket socket;

        @Override public void onOpen(WebSocket webSocket, Response response) {
            socket = webSocket;
            opened.countDown();
        }

        @Override public void onMessage(WebSocket webSocket, String text) {
            received.add(text);
        }

        @Override public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(1000, null);
        }

        boolean awaitOpen() throws InterruptedException {
            return opened.await(5, TimeUnit.SECONDS);
        }

        void drop() {
            WebSocket ws = socket;
            if (ws != null) {
                try {
                    ws.close(1001, "drop");
                } catch (Exception ignored) {
                    // already gone
                }
            }
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

    private InfowayWebSocket newClient() {
        InfowayWebSocket client = InfowayWebSocket.builder()
                .apiKey("test-key")
                .business(WsBusiness.CRYPTO)
                .printFrames(false)
                .baseUrl(server.url("/ws").toString())
                .build();
        client.testTiming(80, 20);
        return client;
    }

    private static void awaitUntil(BooleanSupplier pred) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < deadline) {
            if (pred.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        fail("condition not met");
    }

    private static List<Integer> codes(List<String> frames) {
        List<Integer> out = new ArrayList<>();
        for (String raw : frames) {
            try {
                JsonObject obj = com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
                if (obj.has("code")) {
                    out.add(obj.get("code").getAsInt());
                }
            } catch (Exception ignored) {
                // skip
            }
        }
        return out;
    }

    private static long count(List<Integer> codes, int code) {
        return codes.stream().filter(c -> c == code).count();
    }

    @Test
    void dropReconnectsOnceAndResubscribes() throws Exception {
        ServerSide first = upgrade();
        ServerSide second = upgrade();
        AtomicInteger reconnects = new AtomicInteger();
        AtomicInteger disconnects = new AtomicInteger();
        List<JsonObject> ticks = new CopyOnWriteArrayList<>();

        ws = newClient();
        ws.setOnReconnect(reconnects::incrementAndGet);
        ws.setOnDisconnect(disconnects::incrementAndGet);
        ws.setOnTrade(ticks::add);
        ws.subscribeTrade("BTCUSDT");
        ws.connect();

        assertTrue(first.awaitOpen());
        awaitUntil(() -> count(codes(first.received), WsCode.SUB_TRADE.getCode()) == 1);
        assertEquals(0, reconnects.get());
        assertEquals(1, ws.heartbeatStarts);

        first.drop();
        assertTrue(second.awaitOpen());
        awaitUntil(() -> count(codes(second.received), WsCode.SUB_TRADE.getCode()) >= 1);
        awaitUntil(() -> reconnects.get() == 1);
        assertEquals(1, disconnects.get());
        assertEquals(2, ws.heartbeatStarts);

        second.socket.send("{\"code\":10002,\"data\":{\"s\":\"BTCUSDT\",\"p\":\"1\"}}");
        awaitUntil(() -> ticks.size() == 1);
        assertEquals("BTCUSDT", ticks.get(0).get("s").getAsString());

        Thread.sleep(80);
        assertEquals(1, reconnects.get());
    }

    @Test
    void userCloseDoesNotReconnectOrDisconnect() throws Exception {
        ServerSide first = upgrade();
        upgrade();
        AtomicInteger reconnects = new AtomicInteger();
        AtomicInteger disconnects = new AtomicInteger();
        ws = newClient();
        ws.setOnReconnect(reconnects::incrementAndGet);
        ws.setOnDisconnect(disconnects::incrementAndGet);
        ws.connect();
        assertTrue(first.awaitOpen());
        ws.close();
        ws = null;
        Thread.sleep(150);
        assertEquals(0, disconnects.get());
        assertEquals(0, reconnects.get());
    }

    @Test
    void unsubscribeIsForgottenAcrossReconnect() throws Exception {
        ServerSide first = upgrade();
        ServerSide second = upgrade();
        ws = newClient();
        ws.connect();
        assertTrue(first.awaitOpen());
        ws.subscribeTrade("BTCUSDT");
        ws.subscribeDepth("BTCUSDT");
        awaitUntil(() -> codes(first.received).contains(WsCode.SUB_DEPTH.getCode()));
        ws.unsubscribeTrade("BTCUSDT");
        awaitUntil(() -> codes(first.received).contains(WsCode.UNSUB_TRADE.getCode()));

        first.drop();
        assertTrue(second.awaitOpen());
        awaitUntil(() -> !second.received.isEmpty());
        Thread.sleep(40);
        List<Integer> replayed = codes(second.received);
        assertTrue(replayed.contains(WsCode.SUB_DEPTH.getCode()));
        assertFalse(replayed.contains(WsCode.SUB_TRADE.getCode()));
    }

    @Test
    void klineIntervalIsolationSurvivesReconnect() throws Exception {
        ServerSide first = upgrade();
        ServerSide second = upgrade();
        ws = newClient();
        ws.connect();
        assertTrue(first.awaitOpen());
        ws.subscribeKline("BTCUSDT", KlineType.MIN_1);
        ws.subscribeKline("BTCUSDT", KlineType.DAY);
        // Both subscribes must be on the wire first: awaitOpen() is the server-side latch, so the
        // client socket may still be null here. Subscribes survive that window (onOpen replays
        // them), an unsubscribe would just be dropped.
        awaitUntil(() -> count(codes(first.received), WsCode.SUB_KLINE.getCode()) >= 2);
        ws.unsubscribeKline("BTCUSDT", KlineType.MIN_1);
        awaitUntil(() -> codes(first.received).contains(WsCode.UNSUB_KLINE.getCode()));

        first.drop();
        assertTrue(second.awaitOpen());
        awaitUntil(() -> !second.received.isEmpty());
        Thread.sleep(40);
        List<String> klineSubs = second.received.stream()
                .filter(raw -> raw.contains("\"code\":" + WsCode.SUB_KLINE.getCode())
                        || raw.contains("\"code\": " + WsCode.SUB_KLINE.getCode()))
                .toList();
        assertEquals(1, klineSubs.size());
        assertTrue(klineSubs.get(0).contains("\"type\":8") || klineSubs.get(0).contains("\"type\": 8"));
        assertFalse(klineSubs.get(0).contains("\"type\":1") && !klineSubs.get(0).contains("\"type\":8"));
    }

    @Test
    void subscribeWhileReconnectingIsFlushed() throws Exception {
        ServerSide first = upgrade();
        ServerSide second = upgrade();
        ws = newClient();
        ws.connect();
        assertTrue(first.awaitOpen());
        first.drop();
        ws.subscribeTrade("ETHUSDT");
        assertTrue(second.awaitOpen());
        awaitUntil(() -> codes(second.received).contains(WsCode.SUB_TRADE.getCode()));
        assertTrue(second.received.stream().anyMatch(raw -> raw.contains("ETHUSDT")));
    }

    @Test
    void closeDuringBackoffCancelsReconnect() throws Exception {
        ServerSide first = upgrade();
        ws = newClient();
        ws.connect();
        assertTrue(first.awaitOpen());
        first.drop();
        Thread.sleep(5);
        ws.close();
        ws = null;
        Thread.sleep(150);
        int handshakes = 0;
        okhttp3.mockwebserver.RecordedRequest rec;
        while ((rec = server.takeRequest(0, TimeUnit.MILLISECONDS)) != null) {
            if (rec.getPath() != null && rec.getPath().startsWith("/ws")) {
                handshakes++;
            }
        }
        assertEquals(1, handshakes);
    }

    @Test
    void handshakeFailuresHonorMaxReconnect() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));
        List<Exception> errors = new CopyOnWriteArrayList<>();
        ws = InfowayWebSocket.builder()
                .apiKey("test-key")
                .business(WsBusiness.CRYPTO)
                .maxReconnectAttempts(1)
                .baseUrl(server.url("/ws").toString())
                .onError(errors::add)
                .build();
        ws.testTiming(80, 20);
        ws.connect();
        awaitUntil(() -> errors.stream().anyMatch(e ->
                e.getMessage() != null && e.getMessage().contains("Max reconnect")));
        Thread.sleep(80);
        int handshakes = 0;
        okhttp3.mockwebserver.RecordedRequest rec;
        while ((rec = server.takeRequest(0, TimeUnit.MILLISECONDS)) != null) {
            if (rec.getPath() != null && rec.getPath().startsWith("/ws")) {
                handshakes++;
            }
        }
        assertEquals(2, handshakes);
    }

    @Test
    void newsDropReplaysLangAndUserCloseStaysDown() throws Exception {
        ServerSide first = new ServerSide();
        ServerSide second = new ServerSide();
        server.enqueue(new MockResponse().withWebSocketUpgrade(first));
        server.enqueue(new MockResponse().withWebSocketUpgrade(second));

        AtomicInteger reconnects = new AtomicInteger();
        AtomicInteger disconnects = new AtomicInteger();
        InfowayNewsWebSocket news = InfowayNewsWebSocket.builder()
                .apiKey("test-key")
                .lang(NewsLang.EN)
                .baseUrl(server.url("/news").toString())
                .onReconnect(reconnects::incrementAndGet)
                .onDisconnect(disconnects::incrementAndGet)
                .build();
        news.testTiming(80, 20);
        try {
            news.connect();
            assertTrue(first.awaitOpen());
            awaitUntil(() -> first.received.stream().anyMatch(raw -> raw.contains("\"lang\":\"en\"")));
            assertEquals(0, reconnects.get());
            assertEquals(1, news.heartbeatStarts);

            first.drop();
            assertTrue(second.awaitOpen());
            awaitUntil(() -> second.received.stream().anyMatch(raw -> raw.contains("\"lang\":\"en\"")));
            awaitUntil(() -> reconnects.get() == 1);
            assertEquals(1, disconnects.get());
            assertEquals(2, news.heartbeatStarts);

            news.unsubscribe();
            Thread.sleep(40);
            assertEquals(1, reconnects.get());
        } finally {
            news.close();
        }
    }

    @Test
    void newsAlreadyConnectedDoesNotReconnect() throws Exception {
        ServerSide first = upgrade();
        List<Exception> errors = new CopyOnWriteArrayList<>();
        AtomicInteger reconnects = new AtomicInteger();
        InfowayNewsWebSocket news = InfowayNewsWebSocket.builder()
                .apiKey("test-key")
                .lang(NewsLang.EN)
                .baseUrl(server.url("/news").toString())
                .onError(errors::add)
                .onReconnect(reconnects::incrementAndGet)
                .build();
        news.testTiming(80, 20);
        try {
            news.connect();
            assertTrue(first.awaitOpen());
            assertNotNull(first.socket);
            first.socket.send("{\"code\":520,\"msg\":\"apikey already connected\"}");
            awaitUntil(() -> errors.stream().anyMatch(e ->
                    e instanceof InfowayApiException && ((InfowayApiException) e).getRet() == 520));
            Thread.sleep(200);
            assertEquals(0, reconnects.get());
            assertEquals(1, server.getRequestCount());
        } finally {
            news.close();
        }
    }

    @Test
    void quoteConnectionCapDoesNotReconnect() throws Exception {
        ServerSide first = upgrade();
        List<Exception> errors = new CopyOnWriteArrayList<>();
        AtomicInteger reconnects = new AtomicInteger();
        ws = newClient();
        ws.setOnError(errors::add);
        ws.setOnReconnect(reconnects::incrementAndGet);
        ws.connect();
        assertTrue(first.awaitOpen());
        assertNotNull(first.socket);
        first.socket.send("{\"code\":512,\"msg\":\"WebSocket connections exceeds the limit：10\"}");
        awaitUntil(() -> errors.stream().anyMatch(e ->
                e instanceof InfowayApiException && ((InfowayApiException) e).getRet() == 512));
        Thread.sleep(200);
        assertEquals(0, reconnects.get());
        assertEquals(1, server.getRequestCount());
    }
}
