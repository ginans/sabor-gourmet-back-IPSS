package cl.ipss.saborgourmet.repository.json;

import cl.ipss.saborgourmet.model.Reservation;
import cl.ipss.saborgourmet.repository.ReservationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JsonReservationRepository implements ReservationRepository {

    private final JsonFileStorage storage;

    @Override
    public List<Reservation> findAll() {
        return storage.readData(data -> data.getReservations().stream()
                .map(Reservation::copy)
                .toList());
    }

    @Override
    public Optional<Reservation> findById(Long id) {
        return storage.readData(data -> data.getReservations().stream()
                .filter(reservation -> Objects.equals(reservation.getId(), id))
                .findFirst()
                .map(Reservation::copy));
    }

    @Override
    public List<Reservation> findByClientId(Long clientId) {
        return storage.readData(data -> data.getReservations().stream()
                .filter(reservation -> Objects.equals(reservation.getClientId(), clientId))
                .map(Reservation::copy)
                .toList());
    }

    @Override
    public List<Reservation> findByTableId(Long tableId) {
        return storage.readData(data -> data.getReservations().stream()
                .filter(reservation -> Objects.equals(reservation.getTableId(), tableId))
                .map(Reservation::copy)
                .toList());
    }

    @Override
    public List<Reservation> findByTableIdAndDate(Long tableId, LocalDate date) {
        return storage.readData(data -> data.getReservations().stream()
                .filter(reservation -> Objects.equals(reservation.getTableId(), tableId))
                .filter(reservation -> Objects.equals(reservation.getDate(), date))
                .map(Reservation::copy)
                .toList());
    }

    @Override
    public Reservation save(Reservation reservation) {
        return storage.mutateData(data -> {
            List<Reservation> reservations = data.getReservations();
            Reservation toPersist = reservation.copy();
            if (toPersist.getId() == null) {
                long nextId = reservations.stream()
                        .mapToLong(existing -> existing.getId() != null ? existing.getId() : 0L)
                        .max()
                        .orElse(0L) + 1L;
                toPersist.setId(nextId);
                reservations.add(toPersist);
            } else {
                boolean replaced = false;
                for (int i = 0; i < reservations.size(); i++) {
                    if (Objects.equals(reservations.get(i).getId(), toPersist.getId())) {
                        reservations.set(i, toPersist);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    reservations.add(toPersist);
                }
            }
            return toPersist.copy();
        });
    }

    @Override
    public boolean deleteById(Long id) {
        return storage.mutateData(data ->
                data.getReservations().removeIf(reservation -> Objects.equals(reservation.getId(), id)));
    }
}
