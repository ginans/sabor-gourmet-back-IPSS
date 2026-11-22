package cl.ipss.saborgourmet.exception.infrastructure;

import cl.ipss.saborgourmet.exception.ErrorCode;
import lombok.Getter;

/**
 * Excepción de infraestructura ante fallos de lectura o escritura del archivo JSON (HTTP 500).
 */
@Getter
public class DataStorageException extends RuntimeException {

    private final ErrorCode errorCode;

    public DataStorageException(Throwable cause) {
        super(ErrorCode.DATA_STORAGE_ERROR.getMessageTemplate(), cause);
        this.errorCode = ErrorCode.DATA_STORAGE_ERROR;
    }

    public DataStorageException(String technicalDetail, Throwable cause) {
        super(technicalDetail, cause);
        this.errorCode = ErrorCode.DATA_STORAGE_ERROR;
    }
}
