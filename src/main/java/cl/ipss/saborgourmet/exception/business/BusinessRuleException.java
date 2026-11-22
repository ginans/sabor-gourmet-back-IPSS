package cl.ipss.saborgourmet.exception.business;

import cl.ipss.saborgourmet.exception.ErrorCode;

/**
 * Indica que los datos enviados incumplen una regla de dominio (HTTP 400 Bad Request).
 */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, messageArgs);
    }
}
