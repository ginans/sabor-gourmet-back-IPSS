package cl.ipss.saborgourmet.repository.json;

import cl.ipss.saborgourmet.model.RestaurantTable;
import cl.ipss.saborgourmet.repository.TableRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Implementación de {@link TableRepository} respaldada por {@link JsonFileStorage}.
 */
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
        return storage.mutateData(data -> JsonFileStorage.upsert(
                data.getTables(),
                table.copy(),
                RestaurantTable::getId,
                RestaurantTable::setId,
                data::allocateTableId
        ).copy());
    }

    @Override
    public boolean deleteById(Long id) {
        return storage.mutateData(data ->
                data.getTables().removeIf(table -> Objects.equals(table.getId(), id)));
    }
}
