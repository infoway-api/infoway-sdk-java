package io.infoway.sdk.exception;

/**
 * Raised when a request times out.
 */
public class InfowayTimeoutException extends RuntimeException {

    public InfowayTimeoutException(String message) {
        super(message);
    }

    public InfowayTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
