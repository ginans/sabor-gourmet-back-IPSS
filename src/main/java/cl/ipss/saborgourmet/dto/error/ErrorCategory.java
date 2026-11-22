package cl.ipss.saborgourmet.dto.error;

/**
 * High-level classification of API errors returned by {@code GlobalExceptionHandler}.
 */
public enum ErrorCategory {
    /** Request payload, query parameter, or HTTP syntax validation error. */
    VALIDATION,
    /** Domain or business rule violation (not found, conflict, invalid state). */
    BUSINESS,
    /** Storage, I/O, or unexpected internal infrastructure error. */
    INFRASTRUCTURE
}
