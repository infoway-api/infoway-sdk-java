package io.infoway.sdk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.infoway.sdk.exception.InfowayApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * One-shot live probe of REST + WebSocket against production.
 *
 *   INFOWAY_API_KEY=&lt;key&gt; mvn -q test-compile exec:java \
 *       -Dexec.mainClass=io.infoway.sdk.LiveFullProbe -Dexec.classpathScope=test
 */
public class LiveFullProbe {

    private static final List<String> ok = new ArrayList<>();
    private static final List<String> fail = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("INFOWAY_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Set INFOWAY_API_KEY");
            System.exit(1);
        }

        try (InfowayClient client = InfowayClient.builder().apiKey(apiKey).build()) {
            probe("REST crypto.getTrade BTCUSDT",
                    () -> nonempty(client.crypto().getTrade("BTCUSDT")));
            probe("REST crypto.getDepth BTCUSDT",
                    () -> nonempty(client.crypto().getDepth("BTCUSDT")));
            probe("REST crypto.getKline BTCUSDT MIN_1",
                    () -> nonempty(client.crypto().getKline("BTCUSDT", KlineType.MIN_1, 2)));
            probe("REST stock.getTrade AAPL.US",
                    () -> nonempty(client.stock().getTrade("AAPL.US")));
            probe("REST stock.getTrade 00700.HK",
                    () -> nonempty(client.stock().getTrade("00700.HK")));
            probe("REST stock.getTrade 600519.SH",
                    () -> nonempty(client.stock().getTrade("600519.SH")));
            probe("REST japan.getTrade 7203.JP",
                    () -> nonempty(client.japan().getTrade("7203.JP")));
            probe("REST india.getTrade RELIANCE.IN",
                    () -> nonempty(client.india().getTrade("RELIANCE.IN")));
            probe("REST korea.getTrade 005930.KS",
                    () -> nonempty(client.korea().getTrade("005930.KS")));
            probe("REST taiwan.getTrade 2330.TW",
                    () -> nonempty(client.taiwan().getTrade("2330.TW")));
            probe("REST common.getTrade USDJPY",
                    () -> nonempty(client.common().getTrade("USDJPY")));

            probe("REST basic.getSymbols CRYPTO",
                    () -> nonempty(client.basic().getSymbols(SymbolType.CRYPTO)));
            probe("REST basic.getSymbols STOCK_TW 2330.TW",
                    () -> nonempty(client.basic().getSymbols(SymbolType.STOCK_TW, "2330.TW")));
            probe("REST basic.getStockDetail AAPL.US",
                    () -> present(client.basic().getStockDetail(SymbolType.STOCK_US, "AAPL.US")));
            probe("REST basic.getMarkets",
                    () -> present(client.basic().getMarkets()));
            probe("REST basic.getTradingSchedule US",
                    () -> present(client.basic().getTradingSchedule("US")));

            probe("REST market.getTemperature HK,US",
                    () -> present(client.market().getTemperature("HK,US")));
            probe("REST market.getBreadth US",
                    () -> present(client.market().getBreadth("US")));
            probe("REST market.getTurnover US",
                    () -> present(client.market().getTurnover("US")));
            probe("REST market.getIndexes",
                    () -> present(client.market().getIndexes()));
            probe("REST market.getOverview US",
                    () -> present(client.market().getOverview("US", "en")));
            probe("REST market.getRankCategories US",
                    () -> present(client.market().getRankCategories("US")));
            probe("REST market.getRank US all",
                    () -> present(client.market().getRank("US", "all", "chg", "desc", 5, 0, "en")));

            probe("REST plate.getIndustry HK",
                    () -> present(client.plate().getIndustry("HK", 10)));
            probe("REST stockInfo.getCompany AAPL.US zh-CN",
                    () -> present(client.stockInfo().getCompany("AAPL.US", "zh-CN")));
            probe("REST stockInfo.getValuation AAPL.US",
                    () -> present(client.stockInfo().getValuation("AAPL.US")));

            probe("REST financial.earningStatus AAPL.US",
                    () -> present(client.financial().getEarningStatus("AAPL.US", SymbolType.STOCK_US)));
            probe("REST financial.incomeStatement AAPL.US fq",
                    () -> present(client.financial().getIncomeStatement("AAPL.US", "STOCK_US", "fq")));
            probe("REST financial.dividend AAPL.US",
                    () -> present(client.financial().getDividend("AAPL.US", SymbolType.STOCK_US)));
        }

        probeWs("WS crypto trade BTCUSDT", apiKey, "crypto", "BTCUSDT", 20);
        probeWs("WS korea trade 005930.KS", apiKey, "korea", "005930.KS", 20);
        probeWs("WS taiwan trade 2330.TW", apiKey, "taiwan", "2330.TW", 20);
        probeNews(apiKey);

        System.out.println();
        System.out.println("=== LIVE PROBE ===");
        System.out.println("PASS " + ok.size());
        ok.forEach(s -> System.out.println("  OK   " + s));
        System.out.println("FAIL " + fail.size());
        fail.forEach(s -> System.out.println("  FAIL " + s));
        System.exit(fail.isEmpty() ? 0 : 2);
    }

    private static void probe(String name, Supplier<String> action) {
        try {
            String detail = action.get();
            ok.add(name + (detail == null || detail.isEmpty() ? "" : " " + detail));
            System.out.println("OK   " + name + (detail == null ? "" : " " + detail));
        } catch (Exception e) {
            String msg = e instanceof InfowayApiException api
                    ? api.getMessage()
                    : e.getClass().getSimpleName() + ": " + e.getMessage();
            fail.add(name + " -> " + msg);
            System.out.println("FAIL " + name + " -> " + msg);
        }
    }

    private static String nonempty(JsonElement data) {
        if (data == null) {
            throw new IllegalStateException("null data");
        }
        if (data.isJsonArray() && data.getAsJsonArray().isEmpty()) {
            throw new IllegalStateException("empty array");
        }
        return summarize(data);
    }

    private static String present(JsonElement data) {
        if (data == null) {
            throw new IllegalStateException("null data");
        }
        return summarize(data);
    }

    private static String summarize(JsonElement data) {
        String raw = data.toString();
        return raw.length() > 80 ? raw.substring(0, 80) + "..." : raw;
    }

    private static void probeWs(String name, String apiKey, String business, String codes, int waitSec) {
        CountDownLatch first = new CountDownLatch(1);
        AtomicInteger n = new AtomicInteger();
        AtomicReference<Exception> error = new AtomicReference<>();
        InfowayWebSocket ws = InfowayWebSocket.builder()
                .apiKey(apiKey)
                .business(business)
                .maxReconnectAttempts(1)
                .onTrade(msg -> { n.incrementAndGet(); first.countDown(); })
                .onError(error::set)
                .build();
        try {
            ws.connect();
            ws.subscribeTrade(codes);
            boolean got = first.await(waitSec, TimeUnit.SECONDS);
            if (error.get() != null) {
                fail.add(name + " -> " + error.get().getMessage());
                System.out.println("FAIL " + name + " -> " + error.get().getMessage());
            } else if (!got) {
                fail.add(name + " -> no push in " + waitSec + "s (ack-only / closed / no ticks)");
                System.out.println("FAIL " + name + " -> no push in " + waitSec + "s");
            } else {
                ok.add(name + " ticks=" + n.get());
                System.out.println("OK   " + name + " ticks=" + n.get());
            }
        } catch (Exception e) {
            fail.add(name + " -> " + e.getMessage());
            System.out.println("FAIL " + name + " -> " + e.getMessage());
        } finally {
            ws.close();
        }
    }

    private static void probeNews(String apiKey) {
        String name = "WS news zh-Hans";
        CountDownLatch first = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        InfowayNewsWebSocket news = InfowayNewsWebSocket.builder()
                .apiKey(apiKey)
                .lang("zh-Hans")
                .maxReconnectAttempts(1)
                .onNews(item -> first.countDown())
                .onError(error::set)
                .build();
        try {
            news.connect();
            boolean got = first.await(15, TimeUnit.SECONDS);
            if (error.get() != null) {
                fail.add(name + " -> " + error.get().getMessage());
                System.out.println("FAIL " + name + " -> " + error.get().getMessage());
            } else if (!got) {
                // Handshake succeeded; no article in 15s is still a working channel.
                ok.add(name + " connected, no article in 15s");
                System.out.println("OK   " + name + " connected, no article in 15s");
            } else {
                ok.add(name + " received article");
                System.out.println("OK   " + name + " received article");
            }
        } catch (Exception e) {
            fail.add(name + " -> " + e.getMessage());
            System.out.println("FAIL " + name + " -> " + e.getMessage());
        } finally {
            news.close();
        }
    }
}
