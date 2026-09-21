package io.infoway.sdk;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.infoway.sdk.exception.InfowayAuthException;
import io.infoway.sdk.model.NewsItem;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
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
 * News is a separate endpoint with its own codes and its own entitlement
 * (design doc §4): wss://data.infoway.io/news?apikey=… , 10020/10021/10022.
 */
class NewsWebSocketTest {

    private MockWebServer server;
    private InfowayNewsWebSocket ws;
    private final Gson gson = new Gson();

    private static final class ServerSide extends WebSocketListener {
        final List<String> received = new CopyOnWriteArrayList<>();
        final CountDownLatch opened = new CountDownLatch(1);
        volatile WebSocket socket;

        @Override public void onOpen(WebSocket webSocket, Response response) {
            socket = webSocket;
            opened.countDown();
        }
        @Override public void onMessage(WebSocket webSocket, String text) { received.add(text); }
        @Override public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(1000, null);
        }
        void send(String frame) { socket.send(frame); }
        boolean awaitOpen() throws InterruptedException { return opened.await(5, TimeUnit.SECONDS); }
        String waitForFrameContaining(String needle) throws InterruptedException {
            for (int i = 0; i < 100; i++) {
                for (String frame : received) {
                    if (frame.contains(needle)) return frame;
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
        if (ws != null) ws.close();
        server.shutdown();
    }

    private ServerSide upgrade() {
        ServerSide side = new ServerSide();
        server.enqueue(new MockResponse().withWebSocketUpgrade(side));
        return side;
    }

    /** Drain recorded requests; count only news handshakes. Ignores /json/version probes. */
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

    private InfowayNewsWebSocket.Builder clientBuilder() {
        return InfowayNewsWebSocket.builder()
                .apiKey("test-key")
                .printFrames(false)
                .baseUrl(server.url("/news").toString());
    }

    @Test
    void connectsToNewsPathWithApikeyQueryAndNoBusiness() throws Exception {
        ServerSide side = upgrade();
        ws = clientBuilder().build();

        ws.connect();
        assertTrue(side.awaitOpen());

        String path = server.takeRequest().getPath();
        assertTrue(path.startsWith("/news?"), path);
        assertTrue(path.contains("apikey=test-key"), path);
        assertFalse(path.contains("business="), "news does not take a business parameter");
    }

    @Test
    void subscribeSendsCode10020WithLang() throws Exception {
        ServerSide side = upgrade();
        ws = clientBuilder().build();

        ws.connect();
        assertTrue(side.awaitOpen());
        ws.subscribe("zh-Hans");

        String frame = side.waitForFrameContaining("10020");
        assertNotNull(frame, "no 10020 subscribe frame");
        JsonObject msg = gson.fromJson(frame, JsonObject.class);
        assertEquals(10020, msg.get("code").getAsInt());
        assertNotNull(msg.get("trace"));
        assertEquals("zh-Hans", msg.getAsJsonObject("data").get("lang").getAsString());
    }

    @Test
    void languageFromBuilderIsSubscribedOnOpen() throws Exception {
        ServerSide side = upgrade();
        ws = clientBuilder().lang("ja").build();

        ws.connect();
        assertTrue(side.awaitOpen());

        String frame = side.waitForFrameContaining("10020");
        assertNotNull(frame);
        assertEquals("ja", gson.fromJson(frame, JsonObject.class)
                .getAsJsonObject("data").get("lang").getAsString());
    }

    @Test
    void pushDeliversUnwrappedNewsData() throws Exception {
        ServerSide side = upgrade();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<JsonObject> got = new AtomicReference<>();
        ws = clientBuilder().onNews(item -> { got.set(item); latch.countDown(); }).build();

        ws.connect();
        assertTrue(side.awaitOpen());
        side.send(Fixtures.load("ws/news_push_docs_derived.json"));

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        JsonObject item = got.get();
        assertFalse(item.has("code"), "callbacks receive data, like every other channel");
        assertEquals("AAPL.US", item.getAsJsonArray("symbols").get(0).getAsString());
        assertEquals(1786775220L, item.get("published").getAsLong(), "published is in SECONDS");
        assertNotNull(item.get("dk"), "dk is the dedup key");
    }

    @Test
    void onNewsParsedReceivesTypedItem() throws Exception {
        ServerSide side = upgrade();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<NewsItem> got = new AtomicReference<>();
        ws = clientBuilder().onNewsParsed(item -> { got.set(item); latch.countDown(); }).build();

        ws.connect();
        assertTrue(side.awaitOpen());
        side.send(Fixtures.load("ws/news_push_docs_derived.json"));

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals("AAPL.US", got.get().symbols().get(0));
        assertEquals("Apple announces record quarterly revenue", got.get().title());
    }

    @Test
    void ackIsNotReportedAsError() throws Exception {
        ServerSide side = upgrade();
        AtomicReference<Exception> error = new AtomicReference<>();
        ws = clientBuilder().onError(error::set).build();

        ws.connect();
        assertTrue(side.awaitOpen());
        side.send("{\"code\":10021,\"trace\":\"abc\",\"msg\":\"ok\",\"data\":{\"lang\":\"en\"}}");
        side.send(Fixtures.load("ws/welcome.json"));
        Thread.sleep(300);

        assertNull(error.get());
    }

    @Test
    void handshake401ExplainsMissingNewsEntitlement() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401)
                .setBody("{\"message\":\"The request is rejected, please check the consumer_group_id for this request\"}"));
        CountDownLatch errored = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        ws = clientBuilder().onError(e -> { error.set(e); errored.countDown(); }).build();

        ws.connect();

        assertTrue(errored.await(5, TimeUnit.SECONDS), "a 401 handshake must surface an error");
        InfowayAuthException ex = assertInstanceOf(InfowayAuthException.class, error.get());
        assertTrue(ex.getMessage().toLowerCase().contains("news"),
                "the message must say the key has no news entitlement, got: " + ex.getMessage());

        assertEquals(1, drainHandshakeCount("/news"), "one handshake then stop");
        Thread.sleep(2500);
        assertEquals(0, drainHandshakeCount("/news"), "must not reconnect on 401");
    }

    @Test
    void subscribeBeforeConnectIsFlushedOnOpen() throws Exception {
        ServerSide side = upgrade();
        ws = clientBuilder().build();

        ws.subscribe("en");
        ws.connect();

        assertTrue(side.awaitOpen());
        assertNotNull(side.waitForFrameContaining("10020"));
    }

    @Test
    void unsubscribeSendsCode11020AndClearsLang() throws Exception {
        ServerSide side = upgrade();
        ws = clientBuilder().lang("en").build();

        ws.connect();
        assertTrue(side.awaitOpen());
        assertNotNull(side.waitForFrameContaining("10020"));

        ws.unsubscribe();
        String frame = side.waitForFrameContaining("11020");
        assertNotNull(frame, "no 11020 unsubscribe frame");
        assertEquals(11020, gson.fromJson(frame, JsonObject.class).get("code").getAsInt());
    }
}
