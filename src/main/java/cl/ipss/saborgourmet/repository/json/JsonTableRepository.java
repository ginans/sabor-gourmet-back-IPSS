package cl.ipss.saborgourmet.repository.json;

import cl.ipss.saborgourmet.model.RestaurantTable;
import cl.ipss.saborgourmet.repository.TableRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JsonTableRepository implements TableRepository {

    private final JsonFileStorage storage;

    @Override
    public List<RestaurantTable> findAll() {
        return storage.readData(data -> data.getTables().stream()
                .map(RestaurantTable::copy)
                .toList());
    }

    @Override
    public Optional<RestaurantTable> findById(Long id) {
        return storage.readData(data -> data.getTables().stream()
                .filter(table -> Objects.equals(table.getId(), id))
                .findFirst()
                .map(RestaurantTable::copy));
    }

    @Override
    public Optional<RestaurantTable> findByNumber(Integer number) {
        return storage.readData(data -> data.getTables().stream()
                .filter(table -> Objects.equals(table.getNumber(), number))
                .findFirst()
                .map(RestaurantTable::copy));
    }

    @Override
    public RestaurantTable save(RestaurantTable table) {
        return storage.mutateData(data -> {
            List<RestaurantTable> tables = data.getTables();
            RestaurantTable toPersist = table.copy();
            if (toPersist.getId() == null) {
                long nextId = tables.stream()
                        .mapToLong(existing -> existing.getId() != null ? existing.getId() : 0L)
                        .max()
                        .orElse(0L) + 1L;
                toPersist.setId(nextId);
                tables.add(toPersist);
            } else {
                boolean replaced = false;
                for (int i = 0; i < tables.size(); i++) {
                    if (Objects.equals(tables.get(i).getId(), toPersist.getId())) {
                        tables.set(i, toPersist);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    tables.add(toPersist);
                }
            }
            return toPersist.copy();
        });
    }

    @Override
    public boolean deleteById(Long id) {
        return storage.mutateData(data ->
                data.getTables().removeIf(table -> Objects.equals(table.getId(), id)));
    }
}
