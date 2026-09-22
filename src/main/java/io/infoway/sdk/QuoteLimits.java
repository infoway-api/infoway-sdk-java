package io.infoway.sdk;

import io.infoway.sdk.exception.InfowayApiException;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Server-side caps for HTTP quote requests.
 *
 * <p>These are not the package field {@code maxNum}. That field is a subscription
 * quota (often 600). Trade, depth and kline reject more than
 * {@link #MAX_SYMBOLS} symbols, and a single-symbol kline rejects more than
 * {@link #MAX_KLINE_BARS} bars. Several symbols in one kline request are capped
 * at {@link #MAX_KLINE_BARS_WHEN_BATCHED} bars; the server used to apply that
 * cap silently.</p>
 */
public final class QuoteLimits {

    /** {@code query.max.num}. */
    public static final int MAX_SYMBOLS = 100;

    /** {@code query.max.klineMaxNum}. */
    public static final int MAX_KLINE_BARS = 500;

    /** {@code adjustKlineNum} when more than one symbol is requested. */
    public static final int MAX_KLINE_BARS_WHEN_BATCHED = 2;

    private QuoteLimits() {}

    /** Distinct, non-blank codes in a comma-separated list. */
    public static int symbolCount(String codes) {
        return symbols(codes).size();
    }

    public static void checkSymbols(String codes) {
        int count = symbolCount(codes);
        if (count > MAX_SYMBOLS) {
            throw InfowayApiException.ofRest(
                    RestErrorCode.PRODUCTS_EXCEEDS_LIMIT.getCode(),
                    "Products quantity exceeds the limit：" + MAX_SYMBOLS,
                    null);
        }
    }

    public static void checkKline(String codes, int count) {
        checkSymbols(codes);
        if (symbolCount(codes) > 1 && count > MAX_KLINE_BARS_WHEN_BATCHED) {
            throw InfowayApiException.ofRest(
                    RestErrorCode.PARAM_ERROR.getCode(),
                    "Param error：klineNum exceeds " + MAX_KLINE_BARS_WHEN_BATCHED
                            + " when requesting multiple symbols",
                    null);
        }
        if (count > MAX_KLINE_BARS) {
            throw InfowayApiException.ofRest(
                    RestErrorCode.KLINE_EXCEEDS_LIMIT.getCode(),
                    "Kline quantity exceeds the limit：" + MAX_KLINE_BARS,
                    null);
        }
    }

    private static Set<String> symbols(String codes) {
        Set<String> symbols = new LinkedHashSet<>();
        if (codes == null || codes.isBlank()) {
            return symbols;
        }
        for (String part : codes.split(",")) {
            String token = part.trim();
            if (!token.isEmpty()) {
                symbols.add(token);
            }
        }
        return symbols;
    }
}
