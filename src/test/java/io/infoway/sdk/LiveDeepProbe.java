package io.infoway.sdk;

import com.google.gson.JsonElement;
import io.infoway.sdk.exception.InfowayApiException;
import io.infoway.sdk.exception.InfowayAuthException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Production deep probe: happy paths, bad parameters, and a forged API key.
 *
 * <pre>
 *   INFOWAY_API_KEY=&lt;good&gt; mvn -q test-compile exec:java \
 *       -Dexec.mainClass=io.infoway.sdk.LiveDeepProbe -Dexec.classpathScope=test
 * </pre>
 *
 * Optional {@code INFOWAY_BAD_API_KEY} overrides the forged key
 * ({@code invalid-key-sdk-deep-test-000000-infoway}).
 */
public class LiveDeepProbe {

    private static final String DEFAULT_BAD_KEY = "invalid-key-sdk-deep-test-000000-infoway";

    private final String goodKey;
    private final String badKey;
    private final List<Row> rows = new ArrayList<>();

    public LiveDeepProbe(String goodKey, String badKey) {
        this.goodKey = goodKey;
        this.badKey = badKey != null && !badKey.isBlank() ? badKey : DEFAULT_BAD_KEY;
    }

    public static void main(String[] args) throws Exception {
        String good = System.getenv("INFOWAY_API_KEY");
        if (good == null || good.isBlank()) {
            System.err.println("Set INFOWAY_API_KEY");
            System.exit(1);
        }
        LiveDeepProbe probe = new LiveDeepProbe(good, System.getenv("INFOWAY_BAD_API_KEY"));
        int failed = probe.run();
        System.exit(failed == 0 ? 0 : 2);
    }

    public int run() throws Exception {
        System.out.println("goodKey suffix=" + suffix(goodKey) + " badKey=" + badKey);
        happyRest();
        badParams();
        badKeyRest();
        websocket();
        printReport();
        return (int) rows.stream().filter(r -> !r.pass).count();
    }

    public List<Row> rows() {
        return rows;
    }

    private void happyRest() {
        try (InfowayClient c = InfowayClient.builder().apiKey(goodKey).build()) {
            expectOk("REST crypto.trade BTCUSDT", () -> nonempty(c.crypto().getTrade("BTCUSDT")));
            expectOk("REST crypto.depth BTCUSDT", () -> nonempty(c.crypto().getDepth("BTCUSDT")));
            expectOk("REST crypto.kline MIN_1 x2", () -> nonempty(c.crypto().getKline("BTCUSDT", KlineType.MIN_1, 2)));
            expectOk("REST crypto.kline + timestamp",
                    () -> nonempty(c.crypto().getKline("BTCUSDT", KlineType.MIN_1, 2, 1_700_000_000L)));
            expectOk("REST stock.trade AAPL.US", () -> nonempty(c.stock().getTrade("AAPL.US")));
            expectOk("REST stock.trade 00700.HK", () -> nonempty(c.stock().getTrade("00700.HK")));
            expectOk("REST stock.trade 600519.SH", () -> nonempty(c.stock().getTrade("600519.SH")));
            expectOk("REST japan.trade 7203.JP", () -> nonempty(c.japan().getTrade("7203.JP")));
            expectOk("REST india.trade RELIANCE.IN", () -> nonempty(c.india().getTrade("RELIANCE.IN")));
            expectOk("REST korea.trade 005930.KS", () -> nonempty(c.korea().getTrade("005930.KS")));
            expectOk("REST taiwan.trade 2330.TW", () -> nonempty(c.taiwan().getTrade("2330.TW")));
            expectOk("REST common.trade USDJPY", () -> nonempty(c.common().getTrade("USDJPY")));

            expectOk("REST basic.symbols CRYPTO", () -> nonempty(c.basic().getSymbols(SymbolType.CRYPTO)));
            expectOk("REST basic.symbols STOCK_TW", () -> nonempty(c.basic().getSymbols(SymbolType.STOCK_TW, "2330.TW")));
            expectOk("REST basic.symbolInfo AAPL.US",
                    () -> nonempty(c.basic().getSymbolInfo(SymbolType.STOCK_US, "AAPL.US")));
            expectOk("REST basic.stockDetail AAPL.US",
                    () -> present(c.basic().getStockDetail(SymbolType.STOCK_US, "AAPL.US")));
            expectOk("REST basic.markets", () -> present(c.basic().getMarkets()));
            expectOk("REST basic.tradingDays US",
                    () -> present(c.basic().getTradingDays(Market.US, "20260801", "20260815")));
            expectOk("REST basic.tradingSchedule", () -> present(c.basic().getTradingSchedule()));
            expectOk("REST basic.tradingSchedule ENERGY",
                    () -> present(c.basic().getTradingScheduleByType(ScheduleType.ENERGY)));
            expectOk("REST packages.info", () -> present(c.packages().getInfo()));

            expectOk("REST market.temperature HK,US", () -> present(c.market().getTemperature("HK,US")));
            expectOk("REST market.breadth US", () -> present(c.market().getBreadth("US")));
            expectOk("REST market.turnover US", () -> present(c.market().getTurnover("US")));
            expectOk("REST market.indexes", () -> present(c.market().getIndexes()));
            expectOk("REST market.overview US", () -> present(c.market().getOverview("US", "en")));
            expectOk("REST market.rank US",
                    () -> present(c.market().getRank("US", "all", "chg", "desc", 5, 0, "en")));
            expectOk("REST plate.industry HK", () -> present(c.plate().getIndustry("HK", 10)));
            expectOk("REST stockInfo.company zh-CN", () -> present(c.stockInfo().getCompany("AAPL.US", Lang.ZH_CN)));
            expectOk("REST stockInfo.valuation", () -> present(c.stockInfo().getValuation("AAPL.US")));
            expectOk("REST financial.earningStatus",
                    () -> present(c.financial().getEarningStatus("AAPL.US", SymbolType.STOCK_US)));
            expectOk("REST financial.income fq",
                    () -> present(c.financial().getIncomeStatement("AAPL.US", SymbolType.STOCK_US, PeriodType.FQ)));
            expectOk("REST financial.dividend",
                    () -> present(c.financial().getDividend("AAPL.US", SymbolType.STOCK_US)));
        }
    }

    private void badParams() {
        try (InfowayClient c = InfowayClient.builder().apiKey(goodKey).build()) {
            expectClient("CLIENT scheduleByType STOCK_US", IllegalArgumentException.class,
                    () -> c.basic().getTradingScheduleByType(SymbolType.STOCK_US));
            expectClient("CLIENT scheduleByType string STOCK_US", IllegalArgumentException.class,
                    () -> c.basic().getTradingScheduleByType("STOCK_US"));

            expectApi("REST trade unknown symbol",
                    () -> c.crypto().getTrade("NOPE_SYMBOL_XYZ_NOT_LISTED"));
            expectApi("REST stock trade NOPE.US",
                    () -> c.stock().getTrade("NOPE.US"));
            expectApi("REST korea trade AAPL.US (wrong market)",
                    () -> c.korea().getTrade("AAPL.US"));
            expectApi("REST kline type 99",
                    () -> c.crypto().getKline("BTCUSDT", 99, 2));
            expectApi("REST kline empty codes",
                    () -> c.crypto().getKline("", KlineType.MIN_1, 2));
            expectApi("REST kline timestamp milliseconds",
                    () -> c.crypto().getKline("BTCUSDT", KlineType.MIN_1, 2, 1_700_000_000_000L));
            expectApi("REST kline timestamp year-2000",
                    () -> c.crypto().getKline("BTCUSDT", KlineType.MIN_1, 2, 946_684_800L));
            expectApi("REST kline count 9999",
                    () -> c.crypto().getKline("BTCUSDT", KlineType.DAY, 9999));

            expectApi("REST symbols type=US",
                    () -> c.basic().getSymbols("US"));
            expectApi("REST symbols type=STOCK_XX",
                    () -> c.basic().getSymbols("STOCK_XX"));
            expectApi("REST tradingDays bad date format",
                    () -> c.basic().getTradingDays("US", "2026-08-01", "2026-08-15"));
            expectApi("REST tradingDays missing beginDay",
                    () -> c.basic().getTradingDays("US", "", "20260815"));
            expectApi("REST adjustmentFactors market=STOCK_US",
                    () -> c.basic().getAdjustmentFactors("AAPL.US", "STOCK_US", "20260801", "20260815"));
            // Production is lenient here: HTTP 200, not 400.
            expectOk("REST stockDetail type=CRYPTO → data null", () -> {
                JsonElement data = c.basic().getStockDetail(SymbolType.CRYPTO, "BTCUSDT");
                if (data != null && !data.isJsonNull()) {
                    throw new IllegalStateException("expected json null, got " + clip(data.toString()));
                }
                return "server 200 data=null";
            });
            expectOk("REST financial type=US uses .US suffix", () -> {
                JsonElement data = c.financial().getIncomeStatement("AAPL.US", "US", "fq");
                if (data == null || !data.isJsonArray() || data.getAsJsonArray().isEmpty()) {
                    throw new IllegalStateException("expected rows, got " + data);
                }
                return "server ignored type=US and returned AAPL rows";
            });
            expectOk("REST financial period_type=xx → empty", () -> {
                JsonElement data = c.financial().getIncomeStatement("AAPL.US", "STOCK_US", "xx");
                if (data == null || !data.isJsonArray() || data.getAsJsonArray().size() > 0) {
                    throw new IllegalStateException("expected empty array, got " + data);
                }
                return "server 200 empty list";
            });
        }
    }

    private void badKeyRest() {
        try (InfowayClient c = InfowayClient.builder().apiKey(badKey).build()) {
            expectAuth("REST bad-key crypto.trade", () -> c.crypto().getTrade("BTCUSDT"));
            expectAuth("REST bad-key packages.info", () -> c.packages().getInfo());
            expectAuth("REST bad-key basic.symbols", () -> c.basic().getSymbols(SymbolType.CRYPTO));
            expectAuth("REST bad-key financial",
                    () -> c.financial().getEarningStatus("AAPL.US", SymbolType.STOCK_US));
        }
    }

    private void websocket() throws Exception {
        expectWsTick("WS crypto trade BTCUSDT", goodKey, WsBusiness.CRYPTO, "BTCUSDT", 25);
        expectWsError("WS bad-key crypto handshake", badKey, WsBusiness.CRYPTO, "BTCUSDT",
                InfowayAuthException.class, 8);
        expectWsNews("WS news handshake good key", goodKey, 12);
        expectWsNewsAuth("WS bad-key news handshake", badKey, 8);
    }

    private void expectOk(String name, Supplier<String> action) {
        try {
            String detail = action.get();
            add(name, "OK", true, null, 0, null, detail);
        } catch (Exception e) {
            add(name, "OK", false, e, ret(e), nameOf(e), e.getMessage());
        }
    }

    private void expectApi(String name, Runnable action) {
        try {
            action.run();
            add(name, "API_ERROR", false, null, 0, null, "succeeded unexpectedly");
        } catch (InfowayAuthException e) {
            add(name, "API_ERROR", false, e, e.getRet(), e.getErrorName(),
                    "got AUTH instead of API: " + e.getMessage());
        } catch (InfowayApiException e) {
            add(name, "API_ERROR", true, e, e.getRet(), e.getErrorName(), e.getMessage());
        } catch (Exception e) {
            add(name, "API_ERROR", false, e, ret(e), nameOf(e), e.getMessage());
        }
    }

    private void expectAuth(String name, Runnable action) {
        try {
            action.run();
            add(name, "AUTH", false, null, 0, null, "succeeded unexpectedly");
        } catch (InfowayAuthException e) {
            add(name, "AUTH", true, e, e.getRet(), e.getErrorName(), e.getMessage());
        } catch (InfowayApiException e) {
            // Gateway / package service may return 500 "Apikey not exists" instead of HTTP 401.
            boolean ok = e.getRet() == 401 || containsIgnoreCase(e.getMsg(), "apikey")
                    || containsIgnoreCase(e.getMsg(), "token")
                    || containsIgnoreCase(e.getMsg(), "unauthorized")
                    || containsIgnoreCase(e.getMsg(), "not exists");
            add(name, "AUTH", ok, e, e.getRet(), e.getErrorName(),
                    (ok ? "auth-like API error: " : "not auth: ") + e.getMessage());
        } catch (Exception e) {
            add(name, "AUTH", false, e, ret(e), nameOf(e), e.getMessage());
        }
    }

    private void expectClient(String name, Class<? extends Exception> type, Runnable action) {
        try {
            action.run();
            add(name, type.getSimpleName(), false, null, 0, null, "succeeded unexpectedly");
        } catch (Exception e) {
            add(name, type.getSimpleName(), type.isInstance(e), e, ret(e), nameOf(e), e.getMessage());
        }
    }

    private void expectWsTick(String name, String key, WsBusiness business, String codes, int waitSec) {
        CountDownLatch first = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        InfowayWebSocket ws = InfowayWebSocket.builder()
                .apiKey(key)
                .business(business)
                .maxReconnectAttempts(1)
                .onTrade(data -> first.countDown())
                .onError(error::set)
                .build();
        try {
            ws.connect();
            ws.subscribeTrade(codes);
            boolean got = first.await(waitSec, TimeUnit.SECONDS);
            if (error.get() != null) {
                add(name, "WS_TICK", false, error.get(), ret(error.get()), nameOf(error.get()),
                        error.get().getMessage());
            } else if (!got) {
                add(name, "WS_TICK", false, null, 0, null, "no push in " + waitSec + "s");
            } else {
                add(name, "WS_TICK", true, null, 0, null, "tick received");
            }
        } catch (Exception e) {
            add(name, "WS_TICK", false, e, ret(e), nameOf(e), e.getMessage());
        } finally {
            ws.close();
        }
    }

    private void expectWsError(String name, String key, WsBusiness business, String codes,
                               Class<? extends Exception> type, int waitSec) {
        CountDownLatch errored = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        InfowayWebSocket ws = InfowayWebSocket.builder()
                .apiKey(key)
                .business(business)
                .maxReconnectAttempts(1)
                .onError(e -> { error.set(e); errored.countDown(); })
                .build();
        try {
            ws.connect();
            ws.subscribeTrade(codes);
            boolean got = errored.await(waitSec, TimeUnit.SECONDS);
            Exception e = error.get();
            boolean ok = got && e != null && type.isInstance(e);
            add(name, type.getSimpleName(), ok, e, ret(e), nameOf(e),
                    e == null ? "no error in " + waitSec + "s" : e.getMessage());
        } catch (Exception e) {
            add(name, type.getSimpleName(), type.isInstance(e), e, ret(e), nameOf(e), e.getMessage());
        } finally {
            ws.close();
        }
    }

    private void expectWsNews(String name, String key, int waitSec) {
        AtomicReference<Exception> error = new AtomicReference<>();
        CountDownLatch article = new CountDownLatch(1);
        InfowayNewsWebSocket news = InfowayNewsWebSocket.builder()
                .apiKey(key)
                .lang(NewsLang.ZH_HANS)
                .maxReconnectAttempts(1)
                .onNews(item -> article.countDown())
                .onError(error::set)
                .build();
        try {
            news.connect();
            boolean got = article.await(waitSec, TimeUnit.SECONDS);
            if (error.get() != null) {
                add(name, "WS_NEWS", false, error.get(), ret(error.get()), nameOf(error.get()),
                        error.get().getMessage());
            } else {
                add(name, "WS_NEWS", true, null, 0, null,
                        got ? "article received" : "connected, no article in " + waitSec + "s");
            }
        } catch (Exception e) {
            add(name, "WS_NEWS", false, e, ret(e), nameOf(e), e.getMessage());
        } finally {
            news.close();
        }
    }

    private void expectWsNewsAuth(String name, String key, int waitSec) {
        CountDownLatch errored = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        InfowayNewsWebSocket news = InfowayNewsWebSocket.builder()
                .apiKey(key)
                .lang(NewsLang.EN)
                .maxReconnectAttempts(1)
                .onError(e -> { error.set(e); errored.countDown(); })
                .build();
        try {
            news.connect();
            boolean got = errored.await(waitSec, TimeUnit.SECONDS);
            Exception e = error.get();
            boolean ok = got && e instanceof InfowayAuthException;
            add(name, "AUTH", ok, e, ret(e), nameOf(e),
                    e == null ? "no error in " + waitSec + "s" : e.getMessage());
        } catch (Exception e) {
            add(name, "AUTH", e instanceof InfowayAuthException, e, ret(e), nameOf(e), e.getMessage());
        } finally {
            news.close();
        }
    }

    private void add(String name, String expected, boolean pass, Exception error,
                     int ret, String errorName, String detail) {
        Row row = new Row(name, expected, pass, error == null ? "" : error.getClass().getSimpleName(),
                ret, errorName, clip(detail));
        rows.add(row);
        System.out.printf("%s  %-42s expect=%-12s ret=%-4s name=%-28s %s%n",
                pass ? "OK  " : "FAIL",
                name,
                expected,
                ret == 0 ? "-" : Integer.toString(ret),
                errorName == null ? "-" : errorName,
                clip(detail));
    }

    private void printReport() {
        long pass = rows.stream().filter(r -> r.pass).count();
        long fail = rows.size() - pass;
        System.out.println();
        System.out.println("=== LIVE DEEP PROBE ===");
        System.out.println("PASS " + pass + "  FAIL " + fail + "  TOTAL " + rows.size());
        rows.stream().filter(r -> !r.pass).forEach(r ->
                System.out.println("  FAIL " + r.name + " | " + r.detail));
    }

    private static String nonempty(JsonElement data) {
        if (data == null) {
            throw new IllegalStateException("null data");
        }
        if (data.isJsonNull()) {
            throw new IllegalStateException("json null");
        }
        if (data.isJsonArray() && data.getAsJsonArray().isEmpty()) {
            throw new IllegalStateException("empty array");
        }
        return clip(data.toString());
    }

    private static String present(JsonElement data) {
        if (data == null || data.isJsonNull()) {
            throw new IllegalStateException("null data");
        }
        return clip(data.toString());
    }

    private static String clip(String text) {
        if (text == null) {
            return "";
        }
        String one = text.replace('\n', ' ');
        return one.length() > 140 ? one.substring(0, 140) + "…" : one;
    }

    private static int ret(Exception e) {
        return e instanceof InfowayApiException api ? api.getRet() : 0;
    }

    private static String nameOf(Exception e) {
        if (e instanceof InfowayApiException api) {
            return api.getErrorName();
        }
        return e == null ? null : e.getClass().getSimpleName();
    }

    private static boolean containsIgnoreCase(String text, String needle) {
        return text != null && text.toLowerCase().contains(needle.toLowerCase());
    }

    private static String suffix(String key) {
        if (key == null || key.length() < 8) {
            return "????";
        }
        return "…" + key.substring(key.length() - 8);
    }

    public record Row(String name, String expected, boolean pass, String exception,
                      int ret, String errorName, String detail) {}
}
