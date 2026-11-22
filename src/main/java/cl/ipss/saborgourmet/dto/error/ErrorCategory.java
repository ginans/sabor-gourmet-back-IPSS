package cl.ipss.saborgourmet.dto.error;

/**
 * Clasificación de alto nivel de los errores devueltos por {@code GlobalExceptionHandler}.
 */
public enum ErrorCategory {
    /** Error de validación de cuerpo, parámetros o sintaxis HTTP. */
    VALIDATION,
    /** Violación de regla de dominio (recurso inexistente, conflicto o estado inválido). */
    BUSINESS,
    /** Error interno de almacenamiento, E/S o infraestructura. */
    INFRASTRUCTURE
}
