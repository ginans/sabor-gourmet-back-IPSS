package cl.ipss.saborgourmet.config;

/**
 * Shared application-wide constants for API routes, OpenAPI metadata,
 * and domain defaults, avoiding hardcoded literals across layers.
 */
public final class ApiConstants {

    private ApiConstants() {
    }

    public static final String API_V1_BASE = "/api/v1";
    public static final String CLIENTS_PATH = API_V1_BASE + "/clients";
    public static final String TABLES_PATH = API_V1_BASE + "/tables";
    public static final String RESERVATIONS_PATH = API_V1_BASE + "/reservations";

    public static final String ANONYMIZED_CLIENT_NAME = "Deleted Client";
    public static final String TEMP_FILE_SUFFIX = ".tmp";

    public static final String OPENAPI_TITLE = "Sabor Gourmet — Restaurant Reservation API";
    public static final String OPENAPI_VERSION = "1.0.0";
    public static final String OPENAPI_DESCRIPTION =
            "REST API for managing clients, tables, availability, and reservations "
                    + "using Spring Boot CSR architecture and atomic JSON file persistence.";
    public static final String OPENAPI_CONTACT_NAME = "Sabor Gourmet Backend Team — IPSS";
}
