package io.infoway.sdk.rest;

import com.google.gson.JsonElement;
import io.infoway.sdk.HttpClient;
import io.infoway.sdk.Lang;

/**
 * Stock fundamental data client (valuation, ratings, company info).
 *
 * <p>Every method accepts an optional {@code lang} ({@code en} / {@code zh-CN}) for
 * name-like fields. Valuation and ratings are numeric and ignore {@code lang}.</p>
 */
public class StockInfoClient {

    private final HttpClient http;

    public StockInfoClient(HttpClient http) {
        this.http = http;
    }

    public JsonElement getValuation(String symbol) {
        return getValuation(symbol, (String) null);
    }

    public JsonElement getValuation(String symbol, String lang) {
        return http.get("/common/v2/basic/stock/valuation/" + symbol, Query.of("lang", lang));
    }

    public JsonElement getValuation(String symbol, Lang lang) {
        return getValuation(symbol, lang != null ? lang.value() : null);
    }

    public JsonElement getRatings(String symbol) {
        return getRatings(symbol, (String) null);
    }

    public JsonElement getRatings(String symbol, String lang) {
        return http.get("/common/v2/basic/stock/ratings/" + symbol, Query.of("lang", lang));
    }

    public JsonElement getRatings(String symbol, Lang lang) {
        return getRatings(symbol, lang != null ? lang.value() : null);
    }

    public JsonElement getCompany(String symbol) {
        return getCompany(symbol, (String) null);
    }

    public JsonElement getCompany(String symbol, String lang) {
        return http.get("/common/v2/basic/stock/company/" + symbol, Query.of("lang", lang));
    }

    public JsonElement getCompany(String symbol, Lang lang) {
        return getCompany(symbol, lang != null ? lang.value() : null);
    }

    public JsonElement getPanorama(String symbol) {
        return getPanorama(symbol, (String) null);
    }

    public JsonElement getPanorama(String symbol, String lang) {
        return http.get("/common/v2/basic/stock/panorama/" + symbol, Query.of("lang", lang));
    }

    public JsonElement getPanorama(String symbol, Lang lang) {
        return getPanorama(symbol, lang != null ? lang.value() : null);
    }

    public JsonElement getConcepts(String symbol) {
        return getConcepts(symbol, (String) null);
    }

    public JsonElement getConcepts(String symbol, String lang) {
        return http.get("/common/v2/basic/stock/concepts/" + symbol, Query.of("lang", lang));
    }

    public JsonElement getConcepts(String symbol, Lang lang) {
        return getConcepts(symbol, lang != null ? lang.value() : null);
    }

    public JsonElement getEvents(String symbol, int limit) {
        return getEvents(symbol, limit, (String) null);
    }

    public JsonElement getEvents(String symbol, int limit, String lang) {
        return http.get("/common/v2/basic/stock/events/" + symbol,
                Query.of("limit", String.valueOf(limit), "lang", lang));
    }

    public JsonElement getEvents(String symbol, int limit, Lang lang) {
        return getEvents(symbol, limit, lang != null ? lang.value() : null);
    }

    public JsonElement getEvents(String symbol) {
        return getEvents(symbol, 20);
    }

    public JsonElement getDrivers(String symbol) {
        return getDrivers(symbol, (String) null);
    }

    public JsonElement getDrivers(String symbol, String lang) {
        return http.get("/common/v2/basic/stock/drivers/" + symbol, Query.of("lang", lang));
    }

    public JsonElement getDrivers(String symbol, Lang lang) {
        return getDrivers(symbol, lang != null ? lang.value() : null);
    }
}
