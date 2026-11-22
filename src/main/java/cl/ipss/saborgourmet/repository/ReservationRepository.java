package cl.ipss.saborgourmet.repository;

import cl.ipss.saborgourmet.model.Reservation;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Contrato de persistencia para reservas de mesas.
 * Las reservas siguen un ciclo de estados y se cancelan sin eliminarse físicamente.
 */
public interface ReservationRepository {

    List<Reservation> findAll();

    Optional<Reservation> findById(Long id);

    List<Reservation> findByClientId(Long clientId);

    List<Reservation> findByTableId(Long tableId);

    List<Reservation> findByTableIdAndDate(Long tableId, LocalDate date);

    List<Reservation> findByDate(LocalDate date);

    Reservation save(Reservation reservation);
}
