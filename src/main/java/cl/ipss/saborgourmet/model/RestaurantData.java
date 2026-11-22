package cl.ipss.saborgourmet.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RestaurantData {

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
}
