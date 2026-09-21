package io.infoway.sdk;

import com.google.gson.JsonObject;
import io.infoway.sdk.model.Normalizer;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manual live smoke test — connects to wss://data.infoway.io/ws and reports
 * per-callback counts over 30 seconds.
 *
 * <p>Uses business=crypto because it trades 24/7; equity markets are closed most of the
 * week and "closed" looks exactly like "broken" on a WebSocket (ack ok, zero pushes).</p>
 *
 * NOT part of mvn test. Run with:
 *   mvn test-compile exec:java -Dexec.mainClass=io.infoway.sdk.LiveSmokeTest \
 *       -Dexec.classpathScope=test
 */
public class LiveSmokeTest {
    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("INFOWAY_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Set INFOWAY_API_KEY before running this smoke test.");
            System.exit(1);
        }
        String business = args.length > 0 ? args[0] : "crypto";
        String codes = args.length > 1 ? args[1] : "BTCUSDT,ETHUSDT";

        AtomicInteger trades = new AtomicInteger();
        AtomicInteger depths = new AtomicInteger();
        AtomicInteger klines = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        AtomicReference<JsonObject> firstTrade = new AtomicReference<>();
        AtomicReference<JsonObject> firstKline = new AtomicReference<>();

        InfowayWebSocket ws = InfowayWebSocket.builder()
                .apiKey(apiKey)
                .business(business)
                .onTrade((JsonObject msg) -> { firstTrade.compareAndSet(null, msg); trades.incrementAndGet(); })
                .onDepth((JsonObject msg) -> depths.incrementAndGet())
                .onKline((JsonObject msg) -> { firstKline.compareAndSet(null, msg); klines.incrementAndGet(); })
                .onError(e -> { errors.incrementAndGet(); System.err.println("onError: " + e); })
                .build();

        ws.connect();

        ws.subscribeTrade(codes);
        ws.subscribeDepth(codes);
        ws.subscribeKline(codes, KlineType.MIN_1);

        long deadline = System.currentTimeMillis() + 30_000;
        int prev = 0;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(5_000);
            int now = trades.get() + depths.get() + klines.get();
            System.out.printf("  +%ds: trades=%d depths=%d klines=%d (delta=%d)%n",
                    (int) ((30_000 - (deadline - System.currentTimeMillis())) / 1000),
                    trades.get(), depths.get(), klines.get(), now - prev);
            prev = now;
        }

        ws.close();

        System.out.println();
        System.out.println("=== FINAL ===");
        System.out.printf("business=%s codes=%s%n", business, codes);
        System.out.printf("trades=%d  depths=%d  klines=%d  errors=%d%n",
                trades.get(), depths.get(), klines.get(), errors.get());
        if (firstTrade.get() != null) {
            System.out.println("first trade payload : " + firstTrade.get());
            System.out.println("first trade parsed  : " + Normalizer.trade(firstTrade.get()));
        }
        if (firstKline.get() != null) {
            System.out.println("first kline payload : " + firstKline.get());
            System.out.println("first kline parsed  : " + Normalizer.kline(firstKline.get()));
        }
        if (trades.get() < 10) {
            System.err.println("FAIL: expected at least 10 trade pushes, got " + trades.get());
            System.exit(2);
        }
        System.out.println("PASS");
    }
}
