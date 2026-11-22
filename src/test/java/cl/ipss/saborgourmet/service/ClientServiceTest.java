package cl.ipss.saborgourmet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.ipss.saborgourmet.config.ApiConstants;
import cl.ipss.saborgourmet.dto.client.ClientRequest;
import cl.ipss.saborgourmet.dto.client.ClientResponse;
import cl.ipss.saborgourmet.dto.client.ClientStatusRequest;
import cl.ipss.saborgourmet.dto.common.OperationResponse;
import cl.ipss.saborgourmet.exception.ErrorCode;
import cl.ipss.saborgourmet.exception.business.BusinessConflictException;
import cl.ipss.saborgourmet.exception.business.BusinessRuleException;
import cl.ipss.saborgourmet.exception.business.ResourceNotFoundException;
import cl.ipss.saborgourmet.model.Client;
import cl.ipss.saborgourmet.model.ClientStatus;
import cl.ipss.saborgourmet.model.Reservation;
import cl.ipss.saborgourmet.model.ReservationStatus;
import cl.ipss.saborgourmet.repository.ClientRepository;
import cl.ipss.saborgourmet.repository.ReservationRepository;
import cl.ipss.saborgourmet.repository.StorageTransactionManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final Instant FIXED_NOW = LocalDateTime.of(2026, 12, 1, 10, 0).atZone(ZONE).toInstant();

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private StorageTransactionManager transactionManager;

    private ClientService clientService;

    @BeforeEach
    void setUp() {
        clientService = new ClientService(
                clientRepository, reservationRepository, transactionManager, Clock.fixed(FIXED_NOW, ZONE));
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
    @DisplayName("SG-CLI-01: Creates client normalizing email and phone and returns only ID and confirmation")
    void createNormalizesEmailAndPhoneWithoutEchoingPii() {
        ClientRequest request = new ClientRequest("  Ana Perez  ", "ANA.PEREZ@correo.cl", "+56 9 1234-5678");
        when(clientRepository.findByEmailAndStatusNot("ana.perez@correo.cl", ClientStatus.DELETED))
                .thenReturn(Optional.empty());
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> {
            Client c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        OperationResponse response = clientService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.message()).isEqualTo(ApiConstants.MSG_CLIENT_CREATED);
        assertThat(response.updatedFields()).isNull();

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(captor.capture());
        Client persisted = captor.getValue();
        assertThat(persisted.getName()).isEqualTo("Ana Perez");
        assertThat(persisted.getEmail()).isEqualTo("ana.perez@correo.cl");
        assertThat(persisted.getPhone()).isEqualTo("+56912345678");
        assertThat(persisted.getStatus()).isEqualTo(ClientStatus.ACTIVE);
        assertThat(persisted.getLateCancellations()).isZero();
    }

    @Test
    @DisplayName("SG-CLI-03: Rejects duplicate email even if name or phone changes")
    void createRejectsDuplicateEmail() {
        Client existing = Client.builder()
                .id(1L).name("Juanito Perez").email("juan@correo.cl").phone("+56987654321")
                .status(ClientStatus.BANNED).build();
        when(clientRepository.findByEmailAndStatusNot("juan@correo.cl", ClientStatus.DELETED))
                .thenReturn(Optional.of(existing));

        ClientRequest request = new ClientRequest("Juan P.", "JUAN@CORREO.CL", "+56900001111");

        assertThatThrownBy(() -> clientService.create(request))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(ex -> ((BusinessConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CLIENT_EMAIL_ALREADY_EXISTS);
        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("SG-CLI-04: Lists only non-deleted clients by default")
    void findAllExcludesDeletedByDefault() {
        Client active = Client.builder().id(1L).name("Ana").status(ClientStatus.ACTIVE).build();
        Client banned = Client.builder().id(2L).name("Luis").status(ClientStatus.BANNED).build();
        Client deleted = Client.builder().id(3L).name(ApiConstants.ANONYMIZED_CLIENT_NAME)
                .status(ClientStatus.DELETED).build();
        when(clientRepository.findAll()).thenReturn(List.of(active, banned, deleted));

        List<ClientResponse> visible = clientService.findAll(false);
        List<ClientResponse> all = clientService.findAll(true);

        assertThat(visible).extracting(ClientResponse::id).containsExactly(1L, 2L);
        assertThat(all).extracting(ClientResponse::id).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("SG-CLI-05: Throws ResourceNotFoundException when client ID does not exist")
    void findByIdThrowsNotFound() {
        when(clientRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.findById(42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ResourceNotFoundException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CLIENT_NOT_FOUND);
    }

    @Test
    @DisplayName("SG-CLI-06: Updates existing client and reports only changed field names")
    void updatePersistsNormalizedDataAndReportsUpdatedFieldNames() {
        Client existing = Client.builder()
                .id(1L).name("Ana").email("ana@correo.cl").phone("+56911111111")
                .status(ClientStatus.ACTIVE).build();
        when(clientRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(clientRepository.findByEmailAndStatusNot("ana@correo.cl", ClientStatus.DELETED))
                .thenReturn(Optional.of(existing));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        OperationResponse response = clientService.update(
                1L, new ClientRequest("Ana Maria", "ANA@correo.cl", "+56 9 2222-3333"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.message()).isEqualTo(ApiConstants.MSG_CLIENT_UPDATED);
        assertThat(response.updatedFields()).containsExactly(Client.Fields.name, Client.Fields.phone);

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Ana Maria");
        assertThat(captor.getValue().getEmail()).isEqualTo("ana@correo.cl");
        assertThat(captor.getValue().getPhone()).isEqualTo("+56922223333");
    }

    @Test
    @DisplayName("SG-CLI-07: Rejects update when email already belongs to another non-deleted client")
    void updateRejectsConflictingEmailFromAnotherClient() {
        Client first = Client.builder().id(1L).email("uno@correo.cl").status(ClientStatus.ACTIVE).build();
        Client second = Client.builder().id(2L).email("dos@correo.cl").status(ClientStatus.ACTIVE).build();
        when(clientRepository.findById(1L)).thenReturn(Optional.of(first));
        when(clientRepository.findByEmailAndStatusNot("dos@correo.cl", ClientStatus.DELETED))
                .thenReturn(Optional.of(second));

        assertThatThrownBy(() -> clientService.update(
                1L, new ClientRequest("Uno", "dos@correo.cl", "+56911111111")))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(ex -> ((BusinessConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CLIENT_EMAIL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("SG-CLI-08: Allows manual transition between ACTIVE and BANNED and resets strikes on rehabilitation")
    void updateStatusAllowsBanAndUnban() {
        Client active = Client.builder().id(1L).name("Ana").status(ClientStatus.ACTIVE).lateCancellations(0).build();
        when(clientRepository.findById(1L)).thenReturn(Optional.of(active));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        OperationResponse banned = clientService.updateStatus(1L, new ClientStatusRequest(ClientStatus.BANNED));

        assertThat(banned.id()).isEqualTo(1L);
        assertThat(banned.message()).isEqualTo(ApiConstants.MSG_CLIENT_STATUS_UPDATED);
        assertThat(banned.updatedFields()).containsExactly(Client.Fields.status);
        assertThat(active.getStatus()).isEqualTo(ClientStatus.BANNED);

        active.setLateCancellations(3);
        OperationResponse rehabilitated = clientService.updateStatus(1L, new ClientStatusRequest(ClientStatus.ACTIVE));

        assertThat(rehabilitated.updatedFields())
                .containsExactly(Client.Fields.status, Client.Fields.lateCancellations);
        assertThat(active.getStatus()).isEqualTo(ClientStatus.ACTIVE);
        assertThat(active.getLateCancellations()).isZero();
    }

    @Test
    @DisplayName("Regression: Blocks direct deletion of BANNED clients until the ban is lifted")
    void deleteBlocksBannedClientUntilUnbanned() {
        Client banned = Client.builder().id(4L).status(ClientStatus.BANNED).lateCancellations(3).build();
        when(clientRepository.findById(4L)).thenReturn(Optional.of(banned));

        assertThatThrownBy(() -> clientService.delete(4L))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(ex -> ((BusinessConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CLIENT_BANNED_DELETE_BLOCKED);
        verify(clientRepository, never()).deleteById(4L);
        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("Regression: Blocks profile updates on BANNED clients to prevent email-swap ban evasion")
    void updateBlocksBannedClientToPreventBanEvasion() {
        Client banned = Client.builder()
                .id(4L).name("Ana").email("ana@correo.cl").phone("+56912345678")
                .status(ClientStatus.BANNED).lateCancellations(3).build();
        when(clientRepository.findById(4L)).thenReturn(Optional.of(banned));

        assertThatThrownBy(() -> clientService.update(
                4L, new ClientRequest("Ana", "throwaway@correo.cl", "+56912345678")))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(ex -> ((BusinessConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CLIENT_BANNED_UPDATE_BLOCKED);
        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("SG-CLI-09: Rejects status change on DELETED client or manual transition to DELETED")
    void updateStatusRejectsDeletedClientAndInvalidTarget() {
        Client deleted = Client.builder().id(2L).status(ClientStatus.DELETED).build();
        when(clientRepository.findById(2L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> clientService.updateStatus(2L, new ClientStatusRequest(ClientStatus.ACTIVE)))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(ex -> ((BusinessConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CLIENT_DELETED_IMMUTABLE);

        assertThatThrownBy(() -> clientService.updateStatus(1L, new ClientStatusRequest(ClientStatus.DELETED)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CLIENT_INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("SG-CLI-10: Physically deletes client when no reservation history exists")
    void deleteRemovesPhysicallyWithoutHistory() {
        Client client = Client.builder().id(1L).status(ClientStatus.ACTIVE).build();
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(reservationRepository.findByClientId(1L)).thenReturn(List.of());

        clientService.delete(1L);

        verify(clientRepository).deleteById(1L);
        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("SG-CLI-11: Soft-deletes and anonymizes client when only cancelled or past reservations exist")
    void deleteSoftDeletesAndAnonymizesWithHistoricalReservations() {
        Client client = Client.builder()
                .id(2L).name("Carlos").email("carlos@correo.cl").phone("+56955555555")
                .status(ClientStatus.ACTIVE).build();
        Reservation cancelled = Reservation.builder()
                .id(10L).clientId(2L).tableId(1L)
                .date(LocalDate.of(2026, 12, 3))
                .startTime(LocalTime.of(20, 0)).endTime(LocalTime.of(22, 0))
                .status(ReservationStatus.CANCELLED).build();
        Reservation past = Reservation.builder()
                .id(11L).clientId(2L).tableId(1L)
                .date(LocalDate.of(2026, 11, 28))
                .startTime(LocalTime.of(20, 0)).endTime(LocalTime.of(22, 0))
                .status(ReservationStatus.CONFIRMED).build();

        when(clientRepository.findById(2L)).thenReturn(Optional.of(client));
        when(reservationRepository.findByClientId(2L)).thenReturn(List.of(cancelled, past));

        clientService.delete(2L);

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(captor.capture());
        verify(clientRepository, never()).deleteById(2L);

        Client saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ClientStatus.DELETED);
        assertThat(saved.getName()).isEqualTo(ApiConstants.ANONYMIZED_CLIENT_NAME);
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getPhone()).isNull();
    }

    @Test
    @DisplayName("SG-CLI-12: Blocks deletion when client has active upcoming reservations")
    void deleteRejectsWhenClientHasActiveUpcomingReservation() {
        Client client = Client.builder().id(3L).status(ClientStatus.ACTIVE).build();
        Reservation upcoming = Reservation.builder()
                .id(12L).clientId(3L).tableId(1L)
                .date(LocalDate.of(2026, 12, 2))
                .startTime(LocalTime.of(20, 0)).endTime(LocalTime.of(22, 0))
                .status(ReservationStatus.PENDING).build();

        when(clientRepository.findById(3L)).thenReturn(Optional.of(client));
        when(reservationRepository.findByClientId(3L)).thenReturn(List.of(upcoming));

        assertThatThrownBy(() -> clientService.delete(3L))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(ex -> ((BusinessConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CLIENT_HAS_ACTIVE_RESERVATIONS);
        verify(clientRepository, never()).deleteById(3L);
        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("Regression: Skips disk save when update receives identical values")
    void updateSkipsSaveWhenNoFieldChanges() {
        Client existing = Client.builder()
                .id(1L).name("Ana").email("ana@correo.cl").phone("+56911111111")
                .status(ClientStatus.ACTIVE).build();
        when(clientRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(clientRepository.findByEmailAndStatusNot("ana@correo.cl", ClientStatus.DELETED))
                .thenReturn(Optional.of(existing));

        OperationResponse response = clientService.update(
                1L, new ClientRequest("Ana", "ana@correo.cl", "+56911111111"));

        assertThat(response.updatedFields()).isEmpty();
        verify(clientRepository, never()).save(any());
    }
}
