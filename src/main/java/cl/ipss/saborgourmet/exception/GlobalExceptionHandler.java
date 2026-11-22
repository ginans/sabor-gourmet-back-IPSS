package cl.ipss.saborgourmet.exception;

import cl.ipss.saborgourmet.dto.error.ApiErrorResponse;
import cl.ipss.saborgourmet.dto.error.ErrorCategory;
import cl.ipss.saborgourmet.exception.business.BusinessConflictException;
import cl.ipss.saborgourmet.exception.business.BusinessRuleException;
import cl.ipss.saborgourmet.exception.business.ResourceNotFoundException;
import cl.ipss.saborgourmet.exception.infrastructure.DataStorageException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Centralized REST exception handler that maps validation, business, and
 * infrastructure errors into consistent {@link ApiErrorResponse} JSON payloads
 * using {@link ErrorCode}, without exposing internal stack traces.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return buildResponse(
                ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.formatMessage(),
                request.getRequestURI(),
                fieldErrors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                fieldErrors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return buildResponse(
                ErrorCode.CONSTRAINT_VIOLATION,
                ErrorCode.CONSTRAINT_VIOLATION.formatMessage(),
                request.getRequestURI(),
                fieldErrors
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.MISSING_PARAMETER;
        return buildResponse(
                errorCode,
                errorCode.formatMessage(ex.getParameterName()),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.INVALID_PARAMETER_TYPE;
        return buildResponse(
                errorCode,
                errorCode.formatMessage(ex.getName()),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadablePayload(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        return buildResponse(errorCode, errorCode.formatMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessRule(
            BusinessRuleException ex,
            HttpServletRequest request) {
        return buildResponse(ex.getErrorCode(), ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {
        return buildResponse(ex.getErrorCode(), ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(BusinessConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessConflict(
            BusinessConflictException ex,
            HttpServletRequest request) {
        return buildResponse(ex.getErrorCode(), ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.ENDPOINT_NOT_FOUND;
        return buildResponse(errorCode, errorCode.formatMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
        return buildResponse(
                errorCode,
                errorCode.formatMessage(ex.getMethod()),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(DataStorageException.class)
    public ResponseEntity<ApiErrorResponse> handleDataStorage(
            DataStorageException ex,
            HttpServletRequest request) {
        log.error("Data storage infrastructure error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorCode errorCode = ex.getErrorCode();
        return buildResponse(errorCode, errorCode.formatMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request) {
        log.error("Unexpected internal error at {}", request.getRequestURI(), ex);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return buildResponse(errorCode, errorCode.formatMessage(), request.getRequestURI(), null);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            ErrorCode errorCode,
            String message,
            String path,
            Map<String, String> fieldErrors) {
        if (errorCode.getCategory() == ErrorCategory.VALIDATION) {
            log.debug("Validation error [{}] at {}: {}", errorCode.getCode(), path, message);
        } else if (errorCode.getCategory() == ErrorCategory.BUSINESS) {
            log.warn("Business error [{}] at {}: {}", errorCode.getCode(), path, message);
        }
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                errorCode.getHttpStatus().value(),
                errorCode.getHttpStatus().getReasonPhrase(),
                errorCode.getCategory(),
                errorCode.getCode(),
                message,
                path,
                fieldErrors
        );
        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }
}
