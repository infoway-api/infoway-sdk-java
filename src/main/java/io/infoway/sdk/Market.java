package io.infoway.sdk;

/**
 * Equity market codes used by {@code market}, {@code plate} and calendar endpoints.
 *
 * <p>Temperature accepts a comma-separated list — use {@link #join(Market...)}.</p>
 */
public enum Market {

    HK,
    US,
    CN,
    JP,
    KS,
    TW,
    IN;

    public String value() {
        return name();
    }

    public static String join(Market... markets) {
        if (markets == null || markets.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Market market : markets) {
            if (market == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(market.value());
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
