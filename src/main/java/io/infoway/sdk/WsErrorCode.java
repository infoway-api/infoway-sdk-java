package io.infoway.sdk;

/**
 * Server-side WebSocket error codes from {@code WebsocketResponseCode} on the
 * market and news services. These are <b>not</b> subscribe/push protocol numbers
 * ({@link WsCode}); they arrive as
 * {@code {"code":507,"msg":"...","traceId":"..."}} and are delivered to {@code onError}.
 *
 * <p>Numbers are shared across {@code feat/stock|crypto|japan|india|korea|tw|news}.
 * {@code 519}/{@code 520} share a number but differ by channel — see each constant.</p>
 *
 * <p>508–514 collide with {@link RestErrorCode} on HTTP. Do not use this enum
 * to interpret REST {@code ret}.</p>
 */
public enum WsErrorCode {

    SERVER_ERROR(500, "Server error"),
    REQUEST_FREQUENCY_MIN_EXCEED(501, "Request frequency exceed the limit"),
    REQUEST_FREQUENCY_DAY_EXCEED(502, "Request frequency for the day upper limit"),
    KLINE_QUANTITY_EXCEED(503, "Kline quantity exceeds the limit"),
    ORDER_BOOK_DEPTH_EXCEED(504, "Orderbook depth exceeds the limit"),
    PRODUCTS_QUANTITY_EXCEED(505, "Products quantity exceeds the limit"),
    PARAM_ERROR(506, "Param error"),
    PARAM_LOST(507, "Param lost"),
    APIKEY_EXPIRED(508, "API key expired"),
    APIKEY_INVALID(509, "API key invalid"),
    APIKEY_EMPTY(510, "API key empty"),
    APIKEY_BLACKLIST(511, "API key blacklisted"),
    WS_CONN_EXCEED(512, "WebSocket connections exceeds the limit"),
    WS_HEART_TIMEOUT(513, "Websocket heartbeat timeout"),
    WS_URL_WRONG(514, "WebSocket URL wrong"),
    PARAM_NOT_JSON(515, "Param not json"),
    ALL_PRODUCTS_QUANTITY_EXCEED(516, "All products quantity exceeds the limit"),
    /** Handshake: missing apikey (news / korea-style ports). */
    WS_HANDSHAKE_APIKEY_MISSING(517, "Handshake API key missing"),
    /** Handshake: apikey not in token store. */
    WS_HANDSHAKE_APIKEY_NOT_EXIST(518, "Handshake API key not exist"),
    /** News: no newsFlag. Korea: no subscribe permission. */
    WS_HANDSHAKE_NO_PERMISSION(519, "Handshake no permission"),
    /**
     * Korea/Taiwan: product code does not match the market suffix ({@code .KS}/{@code .TW}).
     * News: apikey already has a news connection.
     */
    PRODUCT_CODE_OR_ALREADY_CONNECTED(520, "Product code mismatch or already connected"),
    /** News: server connection cap. */
    WS_HANDSHAKE_MAX_CONNECTIONS(521, "Handshake max connections");

    private final int code;
    private final String label;

    WsErrorCode(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String label() {
        return label;
    }

    public static WsErrorCode fromCode(int code) {
        for (WsErrorCode value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }

    /** {@code true} for the 5xx rejection window the gateway actually sends. */
    public static boolean isError(int code) {
        return code >= 500 && code < 10000 && WsCode.fromCode(code) == null;
    }
}
