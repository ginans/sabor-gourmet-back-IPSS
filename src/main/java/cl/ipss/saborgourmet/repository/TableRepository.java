package cl.ipss.saborgourmet.repository;

import cl.ipss.saborgourmet.model.RestaurantTable;
import java.util.List;
import java.util.Optional;

/**
 * Contrato de persistencia para mesas, desacoplado del motor de almacenamiento subyacente.
 */
public interface TableRepository {

    List<RestaurantTable> findAll();

    Optional<RestaurantTable> findById(Long id);

    Optional<RestaurantTable> findByNumber(Integer number);

    RestaurantTable save(RestaurantTable table);

    boolean deleteById(Long id);
}
