package cl.ipss.saborgourmet.dto.reservation;

import cl.ipss.saborgourmet.model.RestaurantTable;

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
