package io.infoway.sdk.rest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.infoway.sdk.HttpClient;
import io.infoway.sdk.KlineType;

/**
 * Base class for market-specific data clients (trade, depth, kline).
 *
 * <p>Subclasses set the {@code prefix} field to route requests to the
 * correct market endpoint (e.g. "stock", "crypto", "japan", "india", "common").</p>
 */
public abstract class MarketDataClient {

    protected final HttpClient http;
    protected final String prefix;

    protected MarketDataClient(HttpClient http, String prefix) {
        this.http = http;
        this.prefix = prefix;
    }

    /**
     * Get real-time trade data.
     *
     * @param codes comma-separated symbol codes (e.g. "AAPL.US" or "AAPL.US,TSLA.US")
     * @return trade data as a JsonElement
     */
    public JsonElement getTrade(String codes) {
        return http.get("/" + prefix + "/batch_trade/" + codes);
    }

    /**
     * Get real-time order book depth.
     *
     * @param codes comma-separated symbol codes
     * @return depth data as a JsonElement
     */
    public JsonElement getDepth(String codes) {
        return http.get("/" + prefix + "/batch_depth/" + codes);
    }

    /**
     * Get candlestick / K-line data.
     *
     * @param codes     comma-separated symbol codes
     * @param klineType K-line interval (use {@link KlineType} enum)
     * @param count     number of candles to return
     * @return kline data as a JsonElement
     */
    public JsonElement getKline(String codes, KlineType klineType, int count) {
        return getKline(codes, klineType.getValue(), count);
    }

    /**
     * Get candlestick / K-line data.
     *
     * @param codes     comma-separated symbol codes
     * @param klineType K-line interval as an integer (1-12)
     * @param count     number of candles to return
     * @return kline data as a JsonElement
     */
    public JsonElement getKline(String codes, int klineType, int count) {
        JsonObject body = new JsonObject();
        body.addProperty("codes", codes);
        body.addProperty("klineType", klineType);
        body.addProperty("klineNum", count);
        return http.post("/" + prefix + "/v2/batch_kline", body);
    }
}
