package cl.ipss.saborgourmet.exception.business;

import cl.ipss.saborgourmet.exception.ErrorCode;

/**
 * Thrown when an operation conflicts with existing state or domain constraints
 * such as overlapping reservations, duplicate contacts, or referential integrity
 * (maps to HTTP 409 Conflict).
 */
public class BusinessConflictException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessConflictException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode.formatMessage(messageArgs));
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
