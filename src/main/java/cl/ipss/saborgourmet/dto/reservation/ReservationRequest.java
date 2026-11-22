package cl.ipss.saborgourmet.dto.reservation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Datos de entrada validados para crear o modificar una reserva de mesa.
 */
public record ReservationRequest(
        @NotNull(message = "Client ID is required")
        @Positive(message = "Client ID must be positive")
        Long clientId,

        @NotNull(message = "Table ID is required")
        @Positive(message = "Table ID must be positive")
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
