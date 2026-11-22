package cl.ipss.saborgourmet.dto.reservation;

import cl.ipss.saborgourmet.model.Reservation;
import cl.ipss.saborgourmet.model.ReservationStatus;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Representación de lectura de una reserva vinculada por {@code clientId} y {@code tableId}.
 */
public record ReservationResponse(
        Long id,
        Long clientId,
        Long tableId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        Integer guestCount,
        ReservationStatus status,
        String notes
) {
    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
                r.getId(),
                r.getClientId(),
                r.getTableId(),
                r.getDate(),
                r.getStartTime(),
                r.getEndTime(),
                r.getGuestCount(),
                r.getStatus(),
                r.getNotes()
        );
    }
}
