package io.infoway.sdk.rest;

import com.google.gson.JsonElement;
import io.infoway.sdk.HttpClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Basic information client (symbols, trading days, trading hours).
 */
public class BasicClient {

    private final HttpClient http;

    public BasicClient(HttpClient http) {
        this.http = http;
    }

    /**
     * Get available symbols.
     *
     * @param market optional market filter (e.g. "HK", "US")
     * @return symbol list as a JsonElement
     */
    public JsonElement getSymbols(String market) {
        Map<String, String> params = null;
        if (market != null && !market.isEmpty()) {
            params = new HashMap<>();
            params.put("market", market);
        }
        return http.get("/common/basic/symbols", params);
    }

    /**
     * Get available symbols for all markets.
     *
     * @return symbol list as a JsonElement
     */
    public JsonElement getSymbols() {
        return getSymbols(null);
    }

    /**
     * Get detailed symbol information.
     *
     * @param codes comma-separated symbol codes
     * @return symbol info as a JsonElement
     */
    public JsonElement getSymbolInfo(String codes) {
        Map<String, String> params = new HashMap<>();
        params.put("codes", codes);
        return http.get("/common/basic/symbols/info", params);
    }

    /**
     * Get adjustment factors for symbols.
     *
     * @param codes comma-separated symbol codes
     * @return adjustment factors as a JsonElement
     */
    public JsonElement getAdjustmentFactors(String codes) {
        Map<String, String> params = new HashMap<>();
        params.put("codes", codes);
        return http.get("/common/basic/symbols/adjustment_factors", params);
    }

    /**
     * Get trading days for a market.
     *
     * @param market market code (e.g. "HK", "US")
     * @return trading days as a JsonElement
     */
    public JsonElement getTradingDays(String market) {
        Map<String, String> params = new HashMap<>();
        params.put("market", market);
        return http.get("/common/basic/markets/trading_days", params);
    }

    /**
     * Get trading hours for markets.
     *
     * @param market optional market filter
     * @return trading hours as a JsonElement
     */
    public JsonElement getTradingHours(String market) {
        Map<String, String> params = null;
        if (market != null && !market.isEmpty()) {
            params = new HashMap<>();
            params.put("market", market);
        }
        return http.get("/common/basic/markets", params);
    }

    /**
     * Get trading hours for all markets.
     *
     * @return trading hours as a JsonElement
     */
    public JsonElement getTradingHours() {
        return getTradingHours(null);
    }
}
