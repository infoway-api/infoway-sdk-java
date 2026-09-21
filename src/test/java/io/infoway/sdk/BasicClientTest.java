package io.infoway.sdk;

import com.google.gson.JsonElement;
import io.infoway.sdk.rest.BasicClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * All five basic.* methods sent the wrong query parameters and one used a path that
 * 404s (design doc §2). Every expectation below was verified against production on
 * 2026-08-15; the response bodies are the captures in fixtures/rest.
 */
class BasicClientTest {

    private MockWebServer server;
    private HttpClient httpClient;
    private BasicClient basic;

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
        basic = new BasicClient(httpClient);
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
    void getSymbolsSendsTypeNotMarket() throws Exception {
        enqueue("rest/symbols.json");

        JsonElement result = basic.getSymbols("STOCK_US");

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().startsWith("/common/basic/symbols?"), request.getPath());
        assertTrue(request.getPath().contains("type=STOCK_US"), request.getPath());
        assertFalse(request.getPath().contains("market="),
                "market= is what produced HTTP 400 Required parameter 'type' is not present.");
        assertEquals("AAPL.US",
                result.getAsJsonArray().get(0).getAsJsonObject().get("symbol").getAsString());
    }

    @Test
    void getSymbolsAcceptsSymbolTypeEnum() throws Exception {
        enqueue("rest/symbols.json");

        basic.getSymbols(SymbolType.STOCK_US);

        assertTrue(server.takeRequest().getPath().contains("type=STOCK_US"));
    }

    @Test
    void getSymbolsCanFilterBySymbols() throws Exception {
        enqueue("rest/symbols.json");

        basic.getSymbols("STOCK_US", "AAPL.US");

        String path = server.takeRequest().getPath();
        assertTrue(path.contains("type=STOCK_US"), path);
        assertTrue(path.contains("symbols=AAPL.US"), path);
    }

    @Test
    void getSymbolInfoSendsTypeAndSymbols() throws Exception {
        enqueue("rest/symbol_info.json");

        JsonElement result = basic.getSymbolInfo("STOCK_US", "AAPL.US");

        String path = server.takeRequest().getPath();
        assertTrue(path.startsWith("/common/basic/symbols/info?"), path);
        assertTrue(path.contains("type=STOCK_US"), path);
        assertTrue(path.contains("symbols=AAPL.US"), path);
        assertFalse(path.contains("codes="), "codes= produced HTTP 400 Required parameter 'symbols'");

        assertEquals("NASD",
                result.getAsJsonArray().get(0).getAsJsonObject().get("exchange").getAsString());
    }

    @Test
    void getAdjustmentFactorsSendsSymbolMarketAndDayRange() throws Exception {
        enqueue("rest/adjustment_factors.json");

        JsonElement result = basic.getAdjustmentFactors("AAPL.US", "US", "20260801", "20260815");

        String path = server.takeRequest().getPath();
        assertTrue(path.startsWith("/common/basic/symbols/adjustment_factors?"), path);
        assertTrue(path.contains("symbol=AAPL.US"), path);
        assertTrue(path.contains("market=US"), path);
        assertTrue(path.contains("beginDay=20260801"), path);
        assertTrue(path.contains("endDay=20260815"), path);
        assertFalse(path.contains("codes="), path);

        assertEquals("20260803",
                result.getAsJsonArray().get(0).getAsJsonObject().get("trade_date").getAsString());
    }

    @Test
    void getTradingDaysSendsDayRange() throws Exception {
        enqueue("rest/trading_days.json");

        JsonElement result = basic.getTradingDays("US", "20260801", "20260815");

        String path = server.takeRequest().getPath();
        assertTrue(path.startsWith("/common/basic/markets/trading_days?"), path);
        assertTrue(path.contains("market=US"), path);
        assertTrue(path.contains("beginDay=20260801"), path);
        assertTrue(path.contains("endDay=20260815"), path);

        // data is an object, not an array
        assertEquals(10, result.getAsJsonObject().getAsJsonArray("trade_days").size());
    }

    @Test
    void getTradingScheduleUsesTradingSchedulePath() throws Exception {
        enqueue("rest/trading_schedule_first_entry.json");

        JsonElement result = basic.getTradingSchedule("US");

        String path = server.takeRequest().getPath();
        assertEquals("/common/basic/markets/trading_schedule", path);
        assertFalse(path.contains("trading_hours"), "/markets/trading_hours is a 404 on production");
        assertFalse(path.contains("market="), "server ignores market; do not send it");

        assertEquals("NGAS",
                result.getAsJsonArray().get(0).getAsJsonObject().get("symbol").getAsString());
    }

    @Test
    void getTradingScheduleWithoutArgumentsHitsSamePath() throws Exception {
        enqueue("rest/trading_schedule_first_entry.json");

        basic.getTradingSchedule();

        assertEquals("/common/basic/markets/trading_schedule", server.takeRequest().getPath());
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedGetTradingHoursDelegatesToTradingSchedule() throws Exception {
        enqueue("rest/trading_schedule_first_entry.json");

        basic.getTradingHours("US");

        assertTrue(server.takeRequest().getPath().startsWith("/common/basic/markets/trading_schedule"));
    }

    @Test
    void getMarketsKeepsTheParameterlessMarketsEndpoint() throws Exception {
        enqueue("rest/trading_schedule_first_entry.json");

        basic.getMarkets();

        assertEquals("/common/basic/markets", server.takeRequest().getPath());
    }

    @Test
    void getStockDetailSendsTypeAndSingleSymbol() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"ret\":200,\"msg\":\"success\",\"data\":{\"symbol\":\"AAPL.US\",\"isin\":\"US0378331005\"}}")
                .addHeader("Content-Type", "application/json"));

        var result = basic.getStockDetail(SymbolType.STOCK_US, "AAPL.US");

        String path = server.takeRequest().getPath();
        assertTrue(path.startsWith("/common/basic/stock/detail?"), path);
        assertTrue(path.contains("type=STOCK_US"), path);
        assertTrue(path.contains("symbol=AAPL.US"), path);
        assertFalse(path.contains("symbols="), path);
        assertEquals("US0378331005", result.getAsJsonObject().get("isin").getAsString());
    }

    @Test
    void getTradingScheduleByTypeSendsScheduleType() throws Exception {
        enqueue("rest/trading_schedule_first_entry.json");
        basic.getTradingScheduleByType(ScheduleType.ENERGY);
        assertEquals("/common/basic/markets/trading_schedule?type=ENERGY",
                server.takeRequest().getPath());
    }

    @Test
    void getTradingScheduleByTypeRejectsEquitySymbolType() {
        assertThrows(IllegalArgumentException.class,
                () -> basic.getTradingScheduleByType(SymbolType.STOCK_US));
        assertEquals(0, server.getRequestCount());
    }
}
