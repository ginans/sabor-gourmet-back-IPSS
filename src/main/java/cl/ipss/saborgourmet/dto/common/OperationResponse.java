package cl.ipss.saborgourmet.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

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
