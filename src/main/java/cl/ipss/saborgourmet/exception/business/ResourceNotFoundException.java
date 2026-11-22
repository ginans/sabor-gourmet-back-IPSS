package cl.ipss.saborgourmet.exception.business;

import cl.ipss.saborgourmet.exception.ErrorCode;

/**
 * Indica que el recurso de dominio solicitado no existe (HTTP 404 Not Found).
 */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, messageArgs);
    }
}
