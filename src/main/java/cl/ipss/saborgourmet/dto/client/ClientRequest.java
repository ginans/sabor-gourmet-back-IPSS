package cl.ipss.saborgourmet.dto.client;

import cl.ipss.saborgourmet.config.ApiConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos de entrada validados para registrar o actualizar un cliente.
 */
public record ClientRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        @Pattern(regexp = ApiConstants.MIN_TWO_NON_WHITESPACE_REGEX, message = "Name must contain at least 2 non-whitespace characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must have a valid format")
        @Size(max = 120, message = "Email must not exceed 120 characters")
        String email,

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = ApiConstants.PHONE_REGEX, message = "Phone must contain 7 to 15 digits (optional '+', spaces or dashes)")
        String phone
) {
}
