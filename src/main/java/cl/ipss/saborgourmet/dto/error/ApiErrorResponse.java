package cl.ipss.saborgourmet.dto.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * Standardized JSON error payload returned across all REST endpoints.
 * Ensures internal stack traces are never exposed to API consumers.
 *
 * @param timestamp   UTC instant when the error occurred
 * @param status      HTTP status code
 * @param error       HTTP status reason phrase
 * @param category    origin category ({@link ErrorCategory})
 * @param code        machine-readable error code
 * @param message     human-readable error description
 * @param path        request URI that triggered the error
 * @param fieldErrors optional map of invalid field names and validation messages
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        ErrorCategory category,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
}
