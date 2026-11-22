package cl.ipss.saborgourmet.repository;

import cl.ipss.saborgourmet.model.Reservation;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link Reservation} entities, decoupled from the underlying storage engine.
 */
public interface ReservationRepository {

    List<Reservation> findAll();

    Optional<Reservation> findById(Long id);

    List<Reservation> findByClientId(Long clientId);

    List<Reservation> findByTableId(Long tableId);

    List<Reservation> findByTableIdAndDate(Long tableId, LocalDate date);

    Reservation save(Reservation reservation);

    boolean deleteById(Long id);
}
