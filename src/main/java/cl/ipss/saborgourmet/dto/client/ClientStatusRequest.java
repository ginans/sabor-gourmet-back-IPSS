package cl.ipss.saborgourmet.dto.client;

import cl.ipss.saborgourmet.model.ClientStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Solicitud para actualizar manualmente el estado operativo de un cliente ({@code ACTIVE} o {@code BANNED}).
 */
public record ClientStatusRequest(
        @NotNull(message = "Status is required")
        ClientStatus status
) {
}
