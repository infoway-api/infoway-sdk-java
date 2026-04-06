package io.infoway.sdk.rest;

import com.google.gson.JsonElement;
import io.infoway.sdk.HttpClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Plate (sector) data client.
 */
public class PlateClient {

    private final HttpClient http;

    public PlateClient(HttpClient http) {
        this.http = http;
    }

    /**
     * Get industry plates for a market.
     *
     * @param market market code (e.g. "HK", "US")
     * @param limit  max number of results
     * @return industry data as a JsonElement
     */
    public JsonElement getIndustry(String market, int limit) {
        Map<String, String> params = new HashMap<>();
        params.put("limit", String.valueOf(limit));
        return http.get("/common/v2/basic/plate/industry/" + market, params);
    }

    /**
     * Get industry plates with default limit (200).
     *
     * @param market market code
     * @return industry data as a JsonElement
     */
    public JsonElement getIndustry(String market) {
        return getIndustry(market, 200);
    }

    /**
     * Get concept plates for a market.
     *
     * @param market market code
     * @param limit  max number of results
     * @return concept data as a JsonElement
     */
    public JsonElement getConcept(String market, int limit) {
        Map<String, String> params = new HashMap<>();
        params.put("limit", String.valueOf(limit));
        return http.get("/common/v2/basic/plate/concept/" + market, params);
    }

    /**
     * Get concept plates with default limit (100).
     *
     * @param market market code
     * @return concept data as a JsonElement
     */
    public JsonElement getConcept(String market) {
        return getConcept(market, 100);
    }

    /**
     * Get members of a plate.
     *
     * @param plateSymbol plate symbol code
     * @param offset      pagination offset
     * @param limit       max number of results
     * @return members data as a JsonElement
     */
    public JsonElement getMembers(String plateSymbol, int offset, int limit) {
        Map<String, String> params = new HashMap<>();
        params.put("offset", String.valueOf(offset));
        params.put("limit", String.valueOf(limit));
        return http.get("/common/v2/basic/plate/members/" + plateSymbol, params);
    }

    /**
     * Get members of a plate with default pagination (offset=0, limit=50).
     *
     * @param plateSymbol plate symbol code
     * @return members data as a JsonElement
     */
    public JsonElement getMembers(String plateSymbol) {
        return getMembers(plateSymbol, 0, 50);
    }

    /**
     * Get plate introduction / description.
     *
     * @param plateSymbol plate symbol code
     * @return plate intro as a JsonElement
     */
    public JsonElement getIntro(String plateSymbol) {
        return http.get("/common/v2/basic/plate/intro/" + plateSymbol);
    }

    /**
     * Get plate chart data for a market.
     *
     * @param market market code
     * @param limit  max number of results
     * @return chart data as a JsonElement
     */
    public JsonElement getChart(String market, int limit) {
        Map<String, String> params = new HashMap<>();
        params.put("limit", String.valueOf(limit));
        return http.get("/common/v2/basic/plate/chart/" + market, params);
    }

    /**
     * Get plate chart data with default limit (50).
     *
     * @param market market code
     * @return chart data as a JsonElement
     */
    public JsonElement getChart(String market) {
        return getChart(market, 50);
    }
}
