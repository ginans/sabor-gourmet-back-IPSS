package cl.ipss.saborgourmet.exception.infrastructure;

import cl.ipss.saborgourmet.exception.ErrorCode;

/**
 * Thrown when reading from or writing to the JSON persistence file fails
 * (maps to HTTP 500 Internal Server Error without leaking internal stack traces).
 */
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

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
