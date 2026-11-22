package cl.ipss.saborgourmet.dto.reservation;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Resultado de la consulta de disponibilidad para una fecha, turno y cantidad de comensales.
 */
public record AvailabilityResponse(
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        int guestCount,
        int availableCount,
        List<AvailableTableResponse> availableTables
) {
}
