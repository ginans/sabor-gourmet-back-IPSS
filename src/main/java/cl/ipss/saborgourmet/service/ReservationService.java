package cl.ipss.saborgourmet.service;

import cl.ipss.saborgourmet.config.ReservationProperties;
import cl.ipss.saborgourmet.dto.reservation.*;
import cl.ipss.saborgourmet.exception.ErrorCode;
import cl.ipss.saborgourmet.exception.business.*;
import cl.ipss.saborgourmet.model.*;
import cl.ipss.saborgourmet.repository.*;
import java.time.*;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public AvailabilityResponse checkAvailability(LocalDate date, LocalTime startTime, int guestCount) {
        LocalTime endTime = resolveAndValidateEndTime(date, startTime);

        return transactionManager.executeRead(() -> {
            List<AvailableTableResponse> matchingTables = tableRepository.findAll().stream()
                    .filter(RestaurantTable::isActive)
                    .filter(table -> guestCount <= table.getCapacity())
                    .filter(table -> guestCount >= properties.calculateMinimumGuests(table.getCapacity()))
                    .filter(table -> !overlapsExistingActive(table.getId(), date, startTime, endTime, null))
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

            assertClientCanBook(request.clientId());
            RestaurantTable table = assertTableFitsGuests(request.tableId(), request.guestCount());

            if (overlapsExistingActive(table.getId(), request.date(), request.startTime(), endTime, id)) {
                throw new BusinessConflictException(ErrorCode.RESERVATION_TIME_OVERLAP);
            }

            existing.setClientId(request.clientId());
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

    /**
     * Checks half-open interval intersection [startA, endA) against [startB, endB),
     * allowing back-to-back contiguous slots on the same table.
     */
    private boolean overlapsExistingActive(
            Long tableId,
            LocalDate date,
            LocalTime requestedStart,
            LocalTime requestedEnd,
            Long excludedId) {
        return reservationRepository.findByTableIdAndDate(tableId, date).stream()
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
