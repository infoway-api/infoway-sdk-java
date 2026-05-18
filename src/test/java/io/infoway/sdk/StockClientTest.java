package io.infoway.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.infoway.sdk.rest.StockClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class StockClientTest {

    private MockWebServer server;
    private HttpClient httpClient;
    private StockClient stockClient;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        httpClient = HttpClient.builder()
                .apiKey("test-key")
                .baseUrl(server.url("/").toString())
                .timeout(5)
                .maxRetries(1)
                .build();
        stockClient = new StockClient(httpClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        httpClient.close();
        server.shutdown();
    }

    @Test
    void getTradeCallsCorrectEndpoint() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"ret\":200,\"msg\":\"success\",\"data\":[{\"symbol\":\"AAPL.US\",\"price\":\"150.00\"}]}")
                .addHeader("Content-Type", "application/json"));

        JsonElement result = stockClient.getTrade("AAPL.US");

        assertNotNull(result);
        assertTrue(result.isJsonArray());
        JsonArray arr = result.getAsJsonArray();
        assertEquals(1, arr.size());
        assertEquals("AAPL.US", arr.get(0).getAsJsonObject().get("symbol").getAsString());

        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/stock/batch_trade/AAPL.US", request.getPath());
    }

    @Test
    void getTradeMultipleCodes() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"ret\":200,\"msg\":\"success\",\"data\":[{\"symbol\":\"AAPL.US\"},{\"symbol\":\"TSLA.US\"}]}")
                .addHeader("Content-Type", "application/json"));

        JsonElement result = stockClient.getTrade("AAPL.US,TSLA.US");

        assertTrue(result.isJsonArray());
        assertEquals(2, result.getAsJsonArray().size());

        RecordedRequest request = server.takeRequest();
        assertEquals("/stock/batch_trade/AAPL.US,TSLA.US", request.getPath());
    }

    @Test
    void getDepthCallsCorrectEndpoint() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"ret\":200,\"msg\":\"success\",\"data\":[{\"symbol\":\"AAPL.US\",\"asks\":[],\"bids\":[]}]}")
                .addHeader("Content-Type", "application/json"));

        JsonElement result = stockClient.getDepth("AAPL.US");

        assertNotNull(result);
        RecordedRequest request = server.takeRequest();
        assertEquals("/stock/batch_depth/AAPL.US", request.getPath());
    }

    @Test
    void getKlineCallsCorrectEndpoint() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"ret\":200,\"msg\":\"success\",\"data\":[{\"symbol\":\"AAPL.US\",\"klines\":[]}]}")
                .addHeader("Content-Type", "application/json"));

        JsonElement result = stockClient.getKline("AAPL.US", KlineType.DAY, 100);

        assertNotNull(result);
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/stock/v2/batch_kline", request.getPath());
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"codes\":\"AAPL.US\""));
        assertTrue(body.contains("\"klineType\":8"));
        assertTrue(body.contains("\"klineNum\":100"));
    }

    @Test
    void getKlineWithIntType() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"ret\":200,\"msg\":\"success\",\"data\":[]}")
                .addHeader("Content-Type", "application/json"));

        stockClient.getKline("BTCUSDT", 5, 50);

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"klineType\":5"));
        assertTrue(body.contains("\"klineNum\":50"));
    }

    @Test
    void infowayClientBuilderCreatesAllSubClients() {
        InfowayClient client = InfowayClient.builder()
                .apiKey("test-key")
                .baseUrl(server.url("/").toString())
                .build();

        assertNotNull(client.stock());
        assertNotNull(client.crypto());
        assertNotNull(client.japan());
        assertNotNull(client.india());
        assertNotNull(client.common());
        assertNotNull(client.basic());
        assertNotNull(client.market());
        assertNotNull(client.plate());
        assertNotNull(client.stockInfo());

        client.close();
    }

    @Test
    void klineTypeEnumValues() {
        assertEquals(1, KlineType.MIN_1.getValue());
        assertEquals(8, KlineType.DAY.getValue());
        assertEquals(12, KlineType.YEAR.getValue());
        assertEquals(KlineType.HOUR_4, KlineType.fromValue(7));
    }

    @Test
    void wsCodeEnumValues() {
        // outbound subscribe
        assertEquals(10000, WsCode.SUB_TRADE.getCode());
        assertEquals(10003, WsCode.SUB_DEPTH.getCode());
        assertEquals(10006, WsCode.SUB_KLINE.getCode());
        // inbound acks
        assertEquals(10001, WsCode.SUB_TRADE_ACK.getCode());
        assertEquals(10004, WsCode.SUB_DEPTH_ACK.getCode());
        assertEquals(10007, WsCode.SUB_KLINE_ACK.getCode());
        // inbound pushes — the real data
        assertEquals(10002, WsCode.PUSH_TRADE.getCode());
        assertEquals(10005, WsCode.PUSH_DEPTH.getCode());
        assertEquals(10008, WsCode.PUSH_KLINE.getCode());
        // outbound unsubscribe (11000 range)
        assertEquals(11000, WsCode.UNSUB_TRADE.getCode());
        assertEquals(11001, WsCode.UNSUB_DEPTH.getCode());
        assertEquals(11002, WsCode.UNSUB_KLINE.getCode());
        assertEquals(11010, WsCode.UNSUB_ACK.getCode());
        // heartbeat
        assertEquals(10010, WsCode.HEARTBEAT.getCode());
        // lookup
        assertEquals(WsCode.PUSH_DEPTH, WsCode.fromCode(10005));
        assertEquals(WsCode.PUSH_TRADE, WsCode.fromCode(10002));
        assertNull(WsCode.fromCode(99999));
    }
}
