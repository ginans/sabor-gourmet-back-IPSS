package cl.ipss.saborgourmet.dto.reservation;

import cl.ipss.saborgourmet.model.RestaurantTable;

/**
 * Detalle de una mesa disponible incluyendo su mínimo de comensales requerido (regla del 75%).
 */
public record AvailableTableResponse(
        Long id,
        Integer number,
        Integer capacity,
        int minGuests,
        String location
) {
    public static AvailableTableResponse from(RestaurantTable table, int minGuests) {
        return new AvailableTableResponse(
                table.getId(),
                table.getNumber(),
                table.getCapacity(),
                minGuests,
                table.getLocation()
        );
    }
}
