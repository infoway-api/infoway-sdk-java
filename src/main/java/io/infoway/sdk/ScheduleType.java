package io.infoway.sdk;

/**
 * Product types accepted by {@code /common/basic/markets/trading_schedule}.
 *
 * <p>This is not {@link SymbolType}: equity values such as {@code STOCK_US}
 * return HTTP 400 {@code type must be one of ENERGY/FOREX/FUTURES/METAL/INDICES}.</p>
 */
public enum ScheduleType {

    ENERGY,
    FOREX,
    FUTURES,
    METAL,
    INDICES;

    public String value() {
        return name();
    }

    public static ScheduleType fromValue(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        for (ScheduleType value : values()) {
            if (value.name().equalsIgnoreCase(type.trim())) {
                return value;
            }
        }
        return null;
    }

    /** Maps the non-equity {@link SymbolType} constants; stock types return {@code null}. */
    public static ScheduleType fromSymbolType(SymbolType type) {
        if (type == null) {
            return null;
        }
        return fromValue(type.value());
    }
}
