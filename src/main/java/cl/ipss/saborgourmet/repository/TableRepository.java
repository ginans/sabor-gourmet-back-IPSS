package cl.ipss.saborgourmet.repository;

import cl.ipss.saborgourmet.model.RestaurantTable;
import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link RestaurantTable} entities, decoupled from the underlying storage engine.
 */
public interface TableRepository {

    List<RestaurantTable> findAll();

    Optional<RestaurantTable> findById(Long id);

    Optional<RestaurantTable> findByNumber(Integer number);

    RestaurantTable save(RestaurantTable table);

    boolean deleteById(Long id);
}
