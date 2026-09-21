package io.infoway.sdk;

/**
 * Common rank {@code sort} keys. The full catalog is
 * {@code market().getRankCategories(market)}.
 */
public enum RankSort {

    CHG("chg"),
    LAST_DONE("last_done"),
    CHANGE("change"),
    TURNOVER("turnover"),
    VOLUME("volume"),
    AMPLITUDE("amplitude");

    private final String value;

    RankSort(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
