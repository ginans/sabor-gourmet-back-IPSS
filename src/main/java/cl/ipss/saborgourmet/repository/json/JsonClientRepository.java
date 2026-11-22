package cl.ipss.saborgourmet.repository.json;

import cl.ipss.saborgourmet.model.Client;
import cl.ipss.saborgourmet.model.ClientStatus;
import cl.ipss.saborgourmet.repository.ClientRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JsonClientRepository implements ClientRepository {

    private final JsonFileStorage storage;

    @Override
    public List<Client> findAll() {
        return storage.readData(data -> data.getClients().stream()
                .map(Client::copy)
                .toList());
    }

    @Override
    public Optional<Client> findById(Long id) {
        return storage.readData(data -> data.getClients().stream()
                .filter(client -> Objects.equals(client.getId(), id))
                .findFirst()
                .map(Client::copy));
    }

    @Override
    public Optional<Client> findByEmailAndStatusNot(String normalizedEmail, ClientStatus excludedStatus) {
        return storage.readData(data -> data.getClients().stream()
                .filter(client -> client.getStatus() != excludedStatus)
                .filter(client -> normalizedEmail != null && normalizedEmail.equalsIgnoreCase(client.getEmail()))
                .findFirst()
                .map(Client::copy));
    }

    @Override
    public Client save(Client client) {
        return storage.mutateData(data -> {
            List<Client> clients = data.getClients();
            Client toPersist = client.copy();
            if (toPersist.getId() == null) {
                long nextId = clients.stream()
                        .mapToLong(existing -> existing.getId() != null ? existing.getId() : 0L)
                        .max()
                        .orElse(0L) + 1L;
                toPersist.setId(nextId);
                clients.add(toPersist);
            } else {
                boolean replaced = false;
                for (int i = 0; i < clients.size(); i++) {
                    if (Objects.equals(clients.get(i).getId(), toPersist.getId())) {
                        clients.set(i, toPersist);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    clients.add(toPersist);
                }
            }
            return toPersist.copy();
        });
    }

    @Override
    public boolean deleteById(Long id) {
        return storage.mutateData(data ->
                data.getClients().removeIf(client -> Objects.equals(client.getId(), id)));
    }
}
