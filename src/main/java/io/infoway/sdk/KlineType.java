package io.infoway.sdk;

/**
 * K-line (candlestick) interval types.
 */
public enum KlineType {

    MIN_1(1),
    MIN_5(2),
    MIN_15(3),
    MIN_30(4),
    HOUR_1(5),
    HOUR_2(6),
    HOUR_4(7),
    DAY(8),
    WEEK(9),
    MONTH(10),
    QUARTER(11),
    YEAR(12);

    private final int value;

    KlineType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /**
     * Look up a KlineType by its integer value.
     *
     * @param value integer value (1-12)
     * @return the matching KlineType
     * @throws IllegalArgumentException if no match found
     */
    public static KlineType fromValue(int value) {
        for (KlineType t : values()) {
            if (t.value == value) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown KlineType value: " + value);
    }
}
