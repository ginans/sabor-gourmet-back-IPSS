package cl.ipss.saborgourmet.model;

public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    CANCELLED;

    public boolean isActive() {
        return this == PENDING || this == CONFIRMED;
    }
}
