package io.infoway.sdk.exception;

import io.infoway.sdk.RestErrorCode;
import io.infoway.sdk.WsErrorCode;

/**
 * Raised when the API gateway rejects a request for exceeding a rate limit.
 *
 * <p>Shapes that map here:</p>
 * <ul>
 *   <li>HTTP 429 (with any body)</li>
 *   <li><b>HTTP 200</b> with the body {@code {"detail":"Rate limit exceeded"}}</li>
 *   <li>REST {@code ret} 501 / 502 ({@link RestErrorCode#REQUEST_EXCEED_LIMIT} /
 *       {@link RestErrorCode#REQUEST_FOR_DAY_LIMIT})</li>
 *   <li>WebSocket {@code code} 501 / 502 ({@link WsErrorCode#REQUEST_FREQUENCY_MIN_EXCEED} /
 *       {@link WsErrorCode#REQUEST_FREQUENCY_DAY_EXCEED})</li>
 * </ul>
 *
 * <p>Extends {@link InfowayApiException} so existing {@code catch (InfowayApiException)}
 * blocks keep working. The HTTP client retries this exception with exponential backoff
 * before giving up.</p>
 */
public class InfowayRateLimitException extends InfowayApiException {

    public InfowayRateLimitException(String msg) {
        this(429, msg, null, null);
    }

    public InfowayRateLimitException(int ret, String msg, String traceId) {
        this(ret, msg, traceId, null);
    }

    public InfowayRateLimitException(int ret, String msg, String traceId, String errorName) {
        super(ret, msg, traceId, errorName);
    }

    public static InfowayRateLimitException rest(int ret, String msg, String traceId) {
        RestErrorCode known = RestErrorCode.fromCode(ret);
        return new InfowayRateLimitException(ret, msg, traceId, known != null ? known.name() : "RATE_LIMIT");
    }

    public static InfowayRateLimitException ws(int ret, String msg, String traceId) {
        WsErrorCode known = WsErrorCode.fromCode(ret);
        return new InfowayRateLimitException(ret, msg, traceId, known != null ? known.name() : "RATE_LIMIT");
    }
}
