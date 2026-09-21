package io.infoway.sdk;

/**
 * Business {@code ret} values from the HTTP quote service and the
 * {@code 200}/{@code 400}/{@code 500} envelope used by {@code /common/basic/*}.
 *
 * <p>These numbers overlap WebSocket {@link WsErrorCode} for 508–514 but
 * <b>mean different things</b>. Use this enum for REST {@code InfowayApiException}
 * and {@link WsErrorCode} for WebSocket {@code onError}.</p>
 */
public enum RestErrorCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "Bad request"),
    SERVER_ERROR(500, "Server error"),
    REQUEST_EXCEED_LIMIT(501, "Request frequency exceed the limit"),
    REQUEST_FOR_DAY_LIMIT(502, "Request frequency for the day upper limit"),
    KLINE_EXCEEDS_LIMIT(503, "Kline quantity exceeds the limit"),
    ORDER_BOOK_DEPTH_EXCEEDS_LIMIT(504, "Orderbook depth exceeds the limit"),
    PRODUCTS_EXCEEDS_LIMIT(505, "Products quantity exceeds the limit"),
    PARAM_ERROR(506, "Param error"),
    PARAM_LOST(507, "Param lost"),
    PRODUCT_NOT_EXISTS(508, "All product not exists"),
    TOKEN_PERMISSION_EXPIRED(509, "The token permission has expired"),
    WEBSOCKET_EXCEEDS_LIMIT(510, "WebSocket connections exceeds the limit"),
    WEBSOCKET_HEARTBEAT_TIMEOUT(511, "Websocket heartbeat timeout"),
    WEBSOCKET_DISCONNECTED(512, "WebSocket disconnected"),
    TIME_LIMIT_ERROR(513, "Timestamp limit error"),
    NO_PERMISSION(514, "No permission");

    private final int code;
    private final String label;

    RestErrorCode(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String label() {
        return label;
    }

    public static RestErrorCode fromCode(int code) {
        for (RestErrorCode value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
