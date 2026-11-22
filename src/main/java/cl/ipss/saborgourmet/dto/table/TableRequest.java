package cl.ipss.saborgourmet.dto.table;

import cl.ipss.saborgourmet.config.ApiConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos de entrada validados para crear o reconfigurar una mesa del salón.
 */
public record TableRequest(
        @NotNull(message = "Table number is required")
        @Min(value = 1, message = "Table number must be at least 1")
        Integer number,

        @NotNull(message = "Capacity is required")
        @Min(value = 1, message = "Capacity must be at least 1")
        @Max(value = 50, message = "Capacity must not exceed 50")
        Integer capacity,

        @NotBlank(message = "Location is required")
        @Size(min = 2, max = 80, message = "Location must be between 2 and 80 characters")
        @Pattern(regexp = ApiConstants.MIN_TWO_NON_WHITESPACE_REGEX, message = "Location must contain at least 2 non-whitespace characters")
        String location,

        Boolean active
) {
}
