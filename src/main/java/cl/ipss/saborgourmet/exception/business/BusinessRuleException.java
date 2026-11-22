package cl.ipss.saborgourmet.exception.business;

import cl.ipss.saborgourmet.exception.ErrorCode;

/**
 * Thrown when request data violates a domain rule such as past reservation dates
 * or times outside business hours (maps to HTTP 400 Bad Request).
 */
public class BusinessRuleException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessRuleException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode.formatMessage(messageArgs));
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
