package cl.ipss.saborgourmet.service;

import cl.ipss.saborgourmet.config.ReservationProperties;
import cl.ipss.saborgourmet.dto.table.TableRequest;
import cl.ipss.saborgourmet.dto.table.TableResponse;
import cl.ipss.saborgourmet.exception.ErrorCode;
import cl.ipss.saborgourmet.exception.business.BusinessConflictException;
import cl.ipss.saborgourmet.exception.business.ResourceNotFoundException;
import cl.ipss.saborgourmet.model.Reservation;
import cl.ipss.saborgourmet.model.RestaurantTable;
import cl.ipss.saborgourmet.repository.ReservationRepository;
import cl.ipss.saborgourmet.repository.StorageTransactionManager;
import cl.ipss.saborgourmet.repository.TableRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Servicio de negocio para administrar la configuración de mesas del restaurante,
 * resguardando unicidad de número, ocupación mínima y reservas vigentes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TableService {

    private final TableRepository tableRepository;
    private final ReservationRepository reservationRepository;
    private final StorageTransactionManager transactionManager;
    private final ReservationProperties reservationProperties;
    private final Clock clock;

    /**
     * Lista todas las mesas configuradas o solo las habilitadas operativamente.
     */
    public List<TableResponse> findAll(boolean onlyActive) {
        return transactionManager.executeRead(() -> tableRepository.findAll().stream()
                .filter(table -> !onlyActive || table.isActive())
                .map(TableResponse::from)
                .toList());
    }

    /**
     * Consulta una mesa por su identificador único o lanza 404 si no existe.
     */
    public TableResponse findById(Long id) {
        return transactionManager.executeRead(() -> TableResponse.from(getTableOrThrow(id)));
    }

    /**
     * Crea una mesa validando que su número no esté duplicado.
     */
    public TableResponse create(TableRequest request) {
        return transactionManager.executeWrite(() -> {
            tableRepository.findByNumber(request.number())
                    .ifPresent(existing -> {
                        throw new BusinessConflictException(ErrorCode.TABLE_NUMBER_ALREADY_EXISTS, request.number());
                    });

            boolean active = request.active() == null || request.active();
            RestaurantTable table = RestaurantTable.builder()
                    .number(request.number())
                    .capacity(request.capacity())
                    .location(request.location().trim())
                    .active(active)
                    .build();

            RestaurantTable saved = tableRepository.save(table);
            log.info("Created table id={} number={} capacity={}", saved.getId(), saved.getNumber(), saved.getCapacity());
            return TableResponse.from(saved);
        });
    }

    /**
     * Actualiza número, capacidad, ubicación y estado operativo de una mesa,
     * impidiendo cambios que invaliden reservas vigentes (desactivación, sobrecupo o subocupación).
     */
    public TableResponse update(Long id, TableRequest request) {
        return transactionManager.executeWrite(() -> {
            RestaurantTable existing = getTableOrThrow(id);

            tableRepository.findByNumber(request.number())
                    .filter(other -> !Objects.equals(other.getId(), id))
                    .ifPresent(other -> {
                        throw new BusinessConflictException(ErrorCode.TABLE_NUMBER_ALREADY_EXISTS, request.number());
                    });

            boolean targetActive = request.active() != null ? request.active() : existing.isActive();
            List<Reservation> activeUpcoming = findActiveUpcomingReservations(id);

            if (!targetActive && !activeUpcoming.isEmpty()) {
                throw new BusinessConflictException(ErrorCode.TABLE_DEACTIVATION_CONFLICT);
            }

            if (request.capacity() < existing.getCapacity()) {
                boolean capacityConflict = activeUpcoming.stream()
                        .anyMatch(reservation -> reservation.getGuestCount() > request.capacity());
                if (capacityConflict) {
                    throw new BusinessConflictException(ErrorCode.TABLE_CAPACITY_REDUCTION_CONFLICT);
                }
            } else if (request.capacity() > existing.getCapacity()) {
                int newMinGuests = reservationProperties.calculateMinimumGuests(request.capacity());
                boolean minOccupancyConflict = activeUpcoming.stream()
                        .anyMatch(reservation -> reservation.getGuestCount() < newMinGuests);
                if (minOccupancyConflict) {
                    throw new BusinessConflictException(ErrorCode.TABLE_CAPACITY_MIN_OCCUPANCY_CONFLICT);
                }
            }

            existing.setNumber(request.number());
            existing.setCapacity(request.capacity());
            existing.setLocation(request.location().trim());
            existing.setActive(targetActive);

            RestaurantTable updated = tableRepository.save(existing);
            log.info("Updated table id={} number={} capacity={} active={}",
                    updated.getId(), updated.getNumber(), updated.getCapacity(), updated.isActive());
            return TableResponse.from(updated);
        });
    }

    /**
     * Elimina una mesa solo si no posee ninguna reserva asociada (vigente o histórica).
     */
    public void delete(Long id) {
        transactionManager.executeWrite(() -> {
            getTableOrThrow(id);
            List<Reservation> tableReservations = reservationRepository.findByTableId(id);
            if (!tableReservations.isEmpty()) {
                throw new BusinessConflictException(ErrorCode.TABLE_HAS_RESERVATIONS);
            }
            tableRepository.deleteById(id);
            log.info("Deleted table id={}", id);
        });
    }

    private RestaurantTable getTableOrThrow(Long id) {
        return tableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TABLE_NOT_FOUND, id));
    }

    private List<Reservation> findActiveUpcomingReservations(Long tableId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return reservationRepository.findByTableId(tableId).stream()
                .filter(reservation -> reservation.getStatus() != null && reservation.getStatus().isActive())
                .filter(reservation -> LocalDateTime.of(reservation.getDate(), reservation.getEndTime()).isAfter(now))
                .toList();
    }
}
