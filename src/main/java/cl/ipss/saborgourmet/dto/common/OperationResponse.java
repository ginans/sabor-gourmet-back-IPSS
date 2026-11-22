package cl.ipss.saborgourmet.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Respuesta liviana de confirmación sin datos personales (PII), con soporte opcional de campos actualizados.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperationResponse(
        Long id,
        String message,
        List<String> updatedFields
) {
    public static OperationResponse of(Long id, String message) {
        return new OperationResponse(id, message, null);
    }

    public static OperationResponse ofUpdated(Long id, String message, List<String> updatedFields) {
        return new OperationResponse(id, message, updatedFields);
    }
}
