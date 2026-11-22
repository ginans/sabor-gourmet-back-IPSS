package cl.ipss.saborgourmet.exception.business;

import cl.ipss.saborgourmet.exception.ErrorCode;
import java.util.Objects;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Excepción base para violaciones de reglas de negocio asociadas a un {@link ErrorCode}.
 */
@Getter
public abstract class DomainException extends RuntimeException {

    private final ErrorCode errorCode;

    protected DomainException(ErrorCode errorCode, HttpStatus expectedStatus, Object... messageArgs) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null").formatMessage(messageArgs));
        if (errorCode.getHttpStatus() != expectedStatus) {
            throw new IllegalArgumentException(
                    "ErrorCode " + errorCode.name() + " has HTTP status " + errorCode.getHttpStatus()
                            + " but expected " + expectedStatus);
        }
        this.errorCode = errorCode;
    }
}
