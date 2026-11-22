package cl.ipss.saborgourmet.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Externalized business rule parameters for restaurant reservations.
 *
 * @param turnDurationMinutes     standard duration of each reservation slot in minutes
 * @param openingTime             daily opening time of the restaurant
 * @param closingTime             daily closing time (reservation end time must not exceed this value)
 * @param minOccupancyPercentage  minimum percentage of table capacity required (rounded down, minimum 1)
 * @param cancellationNoticeHours minimum advance notice in hours to cancel without a late penalty
 * @param maxLateCancellations    late-cancellation threshold that triggers an automatic client ban
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
     * Calculates the minimum number of guests allowed for a table capacity
     * applying the configured occupancy percentage rounded down, with a lower bound of 1.
     *
     * @param tableCapacity total number of seats of the table
     * @return minimum number of guests required to reserve the table
     */
    public int calculateMinimumGuests(int tableCapacity) {
        int minimum = (int) Math.floor(tableCapacity * (minOccupancyPercentage / 100.0));
        return Math.max(1, minimum);
    }
}
