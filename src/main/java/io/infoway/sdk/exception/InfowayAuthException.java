package io.infoway.sdk.exception;

/**
 * Raised on 401 Unauthorized responses.
 */
public class InfowayAuthException extends InfowayApiException {

    public InfowayAuthException() {
        this("Unauthorized");
    }

    public InfowayAuthException(String msg) {
        super(401, msg);
    }
}
