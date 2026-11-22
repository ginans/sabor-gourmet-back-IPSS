package cl.ipss.saborgourmet.dto.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * Estructura JSON estandarizada de error para todos los endpoints REST sin exponer trazas internas.
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
