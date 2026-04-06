package io.infoway.sdk.rest;

import com.google.gson.JsonElement;
import io.infoway.sdk.HttpClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Stock fundamental data client (valuation, ratings, company info).
 */
public class StockInfoClient {

    private final HttpClient http;

    public StockInfoClient(HttpClient http) {
        this.http = http;
    }

    /**
     * Get valuation data for a symbol.
     *
     * @param symbol symbol code (e.g. "AAPL.US")
     * @return valuation data as a JsonElement
     */
    public JsonElement getValuation(String symbol) {
        return http.get("/common/v2/basic/stock/valuation/" + symbol);
    }

    /**
     * Get analyst ratings for a symbol.
     *
     * @param symbol symbol code
     * @return ratings data as a JsonElement
     */
    public JsonElement getRatings(String symbol) {
        return http.get("/common/v2/basic/stock/ratings/" + symbol);
    }

    /**
     * Get company information for a symbol.
     *
     * @param symbol symbol code
     * @return company data as a JsonElement
     */
    public JsonElement getCompany(String symbol) {
        return http.get("/common/v2/basic/stock/company/" + symbol);
    }

    /**
     * Get panorama (overview) data for a symbol.
     *
     * @param symbol symbol code
     * @return panorama data as a JsonElement
     */
    public JsonElement getPanorama(String symbol) {
        return http.get("/common/v2/basic/stock/panorama/" + symbol);
    }

    /**
     * Get concept tags for a symbol.
     *
     * @param symbol symbol code
     * @return concepts data as a JsonElement
     */
    public JsonElement getConcepts(String symbol) {
        return http.get("/common/v2/basic/stock/concepts/" + symbol);
    }

    /**
     * Get events for a symbol.
     *
     * @param symbol symbol code
     * @param limit  max number of events
     * @return events data as a JsonElement
     */
    public JsonElement getEvents(String symbol, int limit) {
        Map<String, String> params = new HashMap<>();
        params.put("limit", String.valueOf(limit));
        return http.get("/common/v2/basic/stock/events/" + symbol, params);
    }

    /**
     * Get events with default limit (20).
     *
     * @param symbol symbol code
     * @return events data as a JsonElement
     */
    public JsonElement getEvents(String symbol) {
        return getEvents(symbol, 20);
    }

    /**
     * Get price drivers for a symbol.
     *
     * @param symbol symbol code
     * @return drivers data as a JsonElement
     */
    public JsonElement getDrivers(String symbol) {
        return http.get("/common/v2/basic/stock/drivers/" + symbol);
    }
}
