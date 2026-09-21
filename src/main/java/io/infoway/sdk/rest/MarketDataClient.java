package io.infoway.sdk.rest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.infoway.sdk.HttpClient;
import io.infoway.sdk.KlineType;
import io.infoway.sdk.model.Depth;
import io.infoway.sdk.model.Kline;
import io.infoway.sdk.model.Normalizer;
import io.infoway.sdk.model.Trade;

import java.util.List;

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
        return getKline(codes, klineType.getValue(), count, null);
    }

    /**
     * Get candlestick / K-line data ending at {@code timestamp} (seconds).
     *
     * <p>{@code timestamp} only applies to minute and hour bars; daily and above ignore it.</p>
     *
     * @param codes     comma-separated symbol codes
     * @param klineType K-line interval
     * @param count     number of candles to return
     * @param timestamp optional end time as a unix-seconds value; {@code null} = latest
     * @return kline data as a JsonElement
     */
    public JsonElement getKline(String codes, KlineType klineType, int count, Long timestamp) {
        return getKline(codes, klineType.getValue(), count, timestamp);
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
        return getKline(codes, klineType, count, null);
    }

    /**
     * Get candlestick / K-line data ending at {@code timestamp} (seconds).
     *
     * @param codes     comma-separated symbol codes
     * @param klineType K-line interval as an integer (1-12)
     * @param count     number of candles to return
     * @param timestamp optional end time as a unix-seconds value; {@code null} = latest
     * @return kline data as a JsonElement
     */
    public JsonElement getKline(String codes, int klineType, int count, Long timestamp) {
        JsonObject body = new JsonObject();
        body.addProperty("codes", codes);
        body.addProperty("klineType", klineType);
        body.addProperty("klineNum", count);
        if (timestamp != null) {
            body.addProperty("timestamp", timestamp);
        }
        return http.post("/" + prefix + "/v2/batch_kline", body);
    }

    /**
     * Same request as {@link #getTrade(String)}, returned as typed models.
     *
     * <p>Prices and volumes become {@link java.math.BigDecimal}, {@code t} becomes an
     * {@link java.time.Instant} and {@code vw} is exposed under its real meaning, {@code turnover}.</p>
     *
     * @param codes comma-separated symbol codes
     * @return one {@link Trade} per symbol
     */
    public List<Trade> getTradeParsed(String codes) {
        return Normalizer.trades(getTrade(codes));
    }

    /**
     * Same request as {@link #getDepth(String)}, with the transposed {@code a}/{@code b}
     * columns turned into {@code (price, quantity)} levels.
     *
     * @param codes comma-separated symbol codes
     * @return one {@link Depth} per symbol
     */
    public List<Depth> getDepthParsed(String codes) {
        return Normalizer.depths(getDepth(codes));
    }

    /**
     * Same request as {@link #getKline(String, KlineType, int)}, with {@code respList}
     * flattened and {@code pc} converted to a decimal fraction.
     *
     * @param codes     comma-separated symbol codes
     * @param klineType K-line interval
     * @param count     number of candles (max 500 per symbol; multi-symbol requests are
     *                  silently truncated to 2 candles each by the server)
     * @return every bar of every requested symbol
     */
    public List<Kline> getKlineParsed(String codes, KlineType klineType, int count) {
        return Normalizer.klines(getKline(codes, klineType, count));
    }

    /**
     * @param codes     comma-separated symbol codes
     * @param klineType K-line interval as an integer (1-12)
     * @param count     number of candles
     * @return every bar of every requested symbol
     */
    public List<Kline> getKlineParsed(String codes, int klineType, int count) {
        return Normalizer.klines(getKline(codes, klineType, count));
    }

    /**
     * Same as {@link #getKlineParsed(String, KlineType, int)} with a historical end time.
     *
     * @param codes     comma-separated symbol codes
     * @param klineType K-line interval
     * @param count     number of candles
     * @param timestamp optional end time as a unix-seconds value; {@code null} = latest
     * @return every bar of every requested symbol
     */
    public List<Kline> getKlineParsed(String codes, KlineType klineType, int count, Long timestamp) {
        return Normalizer.klines(getKline(codes, klineType, count, timestamp));
    }

    /**
     * @param codes     comma-separated symbol codes
     * @param klineType K-line interval as an integer (1-12)
     * @param count     number of candles
     * @param timestamp optional end time as a unix-seconds value; {@code null} = latest
     * @return every bar of every requested symbol
     */
    public List<Kline> getKlineParsed(String codes, int klineType, int count, Long timestamp) {
        return Normalizer.klines(getKline(codes, klineType, count, timestamp));
    }
}
