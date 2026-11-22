# Sabor Gourmet — Restaurant Reservation REST API

REST API desarrollada en **Spring Boot** para la gestión de clientes, mesas, disponibilidad y reservas del restaurante **Sabor Gourmet** (Evaluación Sumativa Unidad N° 2 — Instituto Profesional San Sebastián).

## Stack Técnico y Versiones

* **Spring Boot `3.5.16`:** última versión estable de la línea 3.5.x, integrando `spring-boot-starter-web`, `spring-boot-starter-validation` y `spring-boot-starter-test`.
* **Java 21 (bytecode target) ejecutado sobre OpenJDK 25:** garantiza compatibilidad hacia adelante tanto en entornos con Java 21 LTS como con Java 25 LTS.
* **Maven Wrapper (`./mvnw` / `mvnw.cmd`):** fija Maven `3.10.1` dentro del repositorio para ejecución reproducible sin instalación global de Maven.
* **Lombok `1.18.48`:** reducción de *boilerplate* en entidades, inyección por constructor (`@RequiredArgsConstructor`), logging (`@Slf4j`) y constantes de nombres de campo (`@FieldNameConstants`).
* **SpringDoc OpenAPI `2.10.1`:** documentación interactiva Swagger UI autogenerada al iniciar la aplicación.
* **Persistencia JSON (Jackson):** unidad de trabajo en memoria con volcado atómico en [`data/data.json`](./data/data.json), sin base de datos ni Spring Data JPA.

---

## Arquitectura CSR (Controller – Service – Repository)

```text
HTTP Request (JSON)
       │
       ▼
  Controller (@RestController)   ──▶ Valida DTOs (@Valid) y @PathVariable (@Positive),
       │                             retorna ResponseEntity (nunca accede al Repository)
       ▼
   Service (@Service)            ──▶ Reglas de negocio: turnos 120m, ocupación 75% floor,
       │                             solapamiento, soft-delete, baneo automático
       ▼
  Repository (Interfaces)        ──▶ Contratos desacoplados listos para SQL/JPA
       │
       ▼
  repository/json (@Repository)  ──▶ JsonFileStorage: Jackson tolerante + unidad de trabajo
       │                             (1 lectura / 1 flush atómico .tmp -> Files.move)
       ▼                             + ReentrantReadWriteLock(true) + secuencias de IDs
  data/data.json
```

El manejo centralizado de excepciones ([`GlobalExceptionHandler`](src/main/java/cl/ipss/saborgourmet/exception/GlobalExceptionHandler.java)) traduce errores de validación (`VALIDATION`), negocio (`BUSINESS`) e infraestructura (`INFRASTRUCTURE`) mediante [`ErrorCode`](src/main/java/cl/ipss/saborgourmet/exception/ErrorCode.java), devolviendo respuestas JSON uniformes (`400`, `404`, `405`, `406`, `409`, `415`, `500`) sin exponer trazas internas.

---

## Ejecución Rápida

### 1. Ejecutar pruebas automatizadas (50 tests: unitarios, integración, regresión y concurrencia)
```bash
./mvnw clean test
```
*(En Windows: `mvnw.cmd clean test`)*

### 2. Iniciar la aplicación
```bash
./mvnw spring-boot:run
```
La API queda disponible en `http://localhost:8080`.

### 3. Documentación interactiva (Swagger UI) y Postman
* **Swagger UI:** `http://localhost:8080/swagger-ui.html`
* **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`
* **Colección Postman (parametrizada y reproducible):** [`postman/SaborGourmet.postman_collection.json`](./postman/SaborGourmet.postman_collection.json)

---

## Persistencia en Archivo JSON (`data/data.json`)

* **Ruta configurable:** `app.storage.file-path` en [`application.yml`](src/main/resources/application.yml) (sobrescribible vía variable de entorno `APP_STORAGE_FILE_PATH`, valor por defecto `data/data.json`).
* **Inicialización automática:** Si el archivo no existe, `JsonFileStorage` crea los directorios y genera automáticamente un JSON válido con listas vacías y secuencias iniciales.
* **Sin redundancias ni reciclaje de IDs:** Cada reserva referencia a cliente y mesa exclusivamente por `clientId` y `tableId`, y las secuencias persistentes (`nextClientId`, `nextTableId`, `nextReservationId`) impiden reutilizar identificadores tras eliminaciones físicas.
* **Unidad de trabajo atómica y concurrencia local:** Cada operación `executeRead` / `executeWrite` lee el archivo una sola vez (evitando lecturas N+1) bajo `ReentrantReadWriteLock(true)`, acumula mutaciones multi-entidad en memoria y realiza un único volcado atómico (`.tmp` → `Files.move`) solo si la operación completa sin errores. Ante fallos de E/S, los datos previos se conservan íntegros.

---

## Mapa de Endpoints REST

| Recurso | Método y Ruta | Descripción | Códigos HTTP |
| :--- | :--- | :--- | :--- |
| **Clients** | `GET /api/v1/clients` | Listar clientes (excluye `DELETED` por defecto; opcional `?includeDeleted=true`) | `200` |
| | `GET /api/v1/clients/{id}` | Consultar cliente por ID | `200`, `400`, `404` |
| | `POST /api/v1/clients` | Registrar cliente (devuelve solo `id` y mensaje sin eco de PII) | `201`, `400`, `409`, `415` |
| | `PUT /api/v1/clients/{id}` | Actualizar cliente (devuelve `id`, mensaje y `updatedFields` sin valores) | `200`, `400`, `404`, `409` |
| | `PATCH /api/v1/clients/{id}/status` | Cambiar estado operativo (`ACTIVE` / `BANNED`) | `200`, `400`, `404`, `409` |
| | `DELETE /api/v1/clients/{id}` | Eliminación física sin historial o soft-delete anonimizado con historial | `204`, `400`, `404`, `409` |
| **Tables** | `GET /api/v1/tables` | Listar mesas (opcional `?onlyActive=true`) | `200` |
| | `GET /api/v1/tables/{id}` | Consultar mesa por ID | `200`, `400`, `404` |
| | `GET /api/v1/tables/availability?date=&time=&guests=` | Consultar mesas disponibles (turno 120 min, ocupación mínima 75%) | `200`, `400` |
| | `POST /api/v1/tables` | Crear mesa | `201`, `400`, `409` |
| | `PUT /api/v1/tables/{id}` | Actualizar configuración de mesa | `200`, `400`, `404`, `409` |
| | `DELETE /api/v1/tables/{id}` | Eliminar mesa sin reservas asociadas | `204`, `400`, `404`, `409` |
| **Reservations** | `GET /api/v1/reservations` | Listar reservas (filtros opcionales: `date`, `status`, `clientId`, `tableId`) | `200` |
| | `GET /api/v1/reservations/{id}` | Consultar reserva por ID | `200`, `400`, `404` |
| | `POST /api/v1/reservations` | Crear reserva (`PENDING`, bloquea mesa inmediatamente) | `201`, `400`, `404`, `409` |
| | `PUT /api/v1/reservations/{id}` | Modificar reserva vigente futura (sin cambiar `clientId`) | `200`, `400`, `404`, `409` |
| | `PATCH /api/v1/reservations/{id}/confirm` | Confirmar reserva pendiente futura (cliente o personal) | `200`, `400`, `404`, `409` |
| | **`PATCH /api/v1/reservations/{id}/cancel`** | **Cancelar reserva futura:** cambia estado a `CANCELLED`, persiste en JSON, libera disponibilidad inmediatamente y evalúa cancelación tardía (< 24h) | `200`, `400`, `404`, `409` |

---

## Documentación Adicional

* **Reglas de negocio y escenarios BDD (Gherkin):** [`docs/reglas-negocio/README.md`](./docs/reglas-negocio/README.md)
