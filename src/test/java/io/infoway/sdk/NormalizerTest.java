package io.infoway.sdk;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.infoway.sdk.model.Depth;
import io.infoway.sdk.model.Kline;
import io.infoway.sdk.model.NewsItem;
import io.infoway.sdk.model.Normalizer;
import io.infoway.sdk.model.Trade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The optional typed channel (design doc §5). Raw JsonElement stays the default;
 * these models absorb the server's inconsistencies: string numbers, milliseconds
 * for trade/depth vs seconds for kline, pc vs pfr, respList nesting, transposed books,
 * and vw being turnover rather than VWAP.
 */
class NormalizerTest {

    private final Gson gson = new Gson();

    private JsonElement restData(String fixture) {
        return gson.fromJson(Fixtures.load(fixture), JsonObject.class).get("data");
    }

    private JsonObject wsData(String fixture) {
        return gson.fromJson(Fixtures.load(fixture), JsonObject.class).getAsJsonObject("data");
    }

    // ---- trade -----------------------------------------------------------------

    @Test
    void restTradeIsNormalized() {
        List<Trade> trades = Normalizer.trades(restData("rest/trade_stock.json"));

        assertEquals(1, trades.size());
        Trade t = trades.get(0);
        assertEquals("AAPL.US", t.symbol());
        assertEquals(0, new BigDecimal("305.771").compareTo(t.price()));
        assertEquals(0, new BigDecimal("1").compareTo(t.volume()));
        // vw is the traded amount, not a VWAP — the English docs had it backwards
        assertEquals(0, new BigDecimal("305.771").compareTo(t.turnover()));
        assertEquals(Instant.ofEpochMilli(1786751999691L), t.time());
        assertEquals(0, t.direction());
        assertNull(t.tradeType());
    }

    @Test
    void wsTradePushIsNormalized() {
        Trade t = Normalizer.trade(wsData("ws/push_trade.json"));

        assertEquals("BTCUSDT", t.symbol());
        assertEquals(0, new BigDecimal("63039").compareTo(t.price()));
        assertEquals(0, new BigDecimal("5000.88387").compareTo(t.turnover()));
        assertEquals(1, t.direction());
        assertEquals(Instant.ofEpochMilli(1786775227483L), t.time());
    }

    // ---- depth: a/b are transposed columns -------------------------------------

    @Test
    void restDepthTransposedColumnsBecomePriceQuantityPairs() {
        List<Depth> books = Normalizer.depths(restData("rest/depth_stock.json"));

        assertEquals(1, books.size());
        Depth book = books.get(0);
        assertEquals("AAPL.US", book.symbol());
        assertEquals(Instant.ofEpochMilli(1786751397726L), book.time());
        assertEquals(1, book.asks().size());
        assertEquals(0, new BigDecimal("305.800").compareTo(book.asks().get(0).price()));
        assertEquals(0, new BigDecimal("229").compareTo(book.asks().get(0).quantity()));
        assertEquals(0, new BigDecimal("305.770").compareTo(book.bids().get(0).price()));
        assertEquals(0, new BigDecimal("24").compareTo(book.bids().get(0).quantity()));
    }

    @Test
    void wsDepthKeepsEveryLevel() {
        Depth book = Normalizer.depth(wsData("ws/push_depth.json"));

        assertEquals(5, book.asks().size());
        assertEquals(5, book.bids().size());
        assertEquals(0, new BigDecimal("63039.00000000").compareTo(book.asks().get(0).price()));
        assertEquals(0, new BigDecimal("45.06645000").compareTo(book.asks().get(0).quantity()));
    }

    // ---- kline: respList, seconds, pc vs pfr -----------------------------------

    @Test
    void restKlineFlattensRespListAndParsesPercent() {
        List<Kline> bars = Normalizer.klines(restData("rest/kline_stock.json"));

        assertEquals(2, bars.size(), "respList must be flattened");
        Kline bar = bars.get(0);
        assertEquals("AAPL.US", bar.symbol());
        assertEquals(0, new BigDecimal("305.830").compareTo(bar.open()));
        assertEquals(0, new BigDecimal("305.990").compareTo(bar.high()));
        assertEquals(0, new BigDecimal("305.675").compareTo(bar.low()));
        assertEquals(0, new BigDecimal("305.930").compareTo(bar.close()));
        assertEquals(0, new BigDecimal("331559").compareTo(bar.volume()));
        assertEquals(0, new BigDecimal("101402874.954").compareTo(bar.turnover()));
        assertEquals(0, new BigDecimal("0.090").compareTo(bar.changeAmount()));
        // REST calls it "pc" and formats it "0.03%"
        assertEquals(0, new BigDecimal("0.0003").compareTo(bar.changePercent()));
        assertNull(bar.klineType(), "REST kline carries no interval field");
    }

    @Test
    void wsKlineUsesPfrAndCarriesTheInterval() {
        Kline bar = Normalizer.kline(wsData("ws/push_kline.json"));

        assertEquals("BTCUSDT", bar.symbol());
        assertEquals(0, new BigDecimal("-0.0001").compareTo(bar.changePercent()), "WS calls it pfr");
        assertEquals(1, bar.klineType());
        assertEquals(0, new BigDecimal("1254633.2367064").compareTo(bar.turnover()));
    }

    @Test
    void klineTimestampIsSecondsNotMilliseconds() {
        Kline bar = Normalizer.kline(wsData("ws/push_kline.json"));

        // "1786775220" read as milliseconds lands in January 1970 — the classic symptom
        assertEquals(Instant.ofEpochSecond(1786775220L), bar.time());
        assertEquals(2026, bar.time().atZone(ZoneOffset.UTC).getYear());
    }

    // ---- news -------------------------------------------------------------------

    @Test
    void newsItemIsNormalized() {
        NewsItem item = Normalizer.news(wsData("ws/news_push_docs_derived.json"));

        assertEquals("AAPL.US", item.symbols().get(0));
        assertEquals(Instant.ofEpochSecond(1786775220L), item.published());
        assertEquals("Reuters", item.provider());
        assertEquals(3, item.urgency());
        assertNotNull(item.dedupKey());
        assertNotNull(item.summary());
    }

    // ---- robustness -------------------------------------------------------------

    @Test
    void missingOptionalFieldsDoNotThrow() {
        JsonObject sparse = gson.fromJson("{\"s\":\"BTCUSDT\"}", JsonObject.class);

        Trade t = Normalizer.trade(sparse);
        assertEquals("BTCUSDT", t.symbol());
        assertNull(t.price());
        assertNull(t.time());

        Depth d = Normalizer.depth(sparse);
        assertTrue(d.asks().isEmpty());
        assertTrue(d.bids().isEmpty());
    }

    @Test
    void nullDataYieldsEmptyLists() {
        assertTrue(Normalizer.trades(null).isEmpty());
        assertTrue(Normalizer.depths(null).isEmpty());
        assertTrue(Normalizer.klines(null).isEmpty());
    }
}
