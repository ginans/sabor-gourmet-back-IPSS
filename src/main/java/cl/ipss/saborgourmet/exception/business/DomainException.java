package cl.ipss.saborgourmet.exception.business;

import cl.ipss.saborgourmet.exception.ErrorCode;
import lombok.Getter;

/**
 * Excepción base para violaciones de reglas de negocio asociadas a un {@link ErrorCode}.
 */
@Getter
public abstract class DomainException extends RuntimeException {

    private final ErrorCode errorCode;

    protected DomainException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode.formatMessage(messageArgs));
        this.errorCode = errorCode;
    }
}
