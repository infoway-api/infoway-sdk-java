package io.infoway.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.infoway.sdk.model.Depth;
import io.infoway.sdk.model.Kline;
import io.infoway.sdk.model.Trade;
import io.infoway.sdk.rest.StockClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Market-data endpoints, asserted against the real payloads.
 *
 * <p>Until 0.2.0 these tests used invented bodies ({@code symbol}, {@code price},
 * {@code asks}/{@code bids}, {@code klines}) — none of those field names exist on the
 * wire, so the suite was green while the SDK was wrong.</p>
 */
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

    private void enqueue(String fixture) {
        server.enqueue(new MockResponse()
                .setBody(Fixtures.load(fixture))
                .addHeader("Content-Type", "application/json"));
    }

    @Test
    void getTradeReturnsRealTradeFields() throws Exception {
        enqueue("rest/trade_stock.json");

        JsonElement result = stockClient.getTrade("AAPL.US");

        assertTrue(result.isJsonArray());
        JsonArray arr = result.getAsJsonArray();
        assertEquals(1, arr.size());
        JsonObject tick = arr.get(0).getAsJsonObject();
        assertEquals("AAPL.US", tick.get("s").getAsString());
        assertEquals("305.771", tick.get("p").getAsString());
        assertEquals("305.771", tick.get("vw").getAsString());   // vw = turnover, not VWAP
        assertEquals(0, tick.get("td").getAsInt());

        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/stock/batch_trade/AAPL.US", request.getPath());
    }

    @Test
    void getTradeMultipleCodes() throws Exception {
        enqueue("rest/trade_stock.json");

        stockClient.getTrade("AAPL.US,TSLA.US");

        assertEquals("/stock/batch_trade/AAPL.US,TSLA.US", server.takeRequest().getPath());
    }

    @Test
    void getDepthReturnsTransposedColumns() throws Exception {
        enqueue("rest/depth_stock.json");

        JsonElement result = stockClient.getDepth("AAPL.US");

        JsonObject book = result.getAsJsonArray().get(0).getAsJsonObject();
        assertFalse(book.has("asks"), "production sends a/b");
        assertFalse(book.has("bids"), "production sends a/b");
        // a[0] = prices, a[1] = quantities
        assertEquals("305.800", book.getAsJsonArray("a").get(0).getAsJsonArray().get(0).getAsString());
        assertEquals("229", book.getAsJsonArray("a").get(1).getAsJsonArray().get(0).getAsString());

        assertEquals("/stock/batch_depth/AAPL.US", server.takeRequest().getPath());
    }

    @Test
    void getKlineReturnsRespListNesting() throws Exception {
        enqueue("rest/kline_stock.json");

        JsonElement result = stockClient.getKline("AAPL.US", KlineType.DAY, 100);

        JsonObject entry = result.getAsJsonArray().get(0).getAsJsonObject();
        assertEquals("AAPL.US", entry.get("s").getAsString());
        assertTrue(entry.has("respList"), "bars are nested under respList, never under 'klines'");
        JsonObject bar = entry.getAsJsonArray("respList").get(0).getAsJsonObject();
        assertEquals("1786737540", bar.get("t").getAsString(), "REST kline t is a string of seconds");
        assertEquals("0.03%", bar.get("pc").getAsString(), "REST calls change percent pc");

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
        enqueue("rest/kline_stock.json");

        stockClient.getKline("BTCUSDT", 5, 50);

        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"klineType\":5"));
        assertTrue(body.contains("\"klineNum\":50"));
    }

    @Test
    void getKlineSendsOptionalTimestamp() throws Exception {
        enqueue("rest/kline_stock.json");

        stockClient.getKline("AAPL.US", KlineType.MIN_1, 50, 1758553860L);

        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"timestamp\":1758553860"), body);
        assertTrue(body.contains("\"klineType\":1"), body);
    }

    @Test
    void koreaAndTaiwanUseTheirOwnPrefixes() throws Exception {
        enqueue("rest/trade_stock.json");
        InfowayClient client = InfowayClient.builder()
                .apiKey("test-key")
                .baseUrl(server.url("/").toString())
                .build();
        try {
            client.korea().getTrade("005930.KS");
            assertEquals("/korea/batch_trade/005930.KS", server.takeRequest().getPath());

            enqueue("rest/trade_stock.json");
            client.taiwan().getTrade("2330.TW");
            assertEquals("/taiwan/batch_trade/2330.TW", server.takeRequest().getPath());
        } finally {
            client.close();
        }
    }

    // ---- typed channel ---------------------------------------------------------

    @Test
    void getTradeParsedReturnsTypedTrades() {
        enqueue("rest/trade_stock.json");

        List<Trade> trades = stockClient.getTradeParsed("AAPL.US");

        assertEquals(1, trades.size());
        assertEquals("AAPL.US", trades.get(0).symbol());
        assertEquals(0, new BigDecimal("305.771").compareTo(trades.get(0).price()));
    }

    @Test
    void getDepthParsedReturnsPriceQuantityPairs() {
        enqueue("rest/depth_stock.json");

        List<Depth> books = stockClient.getDepthParsed("AAPL.US");

        assertEquals(0, new BigDecimal("305.800").compareTo(books.get(0).asks().get(0).price()));
        assertEquals(0, new BigDecimal("229").compareTo(books.get(0).asks().get(0).quantity()));
    }

    @Test
    void getKlineParsedFlattensBars() {
        enqueue("rest/kline_stock.json");

        List<Kline> bars = stockClient.getKlineParsed("AAPL.US", KlineType.MIN_1, 2);

        assertEquals(2, bars.size());
        assertEquals("AAPL.US", bars.get(0).symbol());
        assertEquals(0, new BigDecimal("0.0003").compareTo(bars.get(0).changePercent()));
    }

    // ---- wiring ----------------------------------------------------------------

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
        assertNotNull(client.korea());
        assertNotNull(client.taiwan());
        assertNotNull(client.financial());
        assertNotNull(client.packages());

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
        // news channel
        assertEquals(10020, WsCode.SUB_NEWS.getCode());
        assertEquals(10021, WsCode.SUB_NEWS_ACK.getCode());
        assertEquals(10022, WsCode.PUSH_NEWS.getCode());
        assertEquals(11020, WsCode.UNSUB_NEWS.getCode());
        // outbound unsubscribe (11000 range)
        assertEquals(11000, WsCode.UNSUB_TRADE.getCode());
        assertEquals(11001, WsCode.UNSUB_DEPTH.getCode());
        assertEquals(11002, WsCode.UNSUB_KLINE.getCode());
        assertEquals(11010, WsCode.UNSUB_ACK.getCode());
        // heartbeat
        assertEquals(10010, WsCode.HEARTBEAT.getCode());
        assertEquals(10011, WsCode.HEART_APPLY.getCode());
        assertEquals(506, WsErrorCode.PARAM_ERROR.getCode());
        assertEquals(507, WsErrorCode.PARAM_LOST.getCode());
        assertEquals(501, WsErrorCode.REQUEST_FREQUENCY_MIN_EXCEED.getCode());
        assertEquals(508, WsErrorCode.APIKEY_EXPIRED.getCode());
        assertEquals(508, RestErrorCode.PRODUCT_NOT_EXISTS.getCode());
        assertEquals(514, RestErrorCode.NO_PERMISSION.getCode());
        assertEquals(514, WsErrorCode.WS_URL_WRONG.getCode());
        assertNotEquals(RestErrorCode.fromCode(508).name(), WsErrorCode.fromCode(508).name());
        assertEquals(516, WsErrorCode.ALL_PRODUCTS_QUANTITY_EXCEED.getCode());
        assertEquals(520, WsErrorCode.PRODUCT_CODE_OR_ALREADY_CONNECTED.getCode());
        assertEquals("crypto", WsBusiness.CRYPTO.value());
        assertEquals("zh-CN", Lang.ZH_CN.value());
        assertEquals("zh-Hans", NewsLang.ZH_HANS.value());
        assertEquals("fq", PeriodType.FQ.value());
        assertEquals("ENERGY", ScheduleType.ENERGY.value());
        assertEquals("HK,US", Market.join(Market.HK, Market.US));
        assertTrue(WsErrorCode.isError(507));
        assertFalse(WsErrorCode.isError(10002));
        // lookup
        assertEquals(WsCode.PUSH_DEPTH, WsCode.fromCode(10005));
        assertEquals(WsCode.PUSH_TRADE, WsCode.fromCode(10002));
        assertNull(WsCode.fromCode(99999));
    }
}
