package cl.ipss.saborgourmet.exception.business;

import cl.ipss.saborgourmet.exception.ErrorCode;

/**
 * Thrown when a requested domain resource does not exist (maps to HTTP 404 Not Found).
 */
public class ResourceNotFoundException extends RuntimeException {

    private final ErrorCode errorCode;

    public ResourceNotFoundException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode.formatMessage(messageArgs));
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
