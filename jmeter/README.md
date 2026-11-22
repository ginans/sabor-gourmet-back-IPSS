# Pruebas de Concurrencia en Apache JMeter

Este directorio contiene el plan de pruebas [`SaborGourmet-Concurrency.jmx`](./SaborGourmet-Concurrency.jmx) para validar el control de concurrencia local (`ReentrantReadWriteLock` + unidad de trabajo atómica en `JsonFileStorage`) con **Apache JMeter 5.6+** sin necesidad de plugins externos.

## Qué valida el plan

El plan crea datos aislados por timestamp en cada ejecución (`setUp`), sincroniza todos los hilos en el mismo milisegundo mediante `SynchronizingTimer` (`20` hilos por defecto) y verifica la integridad exacta al finalizar (`tearDown`):

| Fase | Escenario | Resultado esperado (`N = 20` hilos) |
| :--- | :--- | :--- |
| **setUp** | Crea 1 cliente titular, 1 mesa dedicada (`capacity = 4`) y 1 reserva objetivo de cancelación | `3 × HTTP 201` y `1 × HTTP 200` |
| **1. Reservas simultáneas (`SG-PER-04`)** | 20 hilos ejecutan `POST /api/v1/reservations` sobre la misma mesa, fecha y turno (`20:00`) | `1 × 201` y `19 × 409` (`RESERVATIONS_BS006`), `0 × 500` |
| **2. Registro simultáneo de email** | 20 hilos ejecutan `POST /api/v1/clients` con el mismo correo electrónico | `1 × 201` y `19 × 409` (`CLIENTS_BS002`), `0 × 500` |
| **3. Cancelación simultánea** | 20 hilos ejecutan `PATCH /api/v1/reservations/{id}/cancel` sobre la misma reserva | `1 × 200` y `19 × 409` (`RESERVATIONS_BS010`), `0 × 500` |
| **tearDown** | Consulta `GET /api/v1/reservations` y `GET /api/v1/tables/availability` | Exactamente `1` reserva `PENDING` persistida y disponibilidad liberada en el turno cancelado |

> **Nota:** Las respuestas `409 Conflict` esperadas por exclusión mutua están marcadas como válidas en las aserciones (`Ignore Status` + verificación del código de negocio `RESERVATIONS_BS006`, `CLIENTS_BS002`, `RESERVATIONS_BS010`), por lo que una corrida exitosa muestra **`Error % = 0.00%`**.

## Cómo ejecutarlo

### 1. Levantar la API (recomendado con un archivo JSON temporal para no ensuciar `data/data.json`)

```bash
APP_STORAGE_FILE_PATH=/tmp/sabor-gourmet-jmeter.json ./mvnw spring-boot:run
```

### 2. Ejecutar desde CLI con reporte HTML automático (recomendado)

```bash
rm -rf jmeter/results.jtl jmeter/report
jmeter -n -t jmeter/SaborGourmet-Concurrency.jmx \
  -Jhost=localhost -Jport=8080 -Jthreads=20 \
  -l jmeter/results.jtl -e -o jmeter/report
```

Al finalizar, abre `jmeter/report/index.html` en el navegador para revisar latencias, throughput y tasa de error (`0%`).

### 3. Ejecutar desde la interfaz gráfica (JMeter GUI)

1. Abre Apache JMeter (`jmeter`).
2. Ve a **File → Open** y selecciona `jmeter/SaborGourmet-Concurrency.jmx`.
3. Haz clic en **Start** (`Ctrl + R`) y observa los resultados en **Summary Report** y **View Results Tree**.

## Parámetros configurables (`-J`)

| Propiedad | Valor por defecto | Descripción |
| :--- | :--- | :--- |
| `protocol` | `http` | Protocolo HTTP/HTTPS |
| `host` | `localhost` | Host donde corre la API |
| `port` | `8080` | Puerto del servidor Spring Boot |
| `threads` | `20` | Hilos simultáneos sincronizados por escenario |
| `bookingDate` | `2027-06-15` | Fecha futura para las pruebas de reservas |
| `bookingTime` | `20:00` | Horario del escenario de reservas concurrentes |
| `cancelTime` | `18:00` | Horario de la reserva usada en el escenario de cancelación concurrente |
