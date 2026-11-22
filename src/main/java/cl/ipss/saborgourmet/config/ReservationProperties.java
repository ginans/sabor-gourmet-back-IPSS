package cl.ipss.saborgourmet.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Parámetros de negocio externalizados para la gestión de turnos y reservas.
 *
 * @param turnDurationMinutes     duración estándar de cada turno en minutos
 * @param openingTime             hora diaria de apertura del restaurante
 * @param closingTime             hora diaria de cierre (el fin del turno no debe superarla)
 * @param minOccupancyPercentage  porcentaje mínimo de ocupación por mesa (redondeado hacia abajo, mínimo 1)
 * @param cancellationNoticeHours horas mínimas de anticipación para cancelar sin penalización
 * @param maxLateCancellations    umbral de cancelaciones tardías que activa el baneo automático
 */
@Validated
@ConfigurationProperties(prefix = "app.reservations")
public record ReservationProperties(
        @Min(value = 15, message = "Turn duration must be at least 15 minutes")
        int turnDurationMinutes,

        @NotNull(message = "Opening time is required")
        LocalTime openingTime,

        @NotNull(message = "Closing time is required")
        LocalTime closingTime,

        @Min(value = 1, message = "Minimum occupancy percentage must be at least 1")
        @Max(value = 100, message = "Minimum occupancy percentage must not exceed 100")
        int minOccupancyPercentage,

        @Min(value = 0, message = "Cancellation notice hours cannot be negative")
        int cancellationNoticeHours,

        @Min(value = 1, message = "Maximum late cancellations must be at least 1")
        int maxLateCancellations
) {

    /**
     * Calcula la cantidad mínima de personas permitida para una mesa aplicando el
     * porcentaje mínimo de ocupación redondeado hacia abajo, con cota inferior de 1.
     */
    public int calculateMinimumGuests(int tableCapacity) {
        int minimum = (int) Math.floor(tableCapacity * (minOccupancyPercentage / 100.0));
        return Math.max(1, minimum);
    }
}
