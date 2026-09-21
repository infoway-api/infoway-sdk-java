package io.infoway.sdk.exception;

import io.infoway.sdk.RestErrorCode;
import io.infoway.sdk.WsErrorCode;

/**
 * Raised when the Infoway API returns a non-success response.
 *
 * <p>{@link #getRet()} is the wire code ({@code ret} on REST, {@code code} on
 * WebSocket). {@link #getErrorName()} is the matching enum constant when the
 * SDK knows it — {@link RestErrorCode} for HTTP, {@link WsErrorCode} for
 * sockets. 508–514 share numbers across the two channels but not meanings.</p>
 */
public class InfowayApiException extends RuntimeException {

    private final int ret;
    private final String msg;
    private final String traceId;
    private final String errorName;

    public InfowayApiException(int ret, String msg, String traceId) {
        this(ret, msg, traceId, null);
    }

    public InfowayApiException(int ret, String msg, String traceId, String errorName) {
        super(formatMessage(ret, msg, traceId, errorName));
        this.ret = ret;
        this.msg = msg;
        this.traceId = traceId;
        this.errorName = errorName;
    }

    public InfowayApiException(int ret, String msg) {
        this(ret, msg, null, null);
    }

    /** HTTP / REST body {@code ret} (or {@code code}) classified with {@link RestErrorCode}. */
    public static InfowayApiException ofRest(int ret, String msg, String traceId) {
        RestErrorCode known = RestErrorCode.fromCode(ret);
        return new InfowayApiException(ret, display(msg, known != null ? known.label() : null),
                traceId, known != null ? known.name() : null);
    }

    /** WebSocket error frame {@code code} classified with {@link WsErrorCode}. */
    public static InfowayApiException ofWs(int ret, String msg, String traceId) {
        WsErrorCode known = WsErrorCode.fromCode(ret);
        return new InfowayApiException(ret, display(msg, known != null ? known.label() : null),
                traceId, known != null ? known.name() : null);
    }

    /**
     * Transport / gateway status with no business {@code ret} in the body.
     * Must not look up {@link RestErrorCode} — HTTP 502 is not daily-quota 502.
     */
    public static InfowayApiException ofHttpStatus(int status, String msg, String traceId) {
        return new InfowayApiException(status, msg, traceId, null);
    }

    public int getRet() {
        return ret;
    }

    public String getMsg() {
        return msg;
    }

    public String getTraceId() {
        return traceId;
    }

    /**
     * Enum constant name when known, e.g. {@code PRODUCT_NOT_EXISTS} (REST) or
     * {@code APIKEY_EXPIRED} (WebSocket). {@code null} for unclassified codes.
     */
    public String getErrorName() {
        return errorName;
    }

    private static String display(String msg, String fallback) {
        if (msg != null && !msg.isBlank()) {
            return msg;
        }
        return fallback != null ? fallback : "API error";
    }

    private static String formatMessage(int ret, String msg, String traceId, String errorName) {
        String s = errorName != null
                ? "[" + ret + " " + errorName + "] " + msg
                : "[" + ret + "] " + msg;
        if (traceId != null && !traceId.isEmpty()) {
            s += " (trace: " + traceId + ")";
        }
        return s;
    }
}
