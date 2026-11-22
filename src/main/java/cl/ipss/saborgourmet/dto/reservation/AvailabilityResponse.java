package cl.ipss.saborgourmet.dto.reservation;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AvailabilityResponse(
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        int guestCount,
        int availableCount,
        List<AvailableTableResponse> availableTables
) {
}
