package io.infoway.sdk.rest;

import com.google.gson.JsonElement;
import io.infoway.sdk.HttpClient;
import io.infoway.sdk.Lang;
import io.infoway.sdk.Market;
import io.infoway.sdk.RankSort;
import io.infoway.sdk.SortOrder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Market overview client (temperature, breadth, turnover, indexes, leaders, ranks).
 *
 * <p>Most endpoints accept an optional {@code lang} query ({@code en} or {@code zh-CN}).
 * Supported equity markets are {@code HK}, {@code US}, {@code CN}.</p>
 */
public class MarketClient {

    private final HttpClient http;

    public MarketClient(HttpClient http) {
        this.http = http;
    }

    /**
     * Get market temperature / sentiment overview.
     *
     * @param market comma-separated market codes (e.g. "HK,US")
     * @return temperature data as a JsonElement
     */
    public JsonElement getTemperature(String market) {
        return getTemperature(market, (String) null);
    }

    /**
     * Get market temperature / sentiment overview.
     *
     * @param market comma-separated market codes
     * @param lang   {@code en} or {@code zh-CN}; {@code null} keeps the server default
     * @return temperature data as a JsonElement
     */
    public JsonElement getTemperature(String market, String lang) {
        return http.get("/common/v2/basic/market/temperature", Query.of("market", market, "lang", lang));
    }

    public JsonElement getTemperature(Market... markets) {
        return getTemperature(Market.join(markets), (String) null);
    }

    public JsonElement getTemperature(Lang lang, Market... markets) {
        return getTemperature(Market.join(markets), lang != null ? lang.value() : null);
    }

    public JsonElement getTemperature(String market, Lang lang) {
        return getTemperature(market, lang != null ? lang.value() : null);
    }

    /**
     * Get market temperature with default markets (HK,US,CN).
     *
     * @return temperature data as a JsonElement
     */
    public JsonElement getTemperature() {
        return getTemperature("HK,US,CN");
    }

    /**
     * Get market breadth (advance/decline) data.
     *
     * @param market market code (e.g. "HK", "US")
     * @return breadth data as a JsonElement
     */
    public JsonElement getBreadth(String market) {
        return getBreadth(market, (String) null);
    }

    /**
     * Get market breadth (advance/decline) data.
     *
     * @param market market code
     * @param lang   unused on this endpoint (numbers only); accepted for call-site consistency
     * @return breadth data as a JsonElement
     */
    public JsonElement getBreadth(String market, String lang) {
        return http.get("/common/v2/basic/market/breadth/" + market, Query.of("lang", lang));
    }

    public JsonElement getBreadth(Market market) {
        return getBreadth(market != null ? market.value() : null, (String) null);
    }

    public JsonElement getBreadth(Market market, Lang lang) {
        return getBreadth(market != null ? market.value() : null, lang != null ? lang.value() : null);
    }

    /**
     * Market-wide turnover plus yesterday same-time / full-day comparison.
     *
     * @param market market code ({@code CN} / {@code HK} / {@code US})
     * @return turnover payload
     */
    public JsonElement getTurnover(String market) {
        return getTurnover(market, (String) null);
    }

    /**
     * Market-wide turnover plus yesterday same-time / full-day comparison.
     *
     * @param market market code
     * @param lang   {@code en} or {@code zh-CN}
     * @return turnover payload
     */
    public JsonElement getTurnover(String market, String lang) {
        return http.get("/common/v2/basic/market/turnover/" + market, Query.of("lang", lang));
    }

    public JsonElement getTurnover(Market market) {
        return getTurnover(market != null ? market.value() : null, (String) null);
    }

    public JsonElement getTurnover(Market market, Lang lang) {
        return getTurnover(market != null ? market.value() : null, lang != null ? lang.value() : null);
    }

    /**
     * Get major market indexes.
     *
     * @return indexes data as a JsonElement
     */
    public JsonElement getIndexes() {
        return getIndexes((String) null);
    }

    /**
     * Get major market indexes.
     *
     * @param lang {@code en} or {@code zh-CN}
     * @return indexes data as a JsonElement
     */
    public JsonElement getIndexes(String lang) {
        return http.get("/common/v2/basic/market/indexes", Query.of("lang", lang));
    }

    public JsonElement getIndexes(Lang lang) {
        return getIndexes(lang != null ? lang.value() : null);
    }

    /**
     * Get market leaders (top movers).
     *
     * @param market market code
     * @param limit  max number of results
     * @return leaders data as a JsonElement
     */
    public JsonElement getLeaders(String market, int limit) {
        return getLeaders(market, limit, (String) null);
    }

    /**
     * Get market leaders (top movers).
     *
     * @param market market code
     * @param limit  max number of results (1-50)
     * @param lang   {@code en} or {@code zh-CN}
     * @return leaders data as a JsonElement
     */
    public JsonElement getLeaders(String market, int limit, String lang) {
        return http.get("/common/v2/basic/market/leaders/" + market,
                Query.of("limit", String.valueOf(limit), "lang", lang));
    }

    public JsonElement getLeaders(Market market, int limit) {
        return getLeaders(market != null ? market.value() : null, limit, (String) null);
    }

    public JsonElement getLeaders(Market market, int limit, Lang lang) {
        return getLeaders(market != null ? market.value() : null, limit, lang != null ? lang.value() : null);
    }

    public JsonElement getLeaders(Market market) {
        return getLeaders(market, 10);
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
     * Auto-generated market commentary for the session.
     *
     * @param market market code
     * @return {@code {market, overview}}
     */
    public JsonElement getOverview(String market) {
        return getOverview(market, (String) null);
    }

    /**
     * Auto-generated market commentary for the session.
     *
     * @param market market code
     * @param lang   {@code en} or {@code zh-CN}
     * @return {@code {market, overview}}
     */
    public JsonElement getOverview(String market, String lang) {
        return http.get("/common/v2/basic/market/overview/" + market, Query.of("lang", lang));
    }

    public JsonElement getOverview(Market market) {
        return getOverview(market != null ? market.value() : null, (String) null);
    }

    public JsonElement getOverview(Market market, Lang lang) {
        return getOverview(market != null ? market.value() : null, lang != null ? lang.value() : null);
    }

    /**
     * List rank boards and the sort keys each board accepts.
     *
     * @param market market code
     * @return rank category list
     */
    public JsonElement getRankCategories(String market) {
        return getRankCategories(market, (String) null);
    }

    /**
     * List rank boards and the sort keys each board accepts.
     *
     * @param market market code
     * @param lang   {@code en} or {@code zh-CN}
     * @return rank category list
     */
    public JsonElement getRankCategories(String market, String lang) {
        return http.get("/common/v2/basic/market/rank/categories/" + market, Query.of("lang", lang));
    }

    public JsonElement getRankCategories(Market market) {
        return getRankCategories(market != null ? market.value() : null, (String) null);
    }

    public JsonElement getRankCategories(Market market, Lang lang) {
        return getRankCategories(market != null ? market.value() : null, lang != null ? lang.value() : null);
    }

    /**
     * Rank list (gainers / losers / heat, …). Defaults to {@code sort=chg}, {@code order=desc},
     * {@code limit=30}, {@code offset=0}.
     *
     * @param market market code
     * @param key    board id from {@link #getRankCategories(String)}
     * @return rank rows
     */
    public JsonElement getRank(String market, String key) {
        return getRank(market, key, null, null, null, null, null);
    }

    /**
     * Rank list with explicit sort / paging.
     *
     * @param market market code
     * @param key    board id from {@link #getRankCategories(String)}
     * @param sort   indicator key (default {@code chg})
     * @param order  {@code desc} or {@code asc}
     * @param limit  1-100 (default 30)
     * @param offset page offset (default 0)
     * @return rank rows
     */
    public JsonElement getRank(String market, String key, String sort, String order,
                               Integer limit, Integer offset) {
        return getRank(market, key, sort, order, limit, offset, null);
    }

    /**
     * Rank list with explicit sort / paging / language.
     *
     * @param market market code
     * @param key    board id from {@link #getRankCategories(String)}
     * @param sort   indicator key (default {@code chg})
     * @param order  {@code desc} or {@code asc}
     * @param limit  1-100 (default 30)
     * @param offset page offset (default 0)
     * @param lang   {@code en} or {@code zh-CN}
     * @return rank rows
     */
    public JsonElement getRank(String market, String key, String sort, String order,
                               Integer limit, Integer offset, String lang) {
        Map<String, String> params = new LinkedHashMap<>();
        Query.put(params, "sort", sort);
        Query.put(params, "order", order);
        Query.put(params, "limit", limit);
        Query.put(params, "offset", offset);
        Query.put(params, "lang", lang);
        return http.get("/common/v2/basic/market/rank/" + market + "/" + key,
                params.isEmpty() ? null : params);
    }

    public JsonElement getRank(Market market, String key) {
        return getRank(market != null ? market.value() : null, key);
    }

    public JsonElement getRank(Market market, String key, RankSort sort, SortOrder order,
                               Integer limit, Integer offset, Lang lang) {
        return getRank(
                market != null ? market.value() : null,
                key,
                sort != null ? sort.value() : null,
                order != null ? order.value() : null,
                limit,
                offset,
                lang != null ? lang.value() : null);
    }

    /**
     * @param market market code
     * @return rank config as a JsonElement
     * @deprecated the {@code /rank-config/{market}} path is not on the server (HTTP 404).
     *             Use {@link #getRankCategories(String)} and {@link #getRank(String, String)}.
     */
    @Deprecated
    public JsonElement getRankConfig(String market) {
        return http.get("/common/v2/basic/market/rank-config/" + market);
    }
}
