package cl.ipss.saborgourmet.exception.business;

import cl.ipss.saborgourmet.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Indica que el recurso de dominio solicitado no existe (HTTP 404 Not Found).
 */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, HttpStatus.NOT_FOUND, messageArgs);
    }
}
