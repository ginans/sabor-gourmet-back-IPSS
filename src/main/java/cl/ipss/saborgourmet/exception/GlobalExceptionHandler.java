package cl.ipss.saborgourmet.exception;

import cl.ipss.saborgourmet.dto.error.ApiErrorResponse;
import cl.ipss.saborgourmet.dto.error.ErrorCategory;
import cl.ipss.saborgourmet.exception.business.DomainException;
import cl.ipss.saborgourmet.exception.infrastructure.DataStorageException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Manejador global de excepciones REST que traduce errores de validación,
 * negocio e infraestructura a {@link ApiErrorResponse} sin exponer trazas internas.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Clock clock;
    private final ContentNegotiationManager contentNegotiationManager;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField)
                        .thenComparing(e -> e.getCode() != null ? e.getCode() : "")
                        .thenComparing(e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : ""))
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return buildResponse(
                ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.formatMessage(),
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String rawPath = violation.getPropertyPath().toString();
            String fieldName = rawPath.contains(".")
                    ? rawPath.substring(rawPath.lastIndexOf('.') + 1)
                    : rawPath;
            fieldErrors.putIfAbsent(fieldName, violation.getMessage());
        });
        return buildResponse(
                ErrorCode.CONSTRAINT_VIOLATION,
                ErrorCode.CONSTRAINT_VIOLATION.formatMessage(),
                request,
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
                request,
                null
        );
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingPathVariable(
            MissingPathVariableException ex,
            HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.INVALID_PARAMETER_TYPE;
        return buildResponse(
                errorCode,
                errorCode.formatMessage(ex.getVariableName()),
                request,
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
                request,
                null
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadablePayload(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        return buildResponse(errorCode, errorCode.formatMessage(), request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.UNSUPPORTED_MEDIA_TYPE;
        String contentType = ex.getContentType() != null ? ex.getContentType().toString() : "unknown";
        return buildResponse(errorCode, errorCode.formatMessage(contentType), request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException ex,
            HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.NOT_ACCEPTABLE;
        log.debug("Validation error [{}] at {}: {}", errorCode.getCode(), request.getRequestURI(), errorCode.formatMessage());
        return ResponseEntity.status(errorCode.getHttpStatus()).build();
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomainException(
            DomainException ex,
            HttpServletRequest request) {
        return buildResponse(ex.getErrorCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.ENDPOINT_NOT_FOUND;
        return buildResponse(errorCode, errorCode.formatMessage(), request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
        return buildResponse(
                errorCode,
                errorCode.formatMessage(ex.getMethod()),
                request,
                null
        );
    }

    @ExceptionHandler(DataStorageException.class)
    public ResponseEntity<ApiErrorResponse> handleDataStorage(
            DataStorageException ex,
            HttpServletRequest request) {
        log.error("Data storage infrastructure error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorCode errorCode = ex.getErrorCode();
        return buildResponse(errorCode, errorCode.formatMessage(), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request) {
        log.error("Unexpected internal error at {}", request.getRequestURI(), ex);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return buildResponse(errorCode, errorCode.formatMessage(), request, null);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            ErrorCode errorCode,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors) {
        String path = request.getRequestURI();
        if (errorCode.getCategory() == ErrorCategory.VALIDATION) {
            log.debug("Validation error [{}] at {}: {}", errorCode.getCode(), path, message);
        } else if (errorCode.getCategory() == ErrorCategory.BUSINESS) {
            log.warn("Business error [{}] at {}: {}", errorCode.getCode(), path, message);
        }
        if (!acceptsJson(request)) {
            return ResponseEntity.status(ErrorCode.NOT_ACCEPTABLE.getHttpStatus()).build();
        }
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(clock),
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

    private boolean acceptsJson(HttpServletRequest request) {
        try {
            List<MediaType> requested = contentNegotiationManager.resolveMediaTypes(new ServletWebRequest(request));
            return requested.stream().anyMatch(mediaType ->
                    mediaType.getQualityValue() > 0.0 && mediaType.isCompatibleWith(MediaType.APPLICATION_JSON));
        } catch (HttpMediaTypeNotAcceptableException ex) {
            return false;
        }
    }
}
