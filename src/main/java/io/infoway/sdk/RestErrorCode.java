package io.infoway.sdk;

/**
 * Business {@code ret} values from the HTTP quote service
 * ({@code ResponceCodeEnum} on {@code infoway-httpApi-server}) and the
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

    /**
     * Production sometimes wraps a business failure as {@code ret=500} while
     * {@code msg} is still the English template from {@code ResponceCodeEnum}.
     * The httpApi service itself puts 503/505/513 on {@code ret}; this keeps
     * both shapes distinguishable.
     *
     * @param ret wire {@code ret}
     * @param msg wire {@code msg}
     * @return the specific business code when {@code ret} is 500 and {@code msg}
     *         starts with a known template; otherwise {@code ret}
     */
    public static int classify(int ret, String msg) {
        if (ret != SERVER_ERROR.code || msg == null || msg.isBlank()) {
            return ret;
        }
        String text = msg.strip().toLowerCase(java.util.Locale.ROOT);
        for (RestErrorCode value : values()) {
            if (value == SUCCESS || value == BAD_REQUEST || value == SERVER_ERROR) {
                continue;
            }
            if (text.startsWith(value.label.toLowerCase(java.util.Locale.ROOT))) {
                return value.code;
            }
        }
        return ret;
    }
}
