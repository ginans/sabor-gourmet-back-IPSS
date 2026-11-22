# Sabor Gourmet — Restaurant Reservation REST API

REST API desarrollada en **Spring Boot** para la gestión de clientes, mesas, disponibilidad y reservas del restaurante **Sabor Gourmet** (Evaluación Sumativa Unidad N° 2 — Instituto Profesional San Sebastián).

## Stack Técnico y Versiones

* **Spring Boot `3.5.16`:** última versión estable de la línea 3.5.x, integrando `spring-boot-starter-web`, `spring-boot-starter-validation` y `spring-boot-starter-test`.
* **Java 21 (bytecode target) ejecutado sobre OpenJDK 25:** garantiza compatibilidad hacia adelante tanto en entornos con Java 21 LTS como con Java 25 LTS.
* **Maven Wrapper (`./mvnw` / `mvnw.cmd`):** fija Maven `3.10.1` dentro del repositorio para ejecución reproducible sin instalación global de Maven.
* **Lombok `1.18.48`:** reducción de *boilerplate* en entidades, inyección por constructor (`@RequiredArgsConstructor`), logging (`@Slf4j`) y constantes de nombres de campo (`@FieldNameConstants`).
* **SpringDoc OpenAPI `2.10.1`:** documentación interactiva Swagger UI autogenerada al iniciar la aplicación.
* **Persistencia JSON (Jackson):** lectura/escritura atómica en [`data/data.json`](./data/data.json) sin base de datos ni Spring Data JPA.

---

## Arquitectura CSR (Controller – Service – Repository)

```text
HTTP Request (JSON)
       │
       ▼
  Controller (@RestController)   ──▶ Valida DTOs (@Valid), retorna ResponseEntity
       │                             (nunca accede directo al Repository)
       ▼
   Service (@Service)            ──▶ Reglas de negocio: turnos, 75% floor,
       │                             solapamiento, soft-delete, baneo automático
       ▼
  Repository (Interfaces)        ──▶ Contratos desacoplados listos para SQL/JPA
       │
       ▼
  repository/json (@Repository)  ──▶ JsonFileStorage: Jackson + escritura atómica
       │                             (.tmp -> Files.move) + ReentrantReadWriteLock
       ▼
  data/data.json
```

El manejo centralizado de excepciones ([`GlobalExceptionHandler`](src/main/java/cl/ipss/saborgourmet/exception/GlobalExceptionHandler.java)) traduce errores de validación (`VALIDATION`), dominio (`BUSINESS`) e infraestructura (`INFRASTRUCTURE`) mediante el catálogo [`ErrorCode`](src/main/java/cl/ipss/saborgourmet/exception/ErrorCode.java), devolviendo respuestas JSON uniformes sin exponer trazas internas.

---

## Ejecución Rápida

### 1. Ejecutar pruebas automatizadas (43 tests: unitarios, integración y concurrencia)
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
* **Colección Postman:** [`postman/SaborGourmet.postman_collection.json`](./postman/SaborGourmet.postman_collection.json)

---

## Persistencia en Archivo JSON (`data/data.json`)

* **Ruta configurable:** `app.storage.file-path` en [`application.yml`](src/main/resources/application.yml) (sobrescribible vía variable de entorno `APP_STORAGE_FILE_PATH`, valor por defecto `data/data.json`).
* **Inicialización automática:** Si el archivo no existe, `JsonFileStorage` crea los directorios y genera automáticamente `{"clients":[], "tables":[], "reservations":[]}`.
* **Sin redundancias:** Cada registro en `reservations` referencia a cliente y mesa exclusivamente por `clientId` y `tableId`.
* **Escritura atómica y concurrencia local:** Cada mutación se ejecuta bajo el `writeLock` de un `ReentrantReadWriteLock(true)` (múltiples lecturas concurrentes y escrituras secuenciales justas) y escribe primero en `data.json.tmp` antes de reemplazar `data.json` con `Files.move`. Ante fallos de E/S, los datos previos se conservan íntegros.

---

## Mapa de Endpoints REST

| Recurso | Método y Ruta | Descripción | Códigos HTTP |
| :--- | :--- | :--- | :--- |
| **Clients** | `GET /api/v1/clients` | Listar clientes (excluye `DELETED` por defecto; opcional `?includeDeleted=true`) | `200` |
| | `GET /api/v1/clients/{id}` | Consultar cliente por ID | `200`, `404` |
| | `POST /api/v1/clients` | Registrar cliente (devuelve solo `id` y mensaje sin eco de PII) | `201`, `400`, `409` |
| | `PUT /api/v1/clients/{id}` | Actualizar cliente (devuelve `id`, mensaje y `updatedFields` sin valores) | `200`, `400`, `404`, `409` |
| | `PATCH /api/v1/clients/{id}/status` | Cambiar estado operativo (`ACTIVE` / `BANNED`) | `200`, `400`, `404`, `409` |
| | `DELETE /api/v1/clients/{id}` | Eliminación física sin historial o soft-delete anonimizado con historial | `204`, `404`, `409` |
| **Tables** | `GET /api/v1/tables` | Listar mesas (opcional `?onlyActive=true`) | `200` |
| | `GET /api/v1/tables/{id}` | Consultar mesa por ID | `200`, `404` |
| | `GET /api/v1/tables/availability?date=&time=&guests=` | Consultar mesas disponibles (turno 120 min, ocupación mínima 75%) | `200`, `400` |
| | `POST /api/v1/tables` | Crear mesa | `201`, `400`, `409` |
| | `PUT /api/v1/tables/{id}` | Actualizar configuración de mesa | `200`, `400`, `404`, `409` |
| | `DELETE /api/v1/tables/{id}` | Eliminar mesa sin reservas asociadas | `204`, `404`, `409` |
| **Reservations** | `GET /api/v1/reservations` | Listar reservas (filtros opcionales: `date`, `status`, `clientId`, `tableId`) | `200` |
| | `GET /api/v1/reservations/{id}` | Consultar reserva por ID | `200`, `404` |
| | `POST /api/v1/reservations` | Crear reserva (`PENDING`, bloquea mesa inmediatamente) | `201`, `400`, `404`, `409` |
| | `PUT /api/v1/reservations/{id}` | Modificar reserva vigente (`PENDING` / `CONFIRMED`) | `200`, `400`, `404`, `409` |
| | `PATCH /api/v1/reservations/{id}/confirm` | Confirmar reserva pendiente (invocable por cliente o personal) | `200`, `404`, `409` |
| | **`PATCH /api/v1/reservations/{id}/cancel`** | **Cancelar reserva:** cambia estado a `CANCELADA`/`CANCELLED`, persiste en JSON, libera disponibilidad inmediatamente y evalúa cancelación tardía (< 24h) | `200`, `404`, `409` |

---

## Documentación Adicional

* **Reglas de negocio y escenarios BDD (Gherkin):** [`docs/reglas-negocio/README.md`](./docs/reglas-negocio/README.md)
