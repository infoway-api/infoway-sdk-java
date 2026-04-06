package io.infoway.sdk;

/**
 * WebSocket message code types.
 */
public enum WsCode {

    SUB_TRADE(10000),
    PUSH_TRADE(10001),
    UNSUB_TRADE(10002),
    SUB_DEPTH(10003),
    PUSH_DEPTH(10004),
    UNSUB_DEPTH(10005),
    SUB_KLINE(10006),
    PUSH_KLINE(10007),
    UNSUB_KLINE(10008),
    HEARTBEAT(10010);

    private final int code;

    WsCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /**
     * Look up a WsCode by its integer code.
     *
     * @param code integer code (10000-10010)
     * @return the matching WsCode, or null if not found
     */
    public static WsCode fromCode(int code) {
        for (WsCode c : values()) {
            if (c.code == code) {
                return c;
            }
        }
        return null;
    }
}
