package cl.ipss.saborgourmet.repository.json;

import cl.ipss.saborgourmet.model.Reservation;
import cl.ipss.saborgourmet.repository.ReservationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Implementación de {@link ReservationRepository} respaldada por {@link JsonFileStorage}.
 */
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
    public List<Reservation> findByDate(LocalDate date) {
        return storage.readData(data -> data.getReservations().stream()
                .filter(reservation -> Objects.equals(reservation.getDate(), date))
                .map(Reservation::copy)
                .toList());
    }

    @Override
    public Reservation save(Reservation reservation) {
        return storage.mutateData(data -> JsonFileStorage.upsert(
                data.getReservations(),
                reservation.copy(),
                Reservation::getId,
                Reservation::setId,
                data::allocateReservationId
        ).copy());
    }
}
