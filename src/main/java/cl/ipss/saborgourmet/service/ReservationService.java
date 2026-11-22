package cl.ipss.saborgourmet.service;

import cl.ipss.saborgourmet.config.ReservationProperties;
import cl.ipss.saborgourmet.dto.reservation.AvailabilityResponse;
import cl.ipss.saborgourmet.dto.reservation.AvailableTableResponse;
import cl.ipss.saborgourmet.dto.reservation.ReservationRequest;
import cl.ipss.saborgourmet.dto.reservation.ReservationResponse;
import cl.ipss.saborgourmet.exception.ErrorCode;
import cl.ipss.saborgourmet.exception.business.BusinessConflictException;
import cl.ipss.saborgourmet.exception.business.BusinessRuleException;
import cl.ipss.saborgourmet.exception.business.ResourceNotFoundException;
import cl.ipss.saborgourmet.model.Client;
import cl.ipss.saborgourmet.model.ClientStatus;
import cl.ipss.saborgourmet.model.Reservation;
import cl.ipss.saborgourmet.model.ReservationStatus;
import cl.ipss.saborgourmet.model.RestaurantTable;
import cl.ipss.saborgourmet.repository.ClientRepository;
import cl.ipss.saborgourmet.repository.ReservationRepository;
import cl.ipss.saborgourmet.repository.StorageTransactionManager;
import cl.ipss.saborgourmet.repository.TableRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Servicio de negocio para disponibilidad y ciclo de vida de reservas:
 * turnos, ocupación mínima del 75%, solapamiento, confirmación y cancelación tardía.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ClientRepository clientRepository;
    private final TableRepository tableRepository;
    private final StorageTransactionManager transactionManager;
    private final ReservationProperties properties;
    private final Clock clock;

    public List<ReservationResponse> findAll(
            LocalDate date,
            ReservationStatus status,
            Long clientId,
            Long tableId) {
        return transactionManager.executeRead(() -> reservationRepository.findAll().stream()
                .filter(item -> date == null || Objects.equals(item.getDate(), date))
                .filter(item -> status == null || item.getStatus() == status)
                .filter(item -> clientId == null || Objects.equals(item.getClientId(), clientId))
                .filter(item -> tableId == null || Objects.equals(item.getTableId(), tableId))
                .map(ReservationResponse::from)
                .toList());
    }

    public ReservationResponse findById(Long id) {
        return transactionManager.executeRead(() -> ReservationResponse.from(loadReservation(id)));
    }

    /**
     * Consulta mesas disponibles leyendo las reservas de la fecha una sola vez (sin N+1).
     */
    public AvailabilityResponse checkAvailability(LocalDate date, LocalTime startTime, int guestCount) {
        LocalTime endTime = resolveAndValidateEndTime(date, startTime);

        return transactionManager.executeRead(() -> {
            Map<Long, List<Reservation>> activeByTable = reservationRepository.findByDate(date).stream()
                    .filter(existing -> existing.getStatus() != null && existing.getStatus().isActive())
                    .collect(Collectors.groupingBy(Reservation::getTableId));

            List<AvailableTableResponse> matchingTables = tableRepository.findAll().stream()
                    .filter(RestaurantTable::isActive)
                    .filter(table -> guestCount <= table.getCapacity())
                    .filter(table -> guestCount >= properties.calculateMinimumGuests(table.getCapacity()))
                    .filter(table -> !overlapsAny(
                            activeByTable.getOrDefault(table.getId(), List.of()),
                            startTime,
                            endTime,
                            null))
                    .sorted(Comparator.comparingInt(RestaurantTable::getCapacity)
                            .thenComparingInt(RestaurantTable::getNumber))
                    .map(table -> AvailableTableResponse.from(
                            table, properties.calculateMinimumGuests(table.getCapacity())))
                    .toList();

            return new AvailabilityResponse(
                    date, startTime, endTime, guestCount, matchingTables.size(), matchingTables);
        });
    }

    /**
     * Runs eligibility checks, slot overlap validation, and persistence inside the write lock
     * so concurrent requests for the same table and slot resolve without double booking.
     */
    public ReservationResponse create(ReservationRequest request) {
        LocalTime endTime = resolveAndValidateEndTime(request.date(), request.startTime());

        return transactionManager.executeWrite(() -> {
            assertClientCanBook(request.clientId());
            RestaurantTable table = assertTableFitsGuests(request.tableId(), request.guestCount());

            if (overlapsExistingActive(table.getId(), request.date(), request.startTime(), endTime, null)) {
                throw new BusinessConflictException(ErrorCode.RESERVATION_TIME_OVERLAP);
            }

            Reservation entity = Reservation.builder()
                    .clientId(request.clientId())
                    .tableId(request.tableId())
                    .date(request.date())
                    .startTime(request.startTime())
                    .endTime(endTime)
                    .guestCount(request.guestCount())
                    .status(ReservationStatus.PENDING)
                    .notes(cleanNotes(request.notes()))
                    .build();

            Reservation saved = reservationRepository.save(entity);
            log.info("Created reservation id={} tableId={} date={} slot={}-{}",
                    saved.getId(), saved.getTableId(), saved.getDate(), saved.getStartTime(), saved.getEndTime());
            return ReservationResponse.from(saved);
        });
    }

    public ReservationResponse update(Long id, ReservationRequest request) {
        LocalTime endTime = resolveAndValidateEndTime(request.date(), request.startTime());

        return transactionManager.executeWrite(() -> {
            Reservation existing = loadReservation(id);
            if (existing.getStatus() == ReservationStatus.CANCELLED) {
                throw new BusinessConflictException(ErrorCode.RESERVATION_CANCELLED_IMMUTABLE);
            }
            assertReservationNotStarted(existing);
            if (!Objects.equals(existing.getClientId(), request.clientId())) {
                throw new BusinessConflictException(ErrorCode.RESERVATION_CLIENT_IMMUTABLE);
            }

            assertClientCanBook(existing.getClientId());
            RestaurantTable table = assertTableFitsGuests(request.tableId(), request.guestCount());

            if (overlapsExistingActive(table.getId(), request.date(), request.startTime(), endTime, id)) {
                throw new BusinessConflictException(ErrorCode.RESERVATION_TIME_OVERLAP);
            }

            existing.setTableId(request.tableId());
            existing.setDate(request.date());
            existing.setStartTime(request.startTime());
            existing.setEndTime(endTime);
            existing.setGuestCount(request.guestCount());
            existing.setNotes(cleanNotes(request.notes()));

            Reservation saved = reservationRepository.save(existing);
            log.info("Updated reservation id={} tableId={} date={} slot={}-{}",
                    saved.getId(), saved.getTableId(), saved.getDate(), saved.getStartTime(), saved.getEndTime());
            return ReservationResponse.from(saved);
        });
    }

    public ReservationResponse confirm(Long id) {
        return transactionManager.executeWrite(() -> {
            Reservation existing = loadReservation(id);
            if (existing.getStatus() == ReservationStatus.CONFIRMED) {
                throw new BusinessConflictException(ErrorCode.RESERVATION_ALREADY_CONFIRMED);
            }
            if (existing.getStatus() == ReservationStatus.CANCELLED) {
                throw new BusinessConflictException(ErrorCode.RESERVATION_CANNOT_CONFIRM_CANCELLED);
            }
            assertReservationNotStarted(existing);

            existing.setStatus(ReservationStatus.CONFIRMED);
            Reservation saved = reservationRepository.save(existing);
            log.info("Confirmed reservation id={}", saved.getId());
            return ReservationResponse.from(saved);
        });
    }

    public ReservationResponse cancel(Long id) {
        return transactionManager.executeWrite(() -> {
            Reservation existing = loadReservation(id);
            if (existing.getStatus() == ReservationStatus.CANCELLED) {
                throw new BusinessConflictException(ErrorCode.RESERVATION_ALREADY_CANCELLED);
            }
            assertReservationNotStarted(existing);

            existing.setStatus(ReservationStatus.CANCELLED);
            Reservation saved = reservationRepository.save(existing);
            log.info("Cancelled reservation id={} releasing tableId={}", saved.getId(), saved.getTableId());

            recordLateCancellationStrikeIfNeeded(saved);
            return ReservationResponse.from(saved);
        });
    }

    private LocalTime resolveAndValidateEndTime(LocalDate date, LocalTime startTime) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime requestedStart = LocalDateTime.of(date, startTime);
        if (!requestedStart.isAfter(now)) {
            throw new BusinessRuleException(ErrorCode.RESERVATION_IN_THE_PAST);
        }

        LocalDateTime computedEnd = requestedStart.plusMinutes(properties.turnDurationMinutes());
        LocalDateTime closingLimit = LocalDateTime.of(date, properties.closingTime());
        if (startTime.isBefore(properties.openingTime()) || computedEnd.isAfter(closingLimit)) {
            throw new BusinessRuleException(
                    ErrorCode.RESERVATION_OUTSIDE_BUSINESS_HOURS,
                    properties.openingTime(),
                    properties.closingTime()
            );
        }
        return computedEnd.toLocalTime();
    }

    private void assertClientCanBook(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLIENT_NOT_FOUND, clientId));
        if (client.getStatus() != ClientStatus.ACTIVE) {
            throw new BusinessConflictException(ErrorCode.CLIENT_NOT_ACTIVE);
        }
    }

    private RestaurantTable assertTableFitsGuests(Long tableId, int guestCount) {
        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TABLE_NOT_FOUND, tableId));
        if (!table.isActive()) {
            throw new BusinessConflictException(ErrorCode.TABLE_INACTIVE);
        }
        if (guestCount > table.getCapacity()) {
            throw new BusinessConflictException(
                    ErrorCode.RESERVATION_EXCEEDS_CAPACITY, guestCount, table.getCapacity());
        }
        int minGuests = properties.calculateMinimumGuests(table.getCapacity());
        if (guestCount < minGuests) {
            throw new BusinessConflictException(
                    ErrorCode.RESERVATION_BELOW_MIN_OCCUPANCY, guestCount, minGuests);
        }
        return table;
    }

    private void assertReservationNotStarted(Reservation reservation) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime reservationStart = LocalDateTime.of(reservation.getDate(), reservation.getStartTime());
        if (!reservationStart.isAfter(now)) {
            throw new BusinessConflictException(ErrorCode.RESERVATION_PAST_IMMUTABLE);
        }
    }

    /**
     * Evalúa solapamiento en intervalos semiabiertos [startA, endA) vs [startB, endB),
     * permitiendo turnos contiguos en la misma mesa.
     */
    private boolean overlapsExistingActive(
            Long tableId,
            LocalDate date,
            LocalTime requestedStart,
            LocalTime requestedEnd,
            Long excludedId) {
        return overlapsAny(
                reservationRepository.findByTableIdAndDate(tableId, date),
                requestedStart,
                requestedEnd,
                excludedId);
    }

    private boolean overlapsAny(
            List<Reservation> candidates,
            LocalTime requestedStart,
            LocalTime requestedEnd,
            Long excludedId) {
        return candidates.stream()
                .filter(existing -> !Objects.equals(existing.getId(), excludedId))
                .filter(existing -> existing.getStatus() != null && existing.getStatus().isActive())
                .anyMatch(existing -> requestedStart.isBefore(existing.getEndTime())
                        && existing.getStartTime().isBefore(requestedEnd));
    }

    private void recordLateCancellationStrikeIfNeeded(Reservation cancelled) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime startDateTime = LocalDateTime.of(cancelled.getDate(), cancelled.getStartTime());
        LocalDateTime freeCancellationDeadline = startDateTime.minusHours(properties.cancellationNoticeHours());

        if (now.isBefore(freeCancellationDeadline)) {
            return;
        }

        clientRepository.findById(cancelled.getClientId())
                .filter(client -> client.getStatus() != ClientStatus.DELETED)
                .ifPresent(client -> {
                    int totalStrikes = client.getLateCancellations() + 1;
                    client.setLateCancellations(totalStrikes);
                    if (totalStrikes >= properties.maxLateCancellations()
                            && client.getStatus() == ClientStatus.ACTIVE) {
                        client.setStatus(ClientStatus.BANNED);
                        log.warn("Client id={} automatically banned after {} late cancellations",
                                client.getId(), totalStrikes);
                    }
                    clientRepository.save(client);
                });
    }

    private Reservation loadReservation(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESERVATION_NOT_FOUND, id));
    }

    private String cleanNotes(String notes) {
        if (notes == null) {
            return null;
        }
        String trimmed = notes.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
