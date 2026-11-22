package cl.ipss.saborgourmet.exception;

import cl.ipss.saborgourmet.dto.error.ErrorCategory;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Catálogo centralizado de códigos de error, plantillas de mensajes, categorías
 * y códigos de estado HTTP devueltos por la API REST.
 */
@Getter
public enum ErrorCode {

    VALIDATION_FAILED(
            "VALIDATION_VL001",
            "Request validation failed. Check the 'fieldErrors' details.",
            ErrorCategory.VALIDATION,
            HttpStatus.BAD_REQUEST
    ),
    CONSTRAINT_VIOLATION(
            "VALIDATION_VL002",
            "Request parameter validation failed.",
            ErrorCategory.VALIDATION,
            HttpStatus.BAD_REQUEST
    ),
    INVALID_REQUEST(
            "VALIDATION_VL003",
            "Malformed JSON payload or unsupported field format.",
            ErrorCategory.VALIDATION,
            HttpStatus.BAD_REQUEST
    ),
    MISSING_PARAMETER(
            "VALIDATION_VL004",
            "Missing required query parameter: '%s'.",
            ErrorCategory.VALIDATION,
            HttpStatus.BAD_REQUEST
    ),
    INVALID_PARAMETER_TYPE(
            "VALIDATION_VL005",
            "Invalid value for parameter '%s'.",
            ErrorCategory.VALIDATION,
            HttpStatus.BAD_REQUEST
    ),
    ENDPOINT_NOT_FOUND(
            "VALIDATION_VL006",
            "The requested endpoint does not exist.",
            ErrorCategory.VALIDATION,
            HttpStatus.NOT_FOUND
    ),
    METHOD_NOT_ALLOWED(
            "VALIDATION_VL007",
            "HTTP method '%s' is not supported for this endpoint.",
            ErrorCategory.VALIDATION,
            HttpStatus.METHOD_NOT_ALLOWED
    ),
    UNSUPPORTED_MEDIA_TYPE(
            "VALIDATION_VL008",
            "Content-Type '%s' is not supported. Use 'application/json'.",
            ErrorCategory.VALIDATION,
            HttpStatus.UNSUPPORTED_MEDIA_TYPE
    ),
    NOT_ACCEPTABLE(
            "VALIDATION_VL009",
            "Requested media type in Accept header is not supported.",
            ErrorCategory.VALIDATION,
            HttpStatus.NOT_ACCEPTABLE
    ),

    CLIENT_NOT_FOUND(
            "CLIENTS_BS001",
            "Client with id %d was not found.",
            ErrorCategory.BUSINESS,
            HttpStatus.NOT_FOUND
    ),
    CLIENT_EMAIL_ALREADY_EXISTS(
            "CLIENTS_BS002",
            "A client with the provided email already exists.",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),
    CLIENT_DELETED_IMMUTABLE(
            "CLIENTS_BS003",
            "Deleted clients cannot be modified or reactivated.",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),
    CLIENT_HAS_ACTIVE_RESERVATIONS(
            "CLIENTS_BS004",
            "Client has active reservations and cannot be deleted until they are cancelled.",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),
    CLIENT_INVALID_STATUS_TRANSITION(
            "CLIENTS_BS005",
            "Client status can only be manually updated to ACTIVE or BANNED.",
            ErrorCategory.BUSINESS,
            HttpStatus.BAD_REQUEST
    ),
    CLIENT_NOT_ACTIVE(
            "CLIENTS_BS006",
            "Client is not active and cannot create or hold reservations.",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),
    CLIENT_BANNED_DELETE_BLOCKED(
            "CLIENTS_BS007",
            "Banned clients cannot be deleted directly. Lift the ban before deleting this client.",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),

    TABLE_NOT_FOUND(
            "TABLES_BS001",
            "Table with id %d was not found.",
            ErrorCategory.BUSINESS,
            HttpStatus.NOT_FOUND
    ),
    TABLE_NUMBER_ALREADY_EXISTS(
            "TABLES_BS002",
            "A table with number %d already exists.",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),
    TABLE_CAPACITY_REDUCTION_CONFLICT(
            "TABLES_BS003",
            "Cannot reduce table capacity because active reservations exceed the new capacity.",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),
    TABLE_DEACTIVATION_CONFLICT(
            "TABLES_BS004",
            "Cannot deactivate table because it has active reservations.",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),
    TABLE_HAS_RESERVATIONS(
            "TABLES_BS005",
            "Cannot delete table because it has associated reservations.",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),
    TABLE_INACTIVE(
            "TABLES_BS006",
            "The selected table is currently inactive.",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),
    TABLE_CAPACITY_MIN_OCCUPANCY_CONFLICT(
            "TABLES_BS007",
            "Cannot increase table capacity because active reservations would fall below minimum occupancy.",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),

    RESERVATION_NOT_FOUND(
            "RESERVATIONS_BS001",
            "Reservation with id %d was not found.",
            ErrorCategory.BUSINESS,
            HttpStatus.NOT_FOUND
    ),
    RESERVATION_IN_THE_PAST(
            "RESERVATIONS_BS002",
            "Reservation date and time must be in the future.",
            ErrorCategory.BUSINESS,
            HttpStatus.BAD_REQUEST
    ),
    RESERVATION_OUTSIDE_BUSINESS_HOURS(
            "RESERVATIONS_BS003",
            "Reservation must fall within business hours (%s to %s).",
            ErrorCategory.BUSINESS,
            HttpStatus.BAD_REQUEST
    ),
    RESERVATION_EXCEEDS_CAPACITY(
            "RESERVATIONS_BS004",
            "Guest count (%d) exceeds table capacity (%d).",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),
    RESERVATION_BELOW_MIN_OCCUPANCY(
            "RESERVATIONS_BS005",
            "Guest count (%d) is below the minimum occupancy of %d guests required for this table.",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),
    RESERVATION_TIME_OVERLAP(
            "RESERVATIONS_BS006",
            "The selected table already has an active reservation overlapping this time slot.",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),
    RESERVATION_CANCELLED_IMMUTABLE(
            "RESERVATIONS_BS007",
            "Cancelled reservations cannot be modified.",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),
    RESERVATION_ALREADY_CONFIRMED(
            "RESERVATIONS_BS008",
            "Reservation is already confirmed.",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),
    RESERVATION_CANNOT_CONFIRM_CANCELLED(
            "RESERVATIONS_BS009",
            "Cancelled reservations cannot be confirmed.",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),
    RESERVATION_ALREADY_CANCELLED(
            "RESERVATIONS_BS010",
            "Reservation is already cancelled.",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),
    RESERVATION_PAST_IMMUTABLE(
            "RESERVATIONS_BS011",
            "Past or already started reservations cannot be modified, confirmed, or cancelled.",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),
    RESERVATION_CLIENT_IMMUTABLE(
            "RESERVATIONS_BS012",
            "The client owner of an existing reservation cannot be changed.",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),
    RESERVATION_LATE_MODIFICATION_BLOCKED(
            "RESERVATIONS_BS013",
            "Reservations starting within %d hours cannot be rescheduled to a different date or time.",
            ErrorCategory.BUSINESS,
            HttpStatus.CONFLICT
    ),

    DATA_STORAGE_ERROR(
            "INFRA_IN001",
            "An error occurred while persisting data. Existing records were preserved.",
            ErrorCategory.INFRASTRUCTURE,
            HttpStatus.INTERNAL_SERVER_ERROR
    ),
    INTERNAL_SERVER_ERROR(
            "INFRA_IN002",
            "An unexpected internal error occurred. Please try again later.",
            ErrorCategory.INFRASTRUCTURE,
            HttpStatus.INTERNAL_SERVER_ERROR
    );

    private final String code;
    private final String messageTemplate;
    private final ErrorCategory category;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String messageTemplate, ErrorCategory category, HttpStatus httpStatus) {
        this.code = code;
        this.messageTemplate = messageTemplate;
        this.category = category;
        this.httpStatus = httpStatus;
    }

    public String formatMessage(Object... args) {
        if (args == null || args.length == 0) {
            return messageTemplate;
        }
        return String.format(messageTemplate, args);
    }
}
