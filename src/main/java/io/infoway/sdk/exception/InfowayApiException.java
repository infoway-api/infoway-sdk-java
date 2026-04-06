package io.infoway.sdk.exception;

/**
 * Raised when the Infoway API returns a non-success response.
 */
public class InfowayApiException extends RuntimeException {

    private final int ret;
    private final String msg;
    private final String traceId;

    public InfowayApiException(int ret, String msg, String traceId) {
        super(formatMessage(ret, msg, traceId));
        this.ret = ret;
        this.msg = msg;
        this.traceId = traceId;
    }

    public InfowayApiException(int ret, String msg) {
        this(ret, msg, null);
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

    private static String formatMessage(int ret, String msg, String traceId) {
        String s = "[" + ret + "] " + msg;
        if (traceId != null && !traceId.isEmpty()) {
            s += " (trace: " + traceId + ")";
        }
        return s;
    }
}
