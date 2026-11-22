# Sabor Gourmet — Restaurant Reservation API

API REST desarrollada con **Spring Boot** para la gestión de clientes, mesas, disponibilidad y reservas del restaurante **Sabor Gourmet** (Desarrollo de Software Web II — IPSS).

Implementa arquitectura en capas **CSR (Controller – Service – Repository)**, validación declarativa, manejo centralizado de excepciones, documentación OpenAPI interactiva y persistencia atómica en archivo JSON con control de concurrencia local.

---

## Tecnologías y Dependencias

| Tecnología / Dependencia | Versión | Propósito y justificación |
| :--- | :---: | :--- |
| **Java** | `21` (JDK 21+) | Versión LTS moderna; aporta `record` para DTOs inmutables y la API `java.time` (`LocalDate`, `LocalTime`, `Clock`) para cálculos deterministas de fechas y turnos. |
| **Spring Boot** (`spring-boot-starter-parent`) | `3.5.16` | Estándar para microservicios y APIs REST en Java; centraliza versiones de dependencias, autoconfiguración y servidor embebido Tomcat. |
| **`spring-boot-starter-web`** | Incluida | Provee Spring MVC (`@RestController`, `ResponseEntity`, `@RestControllerAdvice`) e integra **Jackson**, utilizado tanto para contratos HTTP JSON como para la persistencia en `data/data.json`. |
| **`spring-boot-starter-validation`** | Incluida | Habilita Jakarta Bean Validation (`@Valid`, `@NotBlank`, `@Email`, `@Pattern`, `@Min`, `@Max`, `@Positive`) para validar payloads y parámetros de entrada de forma declarativa en la capa de entrada. |
| **`springdoc-openapi-starter-webmvc-ui`** | `2.10.1` | Autogenera la especificación OpenAPI 3 (`/v3/api-docs`) e interfaz interactiva Swagger UI (`/swagger-ui.html`) a partir del código, manteniendo la documentación alineada con los endpoints reales. |
| **`lombok`** | `1.18.48` | Elimina código repetitivo en tiempo de compilación (`@RequiredArgsConstructor`, `@Builder`, `@Getter`/`@Setter`, `@Slf4j`, `@FieldNameConstants`) sin sobrecarga en tiempo de ejecución. |
| **`spring-boot-starter-test`** | Incluida (`test`) | Integra JUnit 5, Mockito, AssertJ y MockMvc para validar lógica de negocio aislada, flujos completos HTTP y concurrencia de forma automatizada. |
| **Maven Wrapper** (`./mvnw` / `mvnw.cmd`) | Incluido | Fija la versión de Maven dentro del repositorio para compilar y ejecutar el proyecto de manera reproducible sin instalación global de Maven. |
| **Postman y Apache JMeter** | Herramientas QA | Postman provee una colección parametrizada para pruebas funcionales de extremo a extremo; JMeter valida el control de concurrencia bajo ráfagas simultáneas con barrera sincronizada. |

---

## Arquitectura y Estructura

```text
HTTP Request (JSON)
       │
       ▼
  Controller (@RestController)   ──▶ Enrutamiento, validación de entrada (@Valid) y contratos HTTP
       │
       ▼
   Service (@Service)            ──▶ Reglas de negocio (turnos, ocupación, solapamiento, estados)
       │
       ▼
   Repository (Interfaces)       ──▶ Contratos de acceso a datos desacoplados del motor físico
       │
       ▼
  JsonFileStorage (@Component)   ──▶ Unidad de trabajo en memoria, ReentrantReadWriteLock(true)
       │                             y escritura atómica (.tmp → Files.move)
       ▼
  data/data.json
```

```text
sabor-gourmet-back-IPSS/
├── data/
│   └── data.json                  # Almacén JSON inicial y persistencia en tiempo de ejecución
├── docs/
│   └── reglas-negocio/            # Especificación funcional y escenarios BDD (Gherkin)
├── jmeter/
│   ├── SaborGourmet-Concurrency.jmx
│   └── README.md                  # Guía de pruebas de concurrencia en Apache JMeter
├── postman/
│   └── SaborGourmet.postman_collection.json
└── src/
    ├── main/java/cl/ipss/saborgourmet/
    │   ├── config/                # Propiedades externalizadas, reloj inyectable y OpenAPI
    │   ├── controller/            # Controladores REST (Client, Table, Reservation)
    │   ├── dto/                   # Contratos de entrada, salida y errores estandarizados
    │   ├── exception/             # Catálogo ErrorCode y GlobalExceptionHandler
    │   ├── model/                 # Entidades de dominio y contenedor RestaurantData
    │   ├── repository/            # Interfaces de repositorio e implementación JSON
    │   └── service/               # Lógica de negocio por módulo
    └── test/                      # Pruebas unitarias, de integración y concurrencia
```

Todas las respuestas de error siguen el formato uniforme `ApiErrorResponse` con código trazable (`ErrorCode`), categoría (`VALIDATION`, `BUSINESS`, `INFRASTRUCTURE`) y detalle de campos cuando corresponde.

---

## Inicio Rápido

### Requisitos
* **JDK 21** o superior instalado en el sistema.

### 1. Ejecutar la aplicación
```bash
./mvnw spring-boot:run
```
*(En Windows: `mvnw.cmd spring-boot:run`)*

La API quedará disponible en `http://localhost:8080`.

### 2. Ejecutar las pruebas automatizadas
```bash
./mvnw clean test
```

### 3. Explorar y probar la API
* **Swagger UI:** `http://localhost:8080/swagger-ui.html`
* **Especificación OpenAPI:** `http://localhost:8080/v3/api-docs`
* **Colección Postman:** [`postman/SaborGourmet.postman_collection.json`](./postman/SaborGourmet.postman_collection.json)
* **Plan de concurrencia JMeter:** [`jmeter/SaborGourmet-Concurrency.jmx`](./jmeter/SaborGourmet-Concurrency.jmx) ([guía de ejecución](./jmeter/README.md))

---

## Configuración

Los parámetros operativos se definen en [`src/main/resources/application.yml`](./src/main/resources/application.yml) y pueden sobrescribirse mediante variables de entorno:

| Variable de entorno | Propiedad | Valor por defecto | Descripción |
| :--- | :--- | :---: | :--- |
| `SERVER_PORT` | `server.port` | `8080` | Puerto HTTP del servidor |
| `APP_STORAGE_FILE_PATH` | `app.storage.file-path` | `data/data.json` | Ruta del archivo JSON de persistencia |
| `APP_RESERVATIONS_TURN_DURATION_MINUTES` | `app.reservations.turn-duration-minutes` | `120` | Duración estándar de cada turno (minutos) |
| `APP_RESERVATIONS_OPENING_TIME` | `app.reservations.opening-time` | `12:00` | Hora diaria de apertura |
| `APP_RESERVATIONS_CLOSING_TIME` | `app.reservations.closing-time` | `23:00` | Hora diaria de cierre |
| `APP_RESERVATIONS_MIN_OCCUPANCY_PERCENTAGE` | `app.reservations.min-occupancy-percentage` | `75` | Ocupación mínima requerida por mesa (%) |
| `APP_RESERVATIONS_CANCELLATION_NOTICE_HOURS` | `app.reservations.cancellation-notice-hours` | `24` | Anticipación mínima para cancelar sin penalización (horas) |
| `APP_RESERVATIONS_MAX_LATE_CANCELLATIONS` | `app.reservations.max-late-cancellations` | `3` | Umbral de cancelaciones tardías para baneo automático |

---

## Endpoints REST

Base path: `/api/v1`

### Clientes (`/api/v1/clients`)

| Método | Ruta | Descripción | Respuestas |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/clients` | Listar clientes (`?includeDeleted=true` incluye registros anonimizados) | `200`, `400` |
| `GET` | `/api/v1/clients/{id}` | Consultar cliente por ID | `200`, `400`, `404` |
| `POST` | `/api/v1/clients` | Registrar un nuevo cliente | `201`, `400`, `409`, `415` |
| `PUT` | `/api/v1/clients/{id}` | Actualizar datos de contacto de un cliente activo | `200`, `400`, `404`, `409` |
| `PATCH` | `/api/v1/clients/{id}/status` | Cambiar estado operativo (`ACTIVE` / `BANNED`) | `200`, `400`, `404`, `409` |
| `DELETE` | `/api/v1/clients/{id}` | Eliminar cliente (físico sin historial o soft-delete anonimizado con historial) | `204`, `400`, `404`, `409` |

### Mesas (`/api/v1/tables`)

| Método | Ruta | Descripción | Respuestas |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/tables` | Listar mesas (`?onlyActive=true` filtra solo habilitadas) | `200`, `400` |
| `GET` | `/api/v1/tables/{id}` | Consultar mesa por ID | `200`, `400`, `404` |
| `GET` | `/api/v1/tables/availability` | Consultar disponibilidad (`?date=YYYY-MM-DD&time=HH:mm&guests=N`) | `200`, `400` |
| `POST` | `/api/v1/tables` | Registrar una nueva mesa | `201`, `400`, `409` |
| `PUT` | `/api/v1/tables/{id}` | Actualizar configuración de una mesa | `200`, `400`, `404`, `409` |
| `DELETE` | `/api/v1/tables/{id}` | Eliminar mesa sin reservas asociadas | `204`, `400`, `404`, `409` |

### Reservas (`/api/v1/reservations`)

| Método | Ruta | Descripción | Respuestas |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/reservations` | Listar reservas (filtros opcionales: `date`, `status`, `clientId`, `tableId`) | `200`, `400` |
| `GET` | `/api/v1/reservations/{id}` | Consultar reserva por ID | `200`, `400`, `404` |
| `POST` | `/api/v1/reservations` | Crear reserva en estado `PENDING` | `201`, `400`, `404`, `409` |
| `PUT` | `/api/v1/reservations/{id}` | Modificar reserva vigente futura | `200`, `400`, `404`, `409` |
| `PATCH` | `/api/v1/reservations/{id}/confirm` | Confirmar reserva `PENDING` futura | `200`, `400`, `404`, `409` |
| `PATCH` | `/api/v1/reservations/{id}/cancel` | Cancelar reserva futura y liberar la mesa | `200`, `400`, `404`, `409` |

---

## Reglas de Negocio Principales

* **Horarios y turnos:** Reservas futuras dentro de `12:00`–`23:00` con turnos fijos de `120` minutos (`endTime <= 23:00`). Se admiten turnos contiguos sin solapamiento sobre la misma mesa.
* **Capacidad y ocupación mínima:** Cada mesa exige un mínimo de `max(1, floor(capacidad * 0.75))` comensales y no puede superar su capacidad máxima.
* **Ciclo de vida y penalizaciones:** Las reservas nacen en `PENDING` y bloquean la mesa de inmediato. Cancelar con menos de `24` horas de anticipación registra una cancelación tardía; al alcanzar `3`, el cliente pasa automáticamente a `BANNED` (rehabilitarlo a `ACTIVE` reinicia el contador).
* **Privacidad e integridad referencial:** Las operaciones de escritura de clientes responden con `OperationResponse` sin eco de datos personales. Al eliminar clientes con historial, se anonimizan en estado `DELETED` conservando las referencias de sus reservas; las mesas con reservas asociadas no pueden eliminarse.
* **Persistencia y concurrencia:** `JsonFileStorage` inicializa automáticamente `data/data.json` si no existe, mantiene secuencias de IDs independientes para evitar reciclaje y serializa lecturas/escrituras mediante `ReentrantReadWriteLock` con volcado atómico.

La especificación completa en formato BDD (Gherkin) se encuentra en [`docs/reglas-negocio/README.md`](./docs/reglas-negocio/README.md).

---

## Declaración de Uso de Inteligencia Artificial (IA)

Durante el desarrollo de este proyecto se utilizó apoyo de inteligencia artificial **Gemini (Google)** bajo una dinámica de *pair programming* como apoyo para revisión de código, identificación de casos borde, contraste de buenas prácticas en Spring Boot y refinamiento de pruebas y documentación. El diseño de la solución, la implementación, las decisiones técnicas, la validación final y la autoría completa del proyecto corresponden íntegramente a la desarrolladora, Gina Norambuena.
