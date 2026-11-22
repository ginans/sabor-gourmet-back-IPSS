package cl.ipss.saborgourmet.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contenedor raíz serializado en {@code data/data.json} con colecciones planas
 * y secuencias persistentes de identificadores que evitan el reciclaje de IDs.
 */
@Getter
@NoArgsConstructor
public class RestaurantData {

    @Setter
    private long nextClientId = 1L;

    @Setter
    private long nextTableId = 1L;

    @Setter
    private long nextReservationId = 1L;

    private List<Client> clients = new ArrayList<>();
    private List<RestaurantTable> tables = new ArrayList<>();
    private List<Reservation> reservations = new ArrayList<>();

    public void setClients(List<Client> clients) {
        this.clients = clients != null ? new ArrayList<>(clients) : new ArrayList<>();
    }

    public void setTables(List<RestaurantTable> tables) {
        this.tables = tables != null ? new ArrayList<>(tables) : new ArrayList<>();
    }

    public void setReservations(List<Reservation> reservations) {
        this.reservations = reservations != null ? new ArrayList<>(reservations) : new ArrayList<>();
    }

    public long allocateClientId() {
        long maxExisting = clients.stream()
                .mapToLong(c -> c.getId() != null ? c.getId() : 0L)
                .max()
                .orElse(0L);
        long allocated = Math.max(nextClientId, maxExisting + 1L);
        nextClientId = allocated + 1L;
        return allocated;
    }

    public long allocateTableId() {
        long maxExisting = tables.stream()
                .mapToLong(t -> t.getId() != null ? t.getId() : 0L)
                .max()
                .orElse(0L);
        long allocated = Math.max(nextTableId, maxExisting + 1L);
        nextTableId = allocated + 1L;
        return allocated;
    }

    public long allocateReservationId() {
        long maxExisting = reservations.stream()
                .mapToLong(r -> r.getId() != null ? r.getId() : 0L)
                .max()
                .orElse(0L);
        long allocated = Math.max(nextReservationId, maxExisting + 1L);
        nextReservationId = allocated + 1L;
        return allocated;
    }
}
