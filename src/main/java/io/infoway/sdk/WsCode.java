package io.infoway.sdk;

/**
 * WebSocket message code types.
 *
 * <p>Outbound (client → server):</p>
 * <ul>
 *   <li>{@link #SUB_TRADE} (10000) — subscribe trade</li>
 *   <li>{@link #SUB_DEPTH} (10003) — subscribe depth</li>
 *   <li>{@link #SUB_KLINE} (10006) — subscribe kline (requires data.arr=[{codes,type}])</li>
 *   <li>{@link #HEARTBEAT} (10010) — heartbeat keepalive</li>
 *   <li>{@link #UNSUB_TRADE} / {@link #UNSUB_DEPTH} / {@link #UNSUB_KLINE} (11000/11001/11002)</li>
 * </ul>
 *
 * <p>Inbound (server → client):</p>
 * <ul>
 *   <li>{@link #SUB_TRADE_ACK} (10001), {@link #SUB_DEPTH_ACK} (10004), {@link #SUB_KLINE_ACK} (10007) —
 *       one-shot subscription confirmations ({"msg":"ok"})</li>
 *   <li>{@link #PUSH_TRADE} (10002), {@link #PUSH_DEPTH} (10005), {@link #PUSH_KLINE} (10008) —
 *       real-time data pushes (high frequency)</li>
 *   <li>{@link #UNSUB_ACK} (11010) — unsubscribe confirmation</li>
 * </ul>
 */
public enum WsCode {

    // Outbound — subscribe
    SUB_TRADE(10000),
    SUB_DEPTH(10003),
    SUB_KLINE(10006),

    // Outbound — heartbeat
    HEARTBEAT(10010),

    // Outbound — unsubscribe
    UNSUB_TRADE(11000),
    UNSUB_DEPTH(11001),
    UNSUB_KLINE(11002),

    // Inbound — subscription acknowledgements
    SUB_TRADE_ACK(10001),
    SUB_DEPTH_ACK(10004),
    SUB_KLINE_ACK(10007),

    // Inbound — real-time data pushes
    PUSH_TRADE(10002),
    PUSH_DEPTH(10005),
    PUSH_KLINE(10008),

    // Inbound — unsubscribe ack
    UNSUB_ACK(11010);

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
     * @param code integer code
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
