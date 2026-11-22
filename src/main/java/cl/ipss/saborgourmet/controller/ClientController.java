package cl.ipss.saborgourmet.controller;

import cl.ipss.saborgourmet.config.ApiConstants;
import cl.ipss.saborgourmet.dto.client.ClientRequest;
import cl.ipss.saborgourmet.dto.client.ClientResponse;
import cl.ipss.saborgourmet.dto.client.ClientStatusRequest;
import cl.ipss.saborgourmet.dto.common.OperationResponse;
import cl.ipss.saborgourmet.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Controlador REST para el ciclo de vida de clientes bajo {@code /api/v1/clients}.
 */
@Validated
@RestController
@RequestMapping(ApiConstants.CLIENTS_PATH)
@RequiredArgsConstructor
@Tag(name = "Clients", description = "Client registration, profile management, status administration, and deletion")
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    @Operation(summary = "List clients", description = "Returns registered clients, excluding soft-deleted records by default")
    public ResponseEntity<List<ClientResponse>> findAll(
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return ResponseEntity.ok(clientService.findAll(includeDeleted));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get client by ID")
    public ResponseEntity<ClientResponse> findById(
            @PathVariable @Positive(message = "Client ID must be positive") Long id) {
        return ResponseEntity.ok(clientService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Register a new client", description = "Returns only the created client ID and confirmation message without echoing personal data")
    public ResponseEntity<OperationResponse> create(@Valid @RequestBody ClientRequest request) {
        OperationResponse created = clientService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update client contact details", description = "Returns only the client ID, confirmation message, and updated field names without exposing personal values")
    public ResponseEntity<OperationResponse> update(
            @PathVariable @Positive(message = "Client ID must be positive") Long id,
            @Valid @RequestBody ClientRequest request) {
        return ResponseEntity.ok(clientService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update client operational status (ACTIVE or BANNED)")
    public ResponseEntity<OperationResponse> updateStatus(
            @PathVariable @Positive(message = "Client ID must be positive") Long id,
            @Valid @RequestBody ClientStatusRequest request) {
        return ResponseEntity.ok(clientService.updateStatus(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete client", description = "Deletes physically without reservation history or anonymizes as DELETED when historical reservations exist")
    public ResponseEntity<Void> delete(
            @PathVariable @Positive(message = "Client ID must be positive") Long id) {
        clientService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
