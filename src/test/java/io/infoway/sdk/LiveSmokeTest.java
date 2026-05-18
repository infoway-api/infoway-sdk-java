package io.infoway.sdk;

import com.google.gson.JsonObject;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manual live test — connects to wss://data.infoway.io/ws and
 * reports counts per callback over 30 seconds.
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

        AtomicInteger trades = new AtomicInteger();
        AtomicInteger depths = new AtomicInteger();
        AtomicInteger klines = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        InfowayWebSocket ws = InfowayWebSocket.builder()
                .apiKey(apiKey)
                .business("stock")
                .onTrade((JsonObject msg) -> trades.incrementAndGet())
                .onDepth((JsonObject msg) -> depths.incrementAndGet())
                .onKline((JsonObject msg) -> klines.incrementAndGet())
                .onError(e -> { errors.incrementAndGet(); e.printStackTrace(); })
                .build();

        ws.connect();
        Thread.sleep(1500);   // give it a moment to handshake

        ws.subscribeTrade("AAPL.US,TSLA.US,MSFT.US,SPY.US,QQQ.US");
        ws.subscribeDepth("AAPL.US,TSLA.US");
        ws.subscribeKline("AAPL.US,TSLA.US", KlineType.MIN_1);

        long deadline = System.currentTimeMillis() + 30_000;
        int prev = 0;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(5_000);
            int now = trades.get() + depths.get() + klines.get();
            System.out.printf("  +%ds: trades=%d depths=%d klines=%d (delta=%d)%n",
                    (int)((30_000 - (deadline - System.currentTimeMillis())) / 1000),
                    trades.get(), depths.get(), klines.get(), now - prev);
            prev = now;
        }

        ws.close();

        System.out.println();
        System.out.println("=== FINAL ===");
        System.out.printf("trades=%d  depths=%d  klines=%d  errors=%d%n",
                trades.get(), depths.get(), klines.get(), errors.get());
        if (trades.get() < 10) {
            System.err.println("FAIL: expected at least 10 trade pushes, got " + trades.get());
            System.exit(2);
        }
        System.out.println("PASS");
    }
}
