package cl.ipss.saborgourmet.dto.client;

import cl.ipss.saborgourmet.model.ClientStatus;
import jakarta.validation.constraints.NotNull;

public record ClientStatusRequest(
        @NotNull(message = "Status is required")
        ClientStatus status
) {
}
