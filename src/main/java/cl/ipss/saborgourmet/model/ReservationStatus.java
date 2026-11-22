package cl.ipss.saborgourmet.model;

/**
 * Ciclo de vida de una reserva: {@code PENDING} y {@code CONFIRMED} bloquean la mesa; {@code CANCELLED} la libera.
 */
public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    CANCELLED;

    public boolean isActive() {
        return this == PENDING || this == CONFIRMED;
    }
}
