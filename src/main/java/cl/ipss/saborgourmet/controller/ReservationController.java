package cl.ipss.saborgourmet.controller;

import cl.ipss.saborgourmet.config.ApiConstants;
import cl.ipss.saborgourmet.dto.error.ApiErrorResponse;
import cl.ipss.saborgourmet.dto.reservation.ReservationRequest;
import cl.ipss.saborgourmet.dto.reservation.ReservationResponse;
import cl.ipss.saborgourmet.model.ReservationStatus;
import cl.ipss.saborgourmet.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Controlador REST para gestionar el ciclo de vida de reservas bajo {@code /api/v1/reservations}.
 */
@Validated
@RestController
@RequestMapping(ApiConstants.RESERVATIONS_PATH)
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "Table booking lifecycle operations")
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping
    @Operation(summary = "List reservations", description = "Optional filters by date, status, clientId, and tableId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservations retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameter",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<ReservationResponse>> findAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) @Positive(message = "Client ID must be positive") Long clientId,
            @RequestParam(required = false) @Positive(message = "Table ID must be positive") Long tableId) {
        return ResponseEntity.ok(reservationService.findAll(date, status, clientId, tableId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get reservation by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation found"),
            @ApiResponse(responseCode = "400", description = "Invalid reservation ID",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Reservation not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ReservationResponse> findById(
            @PathVariable @Positive(message = "Reservation ID must be positive") Long id) {
        return ResponseEntity.ok(reservationService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Create reservation")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reservation created in PENDING status"),
            @ApiResponse(responseCode = "400", description = "Invalid payload, past date, or outside business hours",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Client or table not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Client/table ineligible, capacity/occupancy rule, or time overlap",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody ReservationRequest request) {
        ReservationResponse saved = reservationService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.id())
                .toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update active reservation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid payload, past date, or outside business hours",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Reservation, client, or table not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Cancelled/past reservation, late reschedule, owner change, or overlap",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ReservationResponse> update(
            @PathVariable @Positive(message = "Reservation ID must be positive") Long id,
            @Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.ok(reservationService.update(id, request));
    }

    @PatchMapping("/{id}/confirm")
    @Operation(summary = "Confirm pending reservation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation confirmed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid reservation ID",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Reservation or client not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Already confirmed, cancelled, past, or client not active",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ReservationResponse> confirm(
            @PathVariable @Positive(message = "Reservation ID must be positive") Long id) {
        return ResponseEntity.ok(reservationService.confirm(id));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel reservation and release table availability")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation cancelled and table availability released"),
            @ApiResponse(responseCode = "400", description = "Invalid reservation ID",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Reservation not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Already cancelled or already started",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ReservationResponse> cancel(
            @PathVariable @Positive(message = "Reservation ID must be positive") Long id) {
        return ResponseEntity.ok(reservationService.cancel(id));
    }
}
