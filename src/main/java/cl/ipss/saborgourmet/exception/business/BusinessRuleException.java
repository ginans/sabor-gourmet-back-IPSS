package cl.ipss.saborgourmet.exception.business;

import cl.ipss.saborgourmet.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Indica que los datos enviados incumplen una regla de dominio (HTTP 400 Bad Request).
 */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, HttpStatus.BAD_REQUEST, messageArgs);
    }
}
