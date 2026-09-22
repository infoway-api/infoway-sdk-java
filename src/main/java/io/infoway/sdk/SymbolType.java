package io.infoway.sdk;

/**
 * Values accepted by the {@code type} parameter of {@code /common/basic/symbols*}
 * and {@code /common/basic/financial/*} / {@code /common/basic/stock/detail}.
 *
 * <p>Verified against the public docs. Passing a market code such as {@code "US"}
 * instead of {@code STOCK_US} returns HTTP 400
 * {@code Required parameter 'type' is not present.}</p>
 */
public enum SymbolType {

    STOCK_US,
    STOCK_CN,
    STOCK_HK,
    STOCK_JP,
    STOCK_KS,
    STOCK_IN,
    STOCK_TW,
    CRYPTO,
    FOREX,
    FUTURES,
    ENERGY,
    METAL,
    INDICES;

    /** The wire value sent as {@code type=}. */
    public String value() {
        return name();
    }

    /** {@code null} when {@code value} is not one of the enum names. */
    public static SymbolType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (SymbolType type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return null;
    }
}
