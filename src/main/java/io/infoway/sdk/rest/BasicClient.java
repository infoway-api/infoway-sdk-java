package io.infoway.sdk.rest;

import com.google.gson.JsonElement;
import io.infoway.sdk.HttpClient;
import io.infoway.sdk.Market;
import io.infoway.sdk.ScheduleType;
import io.infoway.sdk.SymbolType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Basic information client: symbol lists, symbol details, adjustment factors,
 * trading calendar and trading schedule.
 *
 * <p><b>Breaking change in 0.2.0.</b> Every method in this class used to send query
 * parameters the server does not accept, so all five returned {@code null} against
 * production (verified 2026-08-15). The signatures below match the real contract:</p>
 *
 * <pre>{@code
 * client.basic().getSymbols(SymbolType.STOCK_US);
 * client.basic().getSymbolInfo("STOCK_US", "AAPL.US");
 * client.basic().getAdjustmentFactors("AAPL.US", "US", "20260801", "20260815");
 * client.basic().getTradingDays("US", "20260801", "20260815");
 * client.basic().getTradingSchedule();
 * }</pre>
 */
public class BasicClient {

    private final HttpClient http;

    public BasicClient(HttpClient http) {
        this.http = http;
    }

    /**
     * List the symbols of one product type.
     *
     * @param type product type, e.g. {@link SymbolType#STOCK_US}
     * @return symbol list ({@code symbol, name_cn, name_hk, name_en, index})
     */
    public JsonElement getSymbols(SymbolType type) {
        return getSymbols(type != null ? type.value() : null, null);
    }

    /**
     * List symbols of one product type, optionally restricted to specific codes.
     *
     * @param type    product type
     * @param symbols optional comma-separated codes
     * @return symbol list
     */
    public JsonElement getSymbols(SymbolType type, String symbols) {
        return getSymbols(type != null ? type.value() : null, symbols);
    }

    /**
     * List the symbols of one product type.
     *
     * @param type product type: {@code STOCK_US STOCK_CN STOCK_HK STOCK_JP STOCK_KS
     *             STOCK_IN CRYPTO FOREX FUTURES} (see {@link SymbolType})
     * @return symbol list
     */
    public JsonElement getSymbols(String type) {
        return getSymbols(type, null);
    }

    /**
     * List symbols, optionally restricted to specific codes.
     *
     * @param type    product type (required by the server; sending {@code market} instead
     *                yields HTTP 400 {@code Required parameter 'type' is not present.})
     * @param symbols optional comma-separated codes, e.g. {@code "AAPL.US,TSLA.US"}
     * @return symbol list
     */
    public JsonElement getSymbols(String type, String symbols) {
        Map<String, String> params = new LinkedHashMap<>();
        put(params, "type", type);
        put(params, "symbols", symbols);
        return http.get("/common/basic/symbols", params);
    }

    /**
     * Detailed symbol information (exchange, currency, lot size, shares, eps, bps ...).
     *
     * @param type    product type (see {@link SymbolType})
     * @param symbols comma-separated codes, at most 500
     * @return symbol details
     */
    public JsonElement getSymbolInfo(SymbolType type, String symbols) {
        return getSymbolInfo(type != null ? type.value() : null, symbols);
    }

    /**
     * Detailed symbol information.
     *
     * @param type    product type (see {@link SymbolType})
     * @param symbols comma-separated codes, at most 500
     * @return symbol details
     */
    public JsonElement getSymbolInfo(String type, String symbols) {
        Map<String, String> params = new LinkedHashMap<>();
        put(params, "type", type);
        put(params, "symbols", symbols);
        return http.get("/common/basic/symbols/info", params);
    }

    /**
     * Forward adjustment factors for one symbol over a day range.
     *
     * @param symbol   single symbol, e.g. {@code "AAPL.US"}
     * @param market   market code, e.g. {@code "US"}
     * @param beginDay start day, {@code YYYYMMDD}
     * @param endDay   end day, {@code YYYYMMDD}
     * @return list of {@code symbol, market, trade_date, forward_factor}
     */
    public JsonElement getAdjustmentFactors(String symbol, String market, String beginDay, String endDay) {
        Map<String, String> params = new LinkedHashMap<>();
        put(params, "symbol", symbol);
        put(params, "market", market);
        put(params, "beginDay", beginDay);
        put(params, "endDay", endDay);
        return http.get("/common/basic/symbols/adjustment_factors", params);
    }

    public JsonElement getAdjustmentFactors(String symbol, Market market, String beginDay, String endDay) {
        return getAdjustmentFactors(symbol, market != null ? market.value() : null, beginDay, endDay);
    }

    /**
     * Trading days of a market over a day range.
     *
     * @param market   market code, e.g. {@code "US"}
     * @param beginDay start day, {@code YYYYMMDD} (required — omitting it returns HTTP 400)
     * @param endDay   end day, {@code YYYYMMDD}
     * @return object with {@code trade_days[]} and {@code half_trade_days[]} (an object, not an array)
     */
    public JsonElement getTradingDays(String market, String beginDay, String endDay) {
        Map<String, String> params = new LinkedHashMap<>();
        put(params, "market", market);
        put(params, "beginDay", beginDay);
        put(params, "endDay", endDay);
        return http.get("/common/basic/markets/trading_days", params);
    }

    public JsonElement getTradingDays(Market market, String beginDay, String endDay) {
        return getTradingDays(market != null ? market.value() : null, beginDay, endDay);
    }

    /**
     * Trading schedule (sessions, holidays, break times) for non-equity products.
     *
     * <p>The server has no {@code market} filter — this overload keeps the old
     * signature and returns the full schedule, same as {@link #getTradingSchedule()}.
     * Filter with {@link #getTradingScheduleByType(ScheduleType)}.</p>
     *
     * @param market ignored; the path does not accept a market code
     * @return trading schedule list
     */
    public JsonElement getTradingSchedule(String market) {
        return getTradingSchedule();
    }

    public JsonElement getTradingSchedule(Market market) {
        return getTradingSchedule();
    }

    /**
     * Full trading schedule, unfiltered.
     *
     * @return trading schedule list
     */
    public JsonElement getTradingSchedule() {
        return http.get("/common/basic/markets/trading_schedule");
    }

    /**
     * Trading schedule filtered by product type.
     *
     * @param type one of {@code ENERGY FOREX FUTURES METAL INDICES}
     * @return trading schedule list
     * @throws IllegalArgumentException if {@code type} is not a {@link ScheduleType}
     */
    public JsonElement getTradingScheduleByType(String type) {
        if (type != null && !type.isEmpty() && ScheduleType.fromValue(type) == null) {
            throw new IllegalArgumentException(
                    "type must be one of ENERGY/FOREX/FUTURES/METAL/INDICES, not " + type);
        }
        Map<String, String> params = new LinkedHashMap<>();
        put(params, "type", type);
        return http.get("/common/basic/markets/trading_schedule", params);
    }

    public JsonElement getTradingScheduleByType(ScheduleType type) {
        return getTradingScheduleByType(type != null ? type.value() : null);
    }

    /**
     * @param type product type; only {@link SymbolType#ENERGY} / {@code FOREX} /
     *             {@code FUTURES} / {@code METAL} / {@code INDICES} are valid
     * @return trading schedule list
     * @throws IllegalArgumentException if {@code type} is an equity {@link SymbolType}
     */
    public JsonElement getTradingScheduleByType(SymbolType type) {
        ScheduleType mapped = ScheduleType.fromSymbolType(type);
        if (type != null && mapped == null) {
            throw new IllegalArgumentException(
                    "trading_schedule type must be ENERGY/FOREX/FUTURES/METAL/INDICES, not " + type);
        }
        return getTradingScheduleByType(mapped);
    }

    /**
     * @param market market code
     * @return trading schedule list
     * @deprecated renamed in 0.2.0 — {@code /common/basic/markets/trading_hours} does not exist
     *             (HTTP 404). Use {@link #getTradingSchedule(String)}, or {@link #getMarkets()}
     *             for the per-market session table.
     */
    @Deprecated
    public JsonElement getTradingHours(String market) {
        return getTradingSchedule(market);
    }

    /**
     * @return trading schedule list
     * @deprecated renamed in 0.2.0. Use {@link #getTradingSchedule()}.
     */
    @Deprecated
    public JsonElement getTradingHours() {
        return getTradingSchedule();
    }

    /**
     * Per-market session table ({@code market, remark, trade_schedules[]} with
     * {@code PreTrade/NormalTrade/PostTrade} segments). Takes no parameters.
     *
     * @return market session table
     */
    public JsonElement getMarkets() {
        return http.get("/common/basic/markets");
    }

    /**
     * Single-name profile (logo, FIGI, ISIN, market cap, CEO, …).
     *
     * <p>This is not {@link #getSymbolInfo(String, String)} (batch static fields)
     * and not {@code stockInfo().getCompany(...)} (v2 company overview).</p>
     *
     * @param type   product type (see {@link SymbolType}, including {@link SymbolType#STOCK_TW})
     * @param symbol a single instrument code
     * @return detail object
     */
    public JsonElement getStockDetail(SymbolType type, String symbol) {
        return getStockDetail(type != null ? type.value() : null, symbol);
    }

    /**
     * Single-name profile (logo, FIGI, ISIN, market cap, CEO, …).
     *
     * @param type   product type (see {@link SymbolType})
     * @param symbol a single instrument code
     * @return detail object
     */
    public JsonElement getStockDetail(String type, String symbol) {
        Map<String, String> params = new LinkedHashMap<>();
        put(params, "type", type);
        put(params, "symbol", symbol);
        return http.get("/common/basic/stock/detail", params);
    }

    private static void put(Map<String, String> params, String key, String value) {
        if (value != null && !value.isEmpty()) {
            params.put(key, value);
        }
    }
}
