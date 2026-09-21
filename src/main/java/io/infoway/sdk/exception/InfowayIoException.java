package io.infoway.sdk.exception;

import java.io.IOException;

/**
 * Raised when a REST call fails at the transport layer after retries are exhausted.
 *
 * <p>Distinct from {@link InfowayTimeoutException} (read/connect timeout) and
 * {@link InfowayApiException} (HTTP / business {@code ret}).</p>
 */
public class InfowayIoException extends RuntimeException {

    public InfowayIoException(String message, Throwable cause) {
        super(message, cause);
    }

    public InfowayIoException(IOException cause) {
        super(cause != null ? cause.getMessage() : "I/O error", cause);
    }
}
