package cl.ipss.saborgourmet.controller;

import cl.ipss.saborgourmet.config.ApiConstants;
import cl.ipss.saborgourmet.dto.reservation.AvailabilityResponse;
import cl.ipss.saborgourmet.dto.table.TableRequest;
import cl.ipss.saborgourmet.dto.table.TableResponse;
import cl.ipss.saborgourmet.service.ReservationService;
import cl.ipss.saborgourmet.service.TableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Controlador REST para la gestión de mesas y consulta de disponibilidad bajo {@code /api/v1/tables}.
 */
@Validated
@RestController
@RequestMapping(ApiConstants.TABLES_PATH)
@RequiredArgsConstructor
@Tag(name = "Tables", description = "Restaurant table configuration and availability")
public class TableController {

    private final TableService tableService;
    private final ReservationService reservationService;

    @GetMapping("/availability")
    @Operation(summary = "Check table availability", description = "Returns active tables without overlapping reservations that satisfy capacity and minimum occupancy rules")
    public ResponseEntity<AvailabilityResponse> checkAvailability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
            @RequestParam @Min(value = 1, message = "Guest count must be at least 1") int guests) {
        return ResponseEntity.ok(reservationService.checkAvailability(date, time, guests));
    }

    @GetMapping
    @Operation(summary = "List tables", description = "Returns all configured tables, optionally filtered by active status")
    public ResponseEntity<List<TableResponse>> findAll(
            @RequestParam(defaultValue = "false") boolean onlyActive) {
        return ResponseEntity.ok(tableService.findAll(onlyActive));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get table by ID")
    public ResponseEntity<TableResponse> findById(
            @PathVariable @Positive(message = "Table ID must be positive") Long id) {
        return ResponseEntity.ok(tableService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Create a new table")
    public ResponseEntity<TableResponse> create(@Valid @RequestBody TableRequest request) {
        TableResponse created = tableService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update table configuration")
    public ResponseEntity<TableResponse> update(
            @PathVariable @Positive(message = "Table ID must be positive") Long id,
            @Valid @RequestBody TableRequest request) {
        return ResponseEntity.ok(tableService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete table", description = "Deletes a table only if it has no associated reservations")
    public ResponseEntity<Void> delete(
            @PathVariable @Positive(message = "Table ID must be positive") Long id) {
        tableService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
