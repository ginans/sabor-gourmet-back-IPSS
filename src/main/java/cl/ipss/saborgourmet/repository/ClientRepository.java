package cl.ipss.saborgourmet.repository;

import cl.ipss.saborgourmet.model.Client;
import cl.ipss.saborgourmet.model.ClientStatus;
import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link Client} entities, decoupled from the underlying storage engine.
 */
public interface ClientRepository {

    List<Client> findAll();

    Optional<Client> findById(Long id);

    Optional<Client> findByEmailAndStatusNot(String normalizedEmail, ClientStatus excludedStatus);

    Client save(Client client);

    boolean deleteById(Long id);
}
