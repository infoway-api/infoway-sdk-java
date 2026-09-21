package io.infoway.sdk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.infoway.sdk.model.Kline;
import io.infoway.sdk.model.Trade;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live contract test — guards against the server silently changing field names again.
 *
 * <p>Skipped unless both environment variables are set:</p>
 * <pre>
 *   INFOWAY_LIVE_TESTS=1 INFOWAY_API_KEY=&lt;key&gt; mvn test -Dtest=LiveContractTest
 * </pre>
 *
 * <p>Uses the crypto channel on purpose: it trades 24/7, so the test does not depend on
 * an equity market being open.</p>
 */
@EnabledIfEnvironmentVariable(named = "INFOWAY_LIVE_TESTS", matches = "1")
@EnabledIfEnvironmentVariable(named = "INFOWAY_API_KEY", matches = ".+")
class LiveContractTest {

    private static InfowayClient client;
    private static String apiKey;

    @BeforeAll
    static void setUp() {
        apiKey = System.getenv("INFOWAY_API_KEY");
        client = InfowayClient.builder().apiKey(apiKey).build();
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void restTradeKeepsItsFieldNames() {
        JsonElement data = client.crypto().getTrade("BTCUSDT");

        JsonObject tick = data.getAsJsonArray().get(0).getAsJsonObject();
        assertTrue(tick.has("s"), "trade payload lost 's'");
        assertTrue(tick.has("p"), "trade payload lost 'p'");
        assertTrue(tick.has("v"), "trade payload lost 'v'");
        assertTrue(tick.has("vw"), "trade payload lost 'vw'");
        assertTrue(tick.get("p").getAsJsonPrimitive().isString(), "prices are strings on the wire");
        assertTrue(tick.get("t").getAsLong() > 1_000_000_000_000L, "trade t is milliseconds");

        List<Trade> parsed = client.crypto().getTradeParsed("BTCUSDT");
        assertEquals(1, parsed.size());
        assertTrue(parsed.get(0).price().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void restKlineKeepsRespListAndSecondTimestamps() {
        JsonElement data = client.crypto().getKline("BTCUSDT", KlineType.MIN_1, 2);

        JsonObject entry = data.getAsJsonArray().get(0).getAsJsonObject();
        assertTrue(entry.has("respList"), "kline payload lost 'respList'");
        JsonObject bar = entry.getAsJsonArray("respList").get(0).getAsJsonObject();
        assertTrue(bar.has("pc"), "REST kline lost 'pc'");
        assertTrue(bar.get("t").getAsJsonPrimitive().isString(), "REST kline t is a string");
        assertTrue(bar.get("t").getAsLong() < 100_000_000_000L, "REST kline t is in seconds");

        List<Kline> bars = client.crypto().getKlineParsed("BTCUSDT", KlineType.MIN_1, 2);
        assertEquals(2, bars.size());
        assertNotNull(bars.get(0).turnover());
    }

    @Test
    void restDepthKeepsTransposedColumns() {
        JsonElement data = client.crypto().getDepth("BTCUSDT");

        JsonObject book = data.getAsJsonArray().get(0).getAsJsonObject();
        assertTrue(book.has("a") && book.has("b"), "depth payload lost 'a'/'b'");
        assertTrue(book.getAsJsonArray("a").get(0).isJsonArray(), "'a' is [[prices],[quantities]]");
    }

    @Test
    void basicSymbolsAcceptsTypeParameter() {
        JsonElement data = client.basic().getSymbols(SymbolType.CRYPTO);

        assertNotNull(data, "getSymbols returned null — the type parameter is wrong again");
        assertTrue(data.getAsJsonArray().size() > 0);
        assertTrue(data.getAsJsonArray().get(0).getAsJsonObject().has("symbol"));
    }

    @Test
    void koreaAndTaiwanTradePathsRespond() {
        JsonElement korea = client.korea().getTrade("005930.KS");
        assertNotNull(korea, "korea trade returned null");
        assertTrue(korea.isJsonArray() && korea.getAsJsonArray().size() > 0, korea.toString());
        assertEquals("005930.KS", korea.getAsJsonArray().get(0).getAsJsonObject().get("s").getAsString());

        JsonElement taiwan = client.taiwan().getTrade("2330.TW");
        assertNotNull(taiwan, "taiwan trade returned null");
        assertTrue(taiwan.isJsonArray() && taiwan.getAsJsonArray().size() > 0, taiwan.toString());
        assertEquals("2330.TW", taiwan.getAsJsonArray().get(0).getAsJsonObject().get("s").getAsString());
    }

    @Test
    void stockDetailAndFinancialRespond() {
        JsonElement detail = client.basic().getStockDetail(SymbolType.STOCK_US, "AAPL.US");
        assertNotNull(detail);
        assertTrue(detail.isJsonObject(), detail.toString());
        assertEquals("AAPL.US", detail.getAsJsonObject().get("symbol").getAsString());

        JsonElement status = client.financial().getEarningStatus("AAPL.US", SymbolType.STOCK_US);
        assertNotNull(status, "earning_status returned null");

        assertNotNull(client.financial().getIncomeStatement("AAPL.US", "STOCK_US", "fq"));
        assertNotNull(client.financial().getRevenue("AAPL.US", SymbolType.STOCK_US));
        assertNotNull(client.financial().getCashFlow("AAPL.US", "STOCK_US", "fy"));
        assertNotNull(client.financial().getBalanceSheet("AAPL.US", SymbolType.STOCK_US));
        assertNotNull(client.financial().getStatistics("AAPL.US", "STOCK_US"));
        assertNotNull(client.financial().getDividend("AAPL.US", SymbolType.STOCK_US));
        assertNotNull(client.financial().getDividendPayout("AAPL.US", "STOCK_US"));
        assertNotNull(client.financial().getEarnings("AAPL.US", "STOCK_US", "fq"));

        JsonElement tw = client.basic().getSymbols(SymbolType.STOCK_TW, "2330.TW");
        assertNotNull(tw);
        assertTrue(tw.isJsonArray(), tw.toString());
    }

    @Test
    void marketTurnoverOverviewAndRankRespond() {
        JsonElement turnover = client.market().getTurnover("US");
        assertNotNull(turnover, "turnover returned null");

        JsonElement overview = client.market().getOverview("US", "en");
        assertNotNull(overview, "overview returned null");

        JsonElement categories = client.market().getRankCategories("US");
        assertNotNull(categories, "rank categories returned null");

        JsonElement rank = client.market().getRank("US", "all", "chg", "desc", 5, 0, "en");
        assertNotNull(rank, "rank returned null");
    }

    @Test
    void klineTimestampAndLangAreAccepted() {
        JsonElement bars = client.crypto().getKline("BTCUSDT", KlineType.MIN_1, 2, 1_700_000_000L);
        assertNotNull(bars);
        assertTrue(bars.isJsonArray(), bars.toString());

        JsonElement company = client.stockInfo().getCompany("AAPL.US", "zh-CN");
        assertNotNull(company);
    }

    @Test
    void websocketDeliversUnwrappedPushes() throws Exception {
        CountDownLatch trade = new CountDownLatch(1);
        AtomicReference<JsonObject> firstTrade = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        InfowayWebSocket ws = InfowayWebSocket.builder()
                .apiKey(apiKey)
                .business("crypto")
                .onTrade(msg -> { firstTrade.compareAndSet(null, msg); trade.countDown(); })
                .onError(error::set)
                .build();
        try {
            ws.connect();
            // includeTy is accepted on the wire; crypto ticks still omit ty (equity-only field)
            ws.subscribeTrade("BTCUSDT,ETHUSDT", true);

            assertTrue(trade.await(45, TimeUnit.SECONDS), "no crypto trade push within 45s");
            JsonObject msg = firstTrade.get();
            assertFalse(msg.has("code"), "callbacks must receive data, not the envelope");
            assertTrue(msg.has("s") && msg.has("p") && msg.has("t"));
            assertNull(error.get());
        } finally {
            ws.close();
        }
    }
}
