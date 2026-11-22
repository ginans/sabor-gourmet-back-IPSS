package cl.ipss.saborgourmet.exception.business;

import cl.ipss.saborgourmet.exception.ErrorCode;

/**
 * Indica un conflicto con el estado actual o restricciones de negocio (HTTP 409 Conflict).
 */
public class BusinessConflictException extends DomainException {

    public BusinessConflictException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, messageArgs);
    }
}
