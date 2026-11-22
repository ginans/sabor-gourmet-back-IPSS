package cl.ipss.saborgourmet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.ipss.saborgourmet.dto.table.TableRequest;
import cl.ipss.saborgourmet.dto.table.TableResponse;
import cl.ipss.saborgourmet.exception.ErrorCode;
import cl.ipss.saborgourmet.exception.business.BusinessConflictException;
import cl.ipss.saborgourmet.exception.business.ResourceNotFoundException;
import cl.ipss.saborgourmet.model.Reservation;
import cl.ipss.saborgourmet.model.ReservationStatus;
import cl.ipss.saborgourmet.model.RestaurantTable;
import cl.ipss.saborgourmet.repository.ReservationRepository;
import cl.ipss.saborgourmet.repository.StorageTransactionManager;
import cl.ipss.saborgourmet.repository.TableRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TableServiceTest {

    @Mock
    private TableRepository tableRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private StorageTransactionManager transactionManager;

    @InjectMocks
    private TableService tableService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.executeRead(any())).thenAnswer(inv -> {
            Supplier<?> supplier = inv.getArgument(0);
            return supplier.get();
        });
        lenient().when(transactionManager.executeWrite(any(Supplier.class))).thenAnswer(inv -> {
            Supplier<?> supplier = inv.getArgument(0);
            return supplier.get();
        });
        lenient().doAnswer(inv -> {
            Runnable action = inv.getArgument(0);
            action.run();
            return null;
        }).when(transactionManager).executeWrite(any(Runnable.class));
    }

    @Test
    @DisplayName("SG-MES-01: Creates active table by default when number is unique")
    void createTableDefaultsToActive() {
        when(tableRepository.findByNumber(5)).thenReturn(Optional.empty());
        when(tableRepository.save(any(RestaurantTable.class))).thenAnswer(inv -> {
            RestaurantTable t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });

        TableResponse created = tableService.create(new TableRequest(5, 4, "  Main Hall  ", null));

        assertThat(created.id()).isEqualTo(1L);
        assertThat(created.number()).isEqualTo(5);
        assertThat(created.capacity()).isEqualTo(4);
        assertThat(created.location()).isEqualTo("Main Hall");
        assertThat(created.active()).isTrue();
    }

    @Test
    @DisplayName("SG-MES-02: Rejects creation when table number already exists")
    void createRejectsDuplicateTableNumber() {
        when(tableRepository.findByNumber(5)).thenReturn(Optional.of(
                RestaurantTable.builder().id(1L).number(5).capacity(4).location("Hall").active(true).build()));

        assertThatThrownBy(() -> tableService.create(new TableRequest(5, 4, "Terrace", true)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(ex -> ((BusinessConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TABLE_NUMBER_ALREADY_EXISTS);
        verify(tableRepository, never()).save(any());
    }

    @Test
    @DisplayName("SG-MES-04 / SG-MES-05: Lists tables and throws 404 when table ID does not exist")
    void findAllAndFindByIdNotFound() {
        RestaurantTable active = RestaurantTable.builder().id(1L).number(1).capacity(4).active(true).build();
        RestaurantTable inactive = RestaurantTable.builder().id(2L).number(2).capacity(2).active(false).build();
        when(tableRepository.findAll()).thenReturn(List.of(active, inactive));
        when(tableRepository.findById(77L)).thenReturn(Optional.empty());

        assertThat(tableService.findAll(false)).hasSize(2);
        assertThat(tableService.findAll(true)).extracting(TableResponse::id).containsExactly(1L);

        assertThatThrownBy(() -> tableService.findById(77L))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ResourceNotFoundException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TABLE_NOT_FOUND);
    }

    @Test
    @DisplayName("SG-MES-06: Updates table capacity and location when no active reservations conflict")
    void updateSucceedsWithoutConflicts() {
        RestaurantTable existing = RestaurantTable.builder()
                .id(1L).number(1).capacity(4).location("Main Hall").active(true).build();
        when(tableRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(tableRepository.findByNumber(1)).thenReturn(Optional.of(existing));
        when(reservationRepository.findByTableId(1L)).thenReturn(List.of());
        when(tableRepository.save(any(RestaurantTable.class))).thenAnswer(inv -> inv.getArgument(0));

        TableResponse updated = tableService.update(1L, new TableRequest(1, 6, "Terrace", true));

        assertThat(updated.capacity()).isEqualTo(6);
        assertThat(updated.location()).isEqualTo("Terrace");
    }

    @Test
    @DisplayName("SG-MES-07: Blocks capacity reduction if an active upcoming reservation exceeds new capacity")
    void updateBlocksCapacityReductionAffectingActiveReservation() {
        RestaurantTable existing = RestaurantTable.builder()
                .id(1L).number(1).capacity(6).location("Main Hall").active(true).build();
        Reservation activeReservation = Reservation.builder()
                .id(10L).clientId(1L).tableId(1L)
                .date(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(20, 0)).endTime(LocalTime.of(22, 0))
                .guestCount(5).status(ReservationStatus.CONFIRMED).build();

        when(tableRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(tableRepository.findByNumber(1)).thenReturn(Optional.of(existing));
        when(reservationRepository.findByTableId(1L)).thenReturn(List.of(activeReservation));

        assertThatThrownBy(() -> tableService.update(1L, new TableRequest(1, 4, "Main Hall", true)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(ex -> ((BusinessConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TABLE_CAPACITY_REDUCTION_CONFLICT);
        verify(tableRepository, never()).save(any());
    }

    @Test
    @DisplayName("SG-MES-08: Blocks deactivation when table has active upcoming reservations")
    void updateBlocksDeactivationWithActiveReservations() {
        RestaurantTable existing = RestaurantTable.builder()
                .id(1L).number(1).capacity(4).location("Main Hall").active(true).build();
        Reservation activeReservation = Reservation.builder()
                .id(10L).clientId(1L).tableId(1L)
                .date(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(19, 0)).endTime(LocalTime.of(21, 0))
                .guestCount(3).status(ReservationStatus.PENDING).build();

        when(tableRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(tableRepository.findByNumber(1)).thenReturn(Optional.of(existing));
        when(reservationRepository.findByTableId(1L)).thenReturn(List.of(activeReservation));

        assertThatThrownBy(() -> tableService.update(1L, new TableRequest(1, 4, "Main Hall", false)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(ex -> ((BusinessConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TABLE_DEACTIVATION_CONFLICT);
    }

    @Test
    @DisplayName("SG-MES-09: Deletes table without associated reservations")
    void deleteRemovesTableWithoutReservations() {
        RestaurantTable existing = RestaurantTable.builder().id(2L).number(2).capacity(2).active(true).build();
        when(tableRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(reservationRepository.findByTableId(2L)).thenReturn(List.of());

        tableService.delete(2L);

        verify(tableRepository).deleteById(2L);
    }

    @Test
    @DisplayName("SG-MES-10: Blocks table deletion when any associated reservation exists")
    void deleteBlocksWhenTableHasReservations() {
        RestaurantTable existing = RestaurantTable.builder().id(1L).number(1).capacity(4).active(true).build();
        Reservation historical = Reservation.builder()
                .id(10L).clientId(1L).tableId(1L)
                .date(LocalDate.now().minusDays(2))
                .startTime(LocalTime.of(20, 0)).endTime(LocalTime.of(22, 0))
                .guestCount(3).status(ReservationStatus.CANCELLED).build();

        when(tableRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(reservationRepository.findByTableId(1L)).thenReturn(List.of(historical));

        assertThatThrownBy(() -> tableService.delete(1L))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(ex -> ((BusinessConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TABLE_HAS_RESERVATIONS);
        verify(tableRepository, never()).deleteById(1L);
    }
}
