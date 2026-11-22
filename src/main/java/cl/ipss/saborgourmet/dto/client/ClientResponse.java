package cl.ipss.saborgourmet.dto.client;

import cl.ipss.saborgourmet.model.Client;
import cl.ipss.saborgourmet.model.ClientStatus;

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
