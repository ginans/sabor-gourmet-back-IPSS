package cl.ipss.saborgourmet.service;

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
import cl.ipss.saborgourmet.repository.ClientRepository;
import cl.ipss.saborgourmet.repository.ReservationRepository;
import cl.ipss.saborgourmet.repository.StorageTransactionManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Servicio de negocio para la administración de clientes: normalización de contacto,
 * unicidad por correo electrónico, gestión de baneo y eliminación física o anonimizada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final ReservationRepository reservationRepository;
    private final StorageTransactionManager transactionManager;
    private final Clock clock;

    /**
     * Lista los clientes registrados, omitiendo por defecto los marcados como {@code DELETED}.
     */
    public List<ClientResponse> findAll(boolean includeDeleted) {
        return transactionManager.executeRead(() -> clientRepository.findAll().stream()
                .filter(client -> includeDeleted || client.getStatus() != ClientStatus.DELETED)
                .sorted(Comparator.comparing(Client::getId, Comparator.nullsLast(Long::compareTo)))
                .map(ClientResponse::from)
                .toList());
    }

    /**
     * Recupera un cliente por su identificador único o lanza 404 si no existe.
     */
    public ClientResponse findById(Long id) {
        return transactionManager.executeRead(() -> ClientResponse.from(getClientOrThrow(id)));
    }

    /**
     * Registra un cliente nuevo normalizando correo y teléfono y evitando eco de PII en la respuesta.
     */
    public OperationResponse create(ClientRequest request) {
        return transactionManager.executeWrite(() -> {
            String normalizedEmail = normalizeEmail(request.email());
            String normalizedPhone = normalizePhone(request.phone());

            clientRepository.findByEmailAndStatusNot(normalizedEmail, ClientStatus.DELETED)
                    .ifPresent(existing -> {
                        throw new BusinessConflictException(ErrorCode.CLIENT_EMAIL_ALREADY_EXISTS);
                    });

            Client client = Client.builder()
                    .name(request.name().trim())
                    .email(normalizedEmail)
                    .phone(normalizedPhone)
                    .status(ClientStatus.ACTIVE)
                    .lateCancellations(0)
                    .build();

            Client saved = clientRepository.save(client);
            log.info("Registered client id={}", saved.getId());
            return OperationResponse.of(saved.getId(), ApiConstants.MSG_CLIENT_CREATED);
        });
    }

    /**
     * Actualiza los datos de un cliente activo, persistiendo solo si hubo cambios reales
     * y reportando únicamente los nombres de los campos modificados.
     * Exige levantar previamente el estado {@code BANNED} para impedir la evasión del baneo mediante cambio de correo.
     */
    public OperationResponse update(Long id, ClientRequest request) {
        return transactionManager.executeWrite(() -> {
            Client existing = getClientOrThrow(id);
            ensureNotDeleted(existing);
            if (existing.getStatus() == ClientStatus.BANNED) {
                throw new BusinessConflictException(ErrorCode.CLIENT_BANNED_UPDATE_BLOCKED);
            }

            String normalizedName = request.name().trim();
            String normalizedEmail = normalizeEmail(request.email());
            String normalizedPhone = normalizePhone(request.phone());

            clientRepository.findByEmailAndStatusNot(normalizedEmail, ClientStatus.DELETED)
                    .filter(other -> !Objects.equals(other.getId(), id))
                    .ifPresent(other -> {
                        throw new BusinessConflictException(ErrorCode.CLIENT_EMAIL_ALREADY_EXISTS);
                    });

            List<String> updatedFields = new ArrayList<>();
            if (!Objects.equals(existing.getName(), normalizedName)) {
                existing.setName(normalizedName);
                updatedFields.add(Client.Fields.name);
            }
            if (!Objects.equals(existing.getEmail(), normalizedEmail)) {
                existing.setEmail(normalizedEmail);
                updatedFields.add(Client.Fields.email);
            }
            if (!Objects.equals(existing.getPhone(), normalizedPhone)) {
                existing.setPhone(normalizedPhone);
                updatedFields.add(Client.Fields.phone);
            }

            if (!updatedFields.isEmpty()) {
                clientRepository.save(existing);
                log.info("Updated client id={} fields={}", existing.getId(), updatedFields);
            }
            return OperationResponse.ofUpdated(existing.getId(), ApiConstants.MSG_CLIENT_UPDATED, List.copyOf(updatedFields));
        });
    }

    /**
     * Cambia manualmente el estado operativo del cliente entre {@code ACTIVE} y {@code BANNED}.
     * Al rehabilitar un cliente desde {@code BANNED} a {@code ACTIVE}, reinicia su contador de cancelaciones tardías.
     */
    public OperationResponse updateStatus(Long id, ClientStatusRequest request) {
        return transactionManager.executeWrite(() -> {
            if (request.status() == ClientStatus.DELETED) {
                throw new BusinessRuleException(ErrorCode.CLIENT_INVALID_STATUS_TRANSITION);
            }

            Client existing = getClientOrThrow(id);
            ensureNotDeleted(existing);

            List<String> updatedFields = new ArrayList<>();
            if (existing.getStatus() != request.status()) {
                ClientStatus previousStatus = existing.getStatus();
                existing.setStatus(request.status());
                updatedFields.add(Client.Fields.status);
                if (previousStatus == ClientStatus.BANNED
                        && request.status() == ClientStatus.ACTIVE
                        && existing.getLateCancellations() > 0) {
                    existing.setLateCancellations(0);
                    updatedFields.add(Client.Fields.lateCancellations);
                }
                clientRepository.save(existing);
                log.info("Updated client id={} status to {} fields={}",
                        existing.getId(), existing.getStatus(), updatedFields);
            }

            return OperationResponse.ofUpdated(
                    existing.getId(),
                    ApiConstants.MSG_CLIENT_STATUS_UPDATED,
                    List.copyOf(updatedFields)
            );
        });
    }

    /**
     * Elimina físicamente al cliente si no posee reservas, o aplica soft-delete con
     * anonimización de datos personales si solo posee historial cancelado o finalizado.
     * Exige levantar previamente el estado {@code BANNED} para impedir la evasión del baneo.
     */
    public void delete(Long id) {
        transactionManager.executeWrite(() -> {
            Client existing = getClientOrThrow(id);
            ensureNotDeleted(existing);
            if (existing.getStatus() == ClientStatus.BANNED) {
                throw new BusinessConflictException(ErrorCode.CLIENT_BANNED_DELETE_BLOCKED);
            }

            List<Reservation> clientReservations = reservationRepository.findByClientId(id);
            if (clientReservations.isEmpty()) {
                clientRepository.deleteById(id);
                log.info("Physically deleted client id={} (no reservation history)", id);
                return;
            }

            LocalDateTime now = LocalDateTime.now(clock);
            boolean hasActiveUpcomingReservation = clientReservations.stream()
                    .anyMatch(reservation -> isActiveAndNotFinished(reservation, now));
            if (hasActiveUpcomingReservation) {
                throw new BusinessConflictException(ErrorCode.CLIENT_HAS_ACTIVE_RESERVATIONS);
            }

            existing.setName(ApiConstants.ANONYMIZED_CLIENT_NAME);
            existing.setEmail(null);
            existing.setPhone(null);
            existing.setStatus(ClientStatus.DELETED);
            clientRepository.save(existing);
            log.info("Anonymized and soft-deleted client id={} preserving historical references", id);
        });
    }

    private Client getClientOrThrow(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLIENT_NOT_FOUND, id));
    }

    private void ensureNotDeleted(Client client) {
        if (client.getStatus() == ClientStatus.DELETED) {
            throw new BusinessConflictException(ErrorCode.CLIENT_DELETED_IMMUTABLE);
        }
    }

    private boolean isActiveAndNotFinished(Reservation reservation, LocalDateTime now) {
        if (reservation.getStatus() == null || !reservation.getStatus().isActive()) {
            return false;
        }
        LocalDateTime reservationEnd = LocalDateTime.of(reservation.getDate(), reservation.getEndTime());
        return reservationEnd.isAfter(now);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String phone) {
        return phone.trim().replaceAll(ApiConstants.PHONE_WHITESPACE_DASH_REGEX, "");
    }
}
