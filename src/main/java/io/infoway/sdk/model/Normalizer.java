package io.infoway.sdk.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Optional normalisation layer: turns the raw wire format into typed models.
 *
 * <p>Raw {@code JsonElement} remains the default channel of every client — nothing here
 * changes existing behaviour. Use these helpers when you want the server's quirks absorbed:</p>
 *
 * <table border="1">
 *   <caption>What gets normalised</caption>
 *   <tr><th>On the wire</th><th>Here</th></tr>
 *   <tr><td>prices/volumes as strings ({@code "305.771"})</td><td>{@link BigDecimal}</td></tr>
 *   <tr><td>{@code t}: number of <b>milliseconds</b> for trade/depth, string of
 *           <b>seconds</b> for kline</td>
 *       <td>{@link Instant} — the unit is decided per field, never guessed from digit count</td></tr>
 *   <tr><td>change percent: {@code pc} on REST, {@code pfr} on WS, value {@code "0.03%"}</td>
 *       <td>{@code changePercent} as a decimal fraction ({@code 0.0003})</td></tr>
 *   <tr><td>kline nested in {@code respList}</td><td>flattened into one list</td></tr>
 *   <tr><td>depth {@code a}/{@code b} as transposed columns
 *           {@code [[price...],[qty...]]}</td><td>{@code List<DepthLevel>}</td></tr>
 *   <tr><td>{@code vw} — the traded amount, not a VWAP</td><td>{@code turnover}</td></tr>
 * </table>
 *
 * <pre>{@code
 * List<Trade> trades = Normalizer.trades(client.stock().getTrade("AAPL.US"));
 * ws.setOnKline(data -> handle(Normalizer.kline(data)));
 * }</pre>
 */
public final class Normalizer {

    private Normalizer() {}

    // ---- trade -----------------------------------------------------------------

    /**
     * Normalise a REST trade payload (the {@code data} array).
     *
     * @param data the {@code data} element of a batch_trade response
     * @return one {@link Trade} per element, never null
     */
    public static List<Trade> trades(JsonElement data) {
        return mapArray(data, Normalizer::trade);
    }

    /**
     * Normalise a single trade object (REST element or WS 10002 payload).
     *
     * @param o trade object
     * @return typed trade
     */
    public static Trade trade(JsonObject o) {
        if (o == null) {
            return null;
        }
        return new Trade(
                string(o, "s"),
                epochMillis(o, "t"),
                decimal(o, "p"),
                decimal(o, "v"),
                decimal(o, "vw"),
                integer(o, "td") != null ? integer(o, "td") : 0,
                string(o, "ty"));
    }

    // ---- depth -----------------------------------------------------------------

    /**
     * Normalise a REST depth payload (the {@code data} array).
     *
     * @param data the {@code data} element of a batch_depth response
     * @return one {@link Depth} per element, never null
     */
    public static List<Depth> depths(JsonElement data) {
        return mapArray(data, Normalizer::depth);
    }

    /**
     * Normalise a single order book (REST element or WS 10005 payload).
     *
     * @param o depth object with transposed {@code a}/{@code b} columns
     * @return typed order book
     */
    public static Depth depth(JsonObject o) {
        if (o == null) {
            return null;
        }
        return new Depth(string(o, "s"), epochMillis(o, "t"), levels(o, "a"), levels(o, "b"));
    }

    /** {@code [[price...],[qty...]]} → {@code [(price, qty), ...]}. */
    private static List<DepthLevel> levels(JsonObject o, String key) {
        JsonElement side = o.get(key);
        if (side == null || !side.isJsonArray()) {
            return Collections.emptyList();
        }
        JsonArray columns = side.getAsJsonArray();
        if (columns.size() < 2 || !columns.get(0).isJsonArray() || !columns.get(1).isJsonArray()) {
            return Collections.emptyList();
        }
        JsonArray prices = columns.get(0).getAsJsonArray();
        JsonArray quantities = columns.get(1).getAsJsonArray();
        int depth = Math.min(prices.size(), quantities.size());
        List<DepthLevel> out = new ArrayList<>(depth);
        for (int i = 0; i < depth; i++) {
            out.add(new DepthLevel(decimal(prices.get(i)), decimal(quantities.get(i))));
        }
        return out;
    }

    // ---- kline -----------------------------------------------------------------

    /**
     * Normalise a REST kline payload, flattening the {@code respList} nesting.
     *
     * @param data the {@code data} element of a v2/batch_kline response
     * @return every bar of every symbol, in server order
     */
    public static List<Kline> klines(JsonElement data) {
        List<Kline> out = new ArrayList<>();
        if (data == null || !data.isJsonArray()) {
            return out;
        }
        for (JsonElement element : data.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            JsonElement bars = entry.get("respList");
            if (bars != null && bars.isJsonArray()) {
                String symbol = string(entry, "s");
                for (JsonElement bar : bars.getAsJsonArray()) {
                    if (bar.isJsonObject()) {
                        out.add(kline(bar.getAsJsonObject(), symbol));
                    }
                }
            } else {
                out.add(kline(entry));
            }
        }
        return out;
    }

    /**
     * Normalise a single bar (WS 10008 payload or one {@code respList} entry).
     *
     * @param o bar object
     * @return typed bar
     */
    public static Kline kline(JsonObject o) {
        return kline(o, null);
    }

    private static Kline kline(JsonObject o, String fallbackSymbol) {
        if (o == null) {
            return null;
        }
        String symbol = string(o, "s");
        BigDecimal changePercent = percent(o.has("pc") ? string(o, "pc") : string(o, "pfr"));
        return new Kline(
                symbol != null ? symbol : fallbackSymbol,
                epochSeconds(o, "t"),          // kline timestamps are SECONDS, on both transports
                decimal(o, "o"),
                decimal(o, "h"),
                decimal(o, "l"),
                decimal(o, "c"),
                decimal(o, "v"),
                decimal(o, "vw"),
                decimal(o, "pca"),
                changePercent,
                integer(o, "ty"));
    }

    // ---- news -------------------------------------------------------------------

    /**
     * Normalise a news push payload (WS 10022).
     *
     * @param o news object
     * @return typed news item
     */
    public static NewsItem news(JsonObject o) {
        if (o == null) {
            return null;
        }
        List<String> symbols = new ArrayList<>();
        JsonElement symbolArray = o.get("symbols");
        if (symbolArray != null && symbolArray.isJsonArray()) {
            for (JsonElement s : symbolArray.getAsJsonArray()) {
                if (s.isJsonPrimitive()) {
                    symbols.add(s.getAsString());
                }
            }
        }
        return new NewsItem(
                string(o, "dk"),
                string(o, "country"),
                string(o, "lang"),
                string(o, "route"),
                string(o, "title"),
                epochSeconds(o, "published"),
                integer(o, "urgency"),
                string(o, "provider"),
                symbols,
                string(o, "link"),
                string(o, "content"),
                string(o, "sd"));
    }

    // ---- primitives --------------------------------------------------------------

    private static <T> List<T> mapArray(JsonElement data, java.util.function.Function<JsonObject, T> fn) {
        List<T> out = new ArrayList<>();
        if (data == null || !data.isJsonArray()) {
            return out;
        }
        for (JsonElement element : data.getAsJsonArray()) {
            if (element.isJsonObject()) {
                out.add(fn.apply(element.getAsJsonObject()));
            }
        }
        return out;
    }

    private static String string(JsonObject o, String key) {
        JsonElement value = o.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        return value.getAsString();
    }

    private static BigDecimal decimal(JsonObject o, String key) {
        JsonElement value = o.get(key);
        return value == null ? null : decimal(value);
    }

    private static BigDecimal decimal(JsonElement value) {
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        try {
            return new BigDecimal(value.getAsString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer integer(JsonObject o, String key) {
        JsonElement value = o.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        try {
            return Integer.valueOf(value.getAsString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code "0.03%"} → {@code 0.0003}; a bare number is treated as already being a percentage. */
    private static BigDecimal percent(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim();
        if (cleaned.endsWith("%")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        try {
            return new BigDecimal(cleaned).movePointLeft(2);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Instant epochMillis(JsonObject o, String key) {
        Long value = epoch(o, key);
        return value == null ? null : Instant.ofEpochMilli(value);
    }

    private static Instant epochSeconds(JsonObject o, String key) {
        Long value = epoch(o, key);
        return value == null ? null : Instant.ofEpochSecond(value);
    }

    private static Long epoch(JsonObject o, String key) {
        JsonElement value = o.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        try {
            return Long.valueOf(value.getAsString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
