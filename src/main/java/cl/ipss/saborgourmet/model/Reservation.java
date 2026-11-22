package cl.ipss.saborgourmet.model;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representa una reserva de mesa vinculada por {@code clientId} y {@code tableId} sin redundancia en JSON.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Reservation {

    private Long id;
    private Long clientId;
    private Long tableId;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer guestCount;
    private ReservationStatus status;
    private String notes;

    public Reservation copy() {
        return toBuilder().build();
    }
}
