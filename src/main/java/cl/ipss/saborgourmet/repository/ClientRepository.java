package cl.ipss.saborgourmet.repository;

import cl.ipss.saborgourmet.model.Client;
import cl.ipss.saborgourmet.model.ClientStatus;
import java.util.List;
import java.util.Optional;

/**
 * Contrato de persistencia para clientes, desacoplado del motor de almacenamiento subyacente.
 */
public interface ClientRepository {

    List<Client> findAll();

    Optional<Client> findById(Long id);

    Optional<Client> findByEmailAndStatusNot(String normalizedEmail, ClientStatus excludedStatus);

    Client save(Client client);

    boolean deleteById(Long id);
}
