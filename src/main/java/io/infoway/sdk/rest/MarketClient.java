package io.infoway.sdk.rest;

import com.google.gson.JsonElement;
import io.infoway.sdk.HttpClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Market overview client (temperature, breadth, indexes, leaders).
 */
public class MarketClient {

    private final HttpClient http;

    public MarketClient(HttpClient http) {
        this.http = http;
    }

    /**
     * Get market temperature / sentiment overview.
     *
     * @param market comma-separated market codes (e.g. "HK,US,CN,SG")
     * @return temperature data as a JsonElement
     */
    public JsonElement getTemperature(String market) {
        Map<String, String> params = new HashMap<>();
        params.put("market", market);
        return http.get("/common/v2/basic/market/temperature", params);
    }

    /**
     * Get market temperature with default markets (HK,US,CN,SG).
     *
     * @return temperature data as a JsonElement
     */
    public JsonElement getTemperature() {
        return getTemperature("HK,US,CN,SG");
    }

    /**
     * Get market breadth (advance/decline) data.
     *
     * @param market market code (e.g. "HK", "US")
     * @return breadth data as a JsonElement
     */
    public JsonElement getBreadth(String market) {
        return http.get("/common/v2/basic/market/breadth/" + market);
    }

    /**
     * Get major market indexes.
     *
     * @return indexes data as a JsonElement
     */
    public JsonElement getIndexes() {
        return http.get("/common/v2/basic/market/indexes");
    }

    /**
     * Get market leaders (top movers).
     *
     * @param market market code
     * @param limit  max number of results
     * @return leaders data as a JsonElement
     */
    public JsonElement getLeaders(String market, int limit) {
        Map<String, String> params = new HashMap<>();
        params.put("limit", String.valueOf(limit));
        return http.get("/common/v2/basic/market/leaders/" + market, params);
    }

    /**
     * Get market leaders with default limit (10).
     *
     * @param market market code
     * @return leaders data as a JsonElement
     */
    public JsonElement getLeaders(String market) {
        return getLeaders(market, 10);
    }

    /**
     * Get rank configuration for a market.
     *
     * @param market market code
     * @return rank config as a JsonElement
     */
    public JsonElement getRankConfig(String market) {
        return http.get("/common/v2/basic/market/rank-config/" + market);
    }
}
