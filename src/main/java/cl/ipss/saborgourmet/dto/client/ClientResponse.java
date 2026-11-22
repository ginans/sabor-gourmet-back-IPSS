package cl.ipss.saborgourmet.dto.client;

import cl.ipss.saborgourmet.model.Client;
import cl.ipss.saborgourmet.model.ClientStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Representación de lectura de un cliente. Omite campos nulos en registros anonimizados ({@code DELETED}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientResponse(
        Long id,
        String name,
        String email,
        String phone,
        ClientStatus status,
        int lateCancellations
) {
    public static ClientResponse from(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getEmail(),
                client.getPhone(),
                client.getStatus(),
                client.getLateCancellations()
        );
    }
}
