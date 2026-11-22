package cl.ipss.saborgourmet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import cl.ipss.saborgourmet.config.ReservationProperties;
import cl.ipss.saborgourmet.dto.reservation.*;
import cl.ipss.saborgourmet.exception.ErrorCode;
import cl.ipss.saborgourmet.exception.business.*;
import cl.ipss.saborgourmet.model.*;
import cl.ipss.saborgourmet.repository.*;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final Instant FIXED_NOW = LocalDateTime.of(2026, 12, 1, 10, 0).atZone(ZONE).toInstant();

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private TableRepository tableRepository;

    @Mock
    private StorageTransactionManager transactionManager;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        ReservationProperties properties = new ReservationProperties(
                120, LocalTime.of(12, 0), LocalTime.of(23, 0), 75, 24, 3);
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZONE);
        reservationService = new ReservationService(
                reservationRepository, clientRepository, tableRepository, transactionManager, properties, fixedClock);

        lenient().when(transactionManager.executeRead(any())).thenAnswer(inv -> {
            Supplier<?> supplier = inv.getArgument(0);
            return supplier.get();
        });
        lenient().when(transactionManager.executeWrite(any(Supplier.class))).thenAnswer(inv -> {
            Supplier<?> supplier = inv.getArgument(0);
            return supplier.get();
        });
    }

    @Test
    @DisplayName("SG-RES-01: Availability filters out inactive, under-capacity, under-75%, and overlapping tables")
    void checkAvailabilityAppliesAllFilters() {
        LocalDate date = LocalDate.of(2026, 12, 5);
        RestaurantTable table2 = RestaurantTable.builder().id(1L).number(1).capacity(2).location("Hall").active(true).build();
        RestaurantTable table4Occupied = RestaurantTable.builder().id(2L).number(2).capacity(4).location("Hall").active(true).build();
        RestaurantTable table4Free = RestaurantTable.builder().id(3L).number(3).capacity(4).location("Terrace").active(true).build();
        RestaurantTable table6TooBig = RestaurantTable.builder().id(4L).number(4).capacity(6).location("VIP").active(true).build();
        RestaurantTable table4Inactive = RestaurantTable.builder().id(5L).number(5).capacity(4).location("Patio").active(false).build();

        when(tableRepository.findAll()).thenReturn(List.of(table2, table4Occupied, table4Free, table6TooBig, table4Inactive));
        when(reservationRepository.findByTableIdAndDate(2L, date)).thenReturn(List.of(
                Reservation.builder().id(10L).tableId(2L).date(date)
                        .startTime(LocalTime.of(19, 0)).endTime(LocalTime.of(21, 0))
                        .status(ReservationStatus.CONFIRMED).build()
        ));
        when(reservationRepository.findByTableIdAndDate(3L, date)).thenReturn(List.of());

        AvailabilityResponse result = reservationService.checkAvailability(date, LocalTime.of(20, 0), 3);

        assertThat(result.endTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(result.availableCount()).isEqualTo(1);
        assertThat(result.availableTables()).extracting(AvailableTableResponse::id).containsExactly(3L);
        assertThat(result.availableTables().get(0).minGuests()).isEqualTo(3);
    }

    @Test
    @DisplayName("SG-RES-02: Creates reservation in PENDING state with computed 120-minute endTime")
    void createStartsAsPendingWithComputedEndTime() {
        LocalDate date = LocalDate.of(2026, 12, 5);
        stubActiveClient(1L);
        stubActiveTable(2L, 4);
        when(reservationRepository.findByTableIdAndDate(2L, date)).thenReturn(List.of());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });

        ReservationResponse created = reservationService.create(
                new ReservationRequest(1L, 2L, date, LocalTime.of(20, 0), 3, "  Window  "));

        assertThat(created.id()).isEqualTo(1L);
        assertThat(created.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(created.endTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(created.notes()).isEqualTo("Window");
    }

    @Test
    @DisplayName("SG-RES-03 / SG-RES-04: Rejects past date/time and slots outside business hours (12:00-23:00)")
    void createRejectsPastOrOutsideBusinessHours() {
        assertThatThrownBy(() -> reservationService.create(
                new ReservationRequest(1L, 1L, LocalDate.of(2026, 12, 1), LocalTime.of(9, 30), 2, null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_IN_THE_PAST);

        assertThatThrownBy(() -> reservationService.create(
                new ReservationRequest(1L, 1L, LocalDate.of(2026, 12, 5), LocalTime.of(11, 0), 2, null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_OUTSIDE_BUSINESS_HOURS);

        assertThatThrownBy(() -> reservationService.create(
                new ReservationRequest(1L, 1L, LocalDate.of(2026, 12, 5), LocalTime.of(21, 30), 2, null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_OUTSIDE_BUSINESS_HOURS);
    }

    @Test
    @DisplayName("SG-RES-05 / SG-RES-06: Rejects missing client/table (404), banned client or inactive table (409)")
    void createValidatesExistenceAndEligibility() {
        LocalDate date = LocalDate.of(2026, 12, 5);
        when(clientRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> reservationService.create(
                new ReservationRequest(99L, 1L, date, LocalTime.of(20, 0), 2, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ResourceNotFoundException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CLIENT_NOT_FOUND);

        when(clientRepository.findById(2L)).thenReturn(Optional.of(
                Client.builder().id(2L).status(ClientStatus.BANNED).build()));
        assertThatThrownBy(() -> reservationService.create(
                new ReservationRequest(2L, 1L, date, LocalTime.of(20, 0), 2, null)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(ex -> ((BusinessConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CLIENT_NOT_ACTIVE);

        stubActiveClient(1L);
        when(tableRepository.findById(5L)).thenReturn(Optional.of(
                RestaurantTable.builder().id(5L).number(5).capacity(4).active(false).build()));
        assertThatThrownBy(() -> reservationService.create(
                new ReservationRequest(1L, 5L, date, LocalTime.of(20, 0), 3, null)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(ex -> ((BusinessConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TABLE_INACTIVE);
    }

    @Test
    @DisplayName("SG-RES-07 / SG-RES-08: Enforces max capacity and 75% floor minimum occupancy")
    void createEnforcesCapacityAndMinOccupancy() {
        LocalDate date = LocalDate.of(2026, 12, 5);
        stubActiveClient(1L);
        stubActiveTable(3L, 6);

        assertThatThrownBy(() -> reservationService.create(
                new ReservationRequest(1L, 3L, date, LocalTime.of(20, 0), 7, null)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(ex -> ((BusinessConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_EXCEEDS_CAPACITY);

        assertThatThrownBy(() -> reservationService.create(
                new ReservationRequest(1L, 3L, date, LocalTime.of(20, 0), 3, null)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(ex -> ((BusinessConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_BELOW_MIN_OCCUPANCY);
    }

    @Test
    @DisplayName("SG-RES-09 / SG-RES-10: Blocks overlapping slot (409) and allows contiguous back-to-back slot (201)")
    void createBlocksOverlapAndAllowsContiguousSlot() {
        LocalDate date = LocalDate.of(2026, 12, 5);
        stubActiveClient(1L);
        stubActiveTable(1L, 4);
        Reservation existing = Reservation.builder()
                .id(10L).clientId(1L).tableId(1L).date(date)
                .startTime(LocalTime.of(19, 0)).endTime(LocalTime.of(21, 0))
                .guestCount(4).status(ReservationStatus.PENDING).build();
        when(reservationRepository.findByTableIdAndDate(1L, date)).thenReturn(List.of(existing));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(11L);
            return r;
        });

        assertThatThrownBy(() -> reservationService.create(
                new ReservationRequest(1L, 1L, date, LocalTime.of(20, 30), 3, null)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(ex -> ((BusinessConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_TIME_OVERLAP);

        ReservationResponse contiguous = reservationService.create(
                new ReservationRequest(1L, 1L, date, LocalTime.of(21, 0), 3, null));
        assertThat(contiguous.id()).isEqualTo(11L);
        assertThat(contiguous.endTime()).isEqualTo(LocalTime.of(23, 0));
    }

    @Test
    @DisplayName("SG-RES-14 / SG-RES-15: Modifies active reservation excluding self-overlap and rejects cancelled modification")
    void updateExcludesSelfOverlapAndRejectsCancelled() {
        LocalDate date = LocalDate.of(2026, 12, 5);
        stubActiveClient(1L);
        stubActiveTable(1L, 4);
        Reservation active = Reservation.builder()
                .id(1L).clientId(1L).tableId(1L).date(date)
                .startTime(LocalTime.of(19, 0)).endTime(LocalTime.of(21, 0))
                .guestCount(4).status(ReservationStatus.PENDING).build();
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(active));
        when(reservationRepository.findByTableIdAndDate(1L, date)).thenReturn(List.of(active));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse updated = reservationService.update(
                1L, new ReservationRequest(1L, 1L, date, LocalTime.of(19, 30), 3, "Updated"));
        assertThat(updated.startTime()).isEqualTo(LocalTime.of(19, 30));
        assertThat(updated.endTime()).isEqualTo(LocalTime.of(21, 30));

        Reservation cancelled = Reservation.builder().id(2L).status(ReservationStatus.CANCELLED).build();
        when(reservationRepository.findById(2L)).thenReturn(Optional.of(cancelled));
        assertThatThrownBy(() -> reservationService.update(
                2L, new ReservationRequest(1L, 1L, date, LocalTime.of(20, 0), 3, null)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(ex -> ((BusinessConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_CANCELLED_IMMUTABLE);
    }

    @Test
    @DisplayName("SG-RES-16 / SG-RES-17: Confirms pending reservation and rejects already confirmed or cancelled")
    void confirmTransitionsOnlyFromPending() {
        Reservation pending = Reservation.builder().id(1L).status(ReservationStatus.PENDING).build();
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse confirmed = reservationService.confirm(1L);
        assertThat(confirmed.status()).isEqualTo(ReservationStatus.CONFIRMED);

        assertThatThrownBy(() -> reservationService.confirm(1L))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(ex -> ((BusinessConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_ALREADY_CONFIRMED);

        Reservation cancelled = Reservation.builder().id(2L).status(ReservationStatus.CANCELLED).build();
        when(reservationRepository.findById(2L)).thenReturn(Optional.of(cancelled));
        assertThatThrownBy(() -> reservationService.confirm(2L))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(ex -> ((BusinessConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_CANNOT_CONFIRM_CANCELLED);
    }

    @Test
    @DisplayName("SG-RES-18 / SG-RES-19 / SG-RES-20: Cancels reservation, rejects double cancel, and auto-bans on 3rd late cancellation (<24h)")
    void cancelRecordsLateStrikeAndAutoBansOnThirdStrike() {
        Client client = Client.builder()
                .id(1L).name("Ana").email("ana@correo.cl").phone("+56912345678")
                .status(ClientStatus.ACTIVE).lateCancellations(2).build();
        Reservation soonReservation = Reservation.builder()
                .id(5L).clientId(1L).tableId(1L)
                .date(LocalDate.of(2026, 12, 1))
                .startTime(LocalTime.of(20, 0)).endTime(LocalTime.of(22, 0))
                .guestCount(4).status(ReservationStatus.CONFIRMED).build();

        when(reservationRepository.findById(5L)).thenReturn(Optional.of(soonReservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse cancelled = reservationService.cancel(5L);

        assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(client.getLateCancellations()).isEqualTo(3);
        assertThat(client.getStatus()).isEqualTo(ClientStatus.BANNED);

        assertThatThrownBy(() -> reservationService.cancel(5L))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(ex -> ((BusinessConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_ALREADY_CANCELLED);
    }

    private void stubActiveClient(Long id) {
        when(clientRepository.findById(id)).thenReturn(Optional.of(
                Client.builder().id(id).name("Ana").email("ana@correo.cl").status(ClientStatus.ACTIVE).build()));
    }

    private void stubActiveTable(Long id, int capacity) {
        when(tableRepository.findById(id)).thenReturn(Optional.of(
                RestaurantTable.builder().id(id).number(id.intValue()).capacity(capacity)
                        .location("Main Hall").active(true).build()));
    }
}
