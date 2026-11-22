package cl.ipss.saborgourmet.dto.reservation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

public record ReservationRequest(
        @NotNull(message = "Client ID is required")
        Long clientId,

        @NotNull(message = "Table ID is required")
        Long tableId,

        @NotNull(message = "Reservation date is required")
        LocalDate date,

        @NotNull(message = "Start time is required")
        LocalTime startTime,

        @NotNull(message = "Guest count is required")
        @Min(value = 1, message = "Guest count must be at least 1")
        Integer guestCount,

        @Size(max = 250, message = "Notes cannot exceed 250 characters")
        String notes
) {
}
