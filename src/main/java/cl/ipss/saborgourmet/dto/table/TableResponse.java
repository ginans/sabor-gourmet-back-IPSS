package cl.ipss.saborgourmet.dto.table;

import cl.ipss.saborgourmet.model.RestaurantTable;

/**
 * Representación de lectura de una mesa del restaurante.
 */
public record TableResponse(
        Long id,
        Integer number,
        Integer capacity,
        String location,
        boolean active
) {
    public static TableResponse from(RestaurantTable table) {
        return new TableResponse(
                table.getId(),
                table.getNumber(),
                table.getCapacity(),
                table.getLocation(),
                table.isActive()
        );
    }
}
