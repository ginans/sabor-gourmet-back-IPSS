# Módulo 4: Gestión de Reservations y Availability (`SG-RES-01` a `SG-RES-20`)

> **Rama de implementación:** `eva2/add-reservations`  
> **Volver al índice:** [README.md](./README.md)

```gherkin
# language: es
Característica: Consulta de disponibilidad y ciclo de vida de reservations
  Como client o personal del restaurante
  Quiero consultar tables disponibles, crear, modificar, confirmar y cancelar reservations
  Para gestionar eficientemente la ocupación del restaurante sin solapamientos

  @SG-RES-01
  Escenario: Consulta de disponibilidad filtrando por estado activo, capacidad, ocupación mínima y solapamiento
    Dado que existen tables activas: table 1 (capacity 2), table 2 (capacity 4) y table 3 (capacity 6)
    Y la table 2 tiene una reservation "PENDING" o "CONFIRMED" el "2026-12-20" de "19:00" a "21:00"
    Cuando consulto GET /api/v1/tables/availability?date=2026-12-20&time=20:00&guests=3
    Entonces la respuesta es HTTP 200 OK leyendo el almacén una sola vez
    Y se excluye la table 1 (capacidad insuficiente: 3 > 2)
    Y se excluye la table 2 (solapada con el turno 19:00-21:00)
    Y se excluye la table 3 (no alcanza el 75% de ocupación mínima: mínimo 4 personas)

  @SG-RES-02
  Escenario: Creación exitosa de una reservation con bloqueo inmediato de table
    Dado que existe un client "ACTIVE" con ID 1 y una table activa con ID 2 de capacity 4 disponible el "2026-12-20" a las "20:00"
    Cuando envío POST /api/v1/reservations con clientId 1, tableId 2, date "2026-12-20", startTime "20:00" y guestCount 3
    Entonces la respuesta es HTTP 201 Created
    Y la reservation nace en status "PENDING" con endTime "22:00:00" (duración 120 minutos)
    Y la table 2 queda inmediatamente bloqueada para el intervalo "20:00"–"22:00"

  @SG-RES-03
  Escenario: Rechazo de reservation en fecha u hora pasada
    Dado que preparo una solicitud de reservation con date y startTime anteriores al momento actual
    Cuando envío POST /api/v1/reservations
    Entonces la respuesta es HTTP 400 Bad Request con categoría "BUSINESS" y código "RESERVATIONS_BS002"

  @SG-RES-04
  Escenario: Rechazo de reservation fuera del horario de atención del restaurante
    Dado que el horario de atención es de "12:00" a "23:00" con turnos de 120 minutos
    Cuando envío POST /api/v1/reservations con startTime "11:00" o "21:30" (cuyo endTime "23:30" excede el cierre)
    Entonces la respuesta es HTTP 400 Bad Request con categoría "BUSINESS" y código "RESERVATIONS_BS003"

  @SG-RES-05
  Escenario: Rechazo de reservation cuando el client o la table no existen
    Dado que el clientId o el tableId indicado no existe en "data/data.json"
    Cuando envío POST /api/v1/reservations
    Entonces la respuesta es HTTP 404 Not Found con categoría "BUSINESS"

  @SG-RES-06
  Escenario: Rechazo de reservation para client BANNED/DELETED o table inactiva
    Dado que el client se encuentra en status "BANNED" o "DELETED", o la table tiene active "false"
    Cuando envío POST /api/v1/reservations
    Entonces la respuesta es HTTP 409 Conflict con categoría "BUSINESS"

  @SG-RES-07
  Escenario: Rechazo de reservation por superar la capacidad máxima de la table
    Dado que la table 3 tiene capacity 6
    Cuando envío POST /api/v1/reservations para la table 3 con guestCount 7
    Entonces la respuesta es HTTP 409 Conflict con código "RESERVATIONS_BS004"

  @SG-RES-08
  Escenario: Rechazo de reservation por no cubrir la ocupación mínima del 75% (redondeo hacia abajo)
    Dado que la table 3 tiene capacity 6 (mínimo requerido: 4 personas)
    Cuando envío POST /api/v1/reservations para la table 3 con guestCount 3
    Entonces la respuesta es HTTP 409 Conflict con código "RESERVATIONS_BS005"

  @SG-RES-09
  Escenario: Prevención de solapamiento de turnos en la misma table
    Dado que la table 2 tiene una reservation "PENDING" o "CONFIRMED" el "2026-12-20" de "19:00" a "21:00"
    Cuando envío POST /api/v1/reservations para la table 2 el "2026-12-20" a las "20:30" (turno "20:30"-"22:30")
    Entonces la respuesta es HTTP 409 Conflict con código "RESERVATIONS_BS006"

  @SG-RES-10
  Escenario: Habilitación de turnos contiguos sin superposición
    Dado que la table 2 tiene una reservation "PENDING" o "CONFIRMED" el "2026-12-20" de "19:00" a "21:00"
    Cuando envío POST /api/v1/reservations para la table 2 el "2026-12-20" a las "21:00" (turno "21:00"-"23:00")
    Entonces la respuesta es HTTP 201 Created porque el turno contiguo no se superpone

  @SG-RES-11
  Escenario: Un mismo client puede reservar múltiples tables en el mismo turno para grupos grandes
    Dado que el client 1 ya tiene una reservation vigente en la table 2 el "2026-12-20" a las "20:00"
    Y otra table está disponible en el mismo horario
    Cuando el client 1 envía POST /api/v1/reservations para la segunda table el "2026-12-20" a las "20:00"
    Entonces la respuesta es HTTP 201 Created registrando una reservation independiente

  @SG-RES-12
  Escenario: Listado de reservations con filtros opcionales
    Dado que existen reservations registradas en distintas fechas y estados
    Cuando envío GET /api/v1/reservations?date=2026-12-20&status=PENDING
    Entonces la respuesta es HTTP 200 OK con las reservations que coinciden con los filtros aplicados

  @SG-RES-13
  Escenario: Consulta de reservation inexistente por ID
    Dado que no existe ninguna reservation registrada con el ID consultado
    Cuando envío GET /api/v1/reservations/{nonExistentId}
    Entonces la respuesta es HTTP 404 Not Found con categoría "BUSINESS" y código "RESERVATIONS_BS001"

  @SG-RES-14
  Escenario: Modificación exitosa de una reservation vigente futura excluyendo auto-solapamiento
    Dado que existe una reservation futura en status "PENDING" para el mismo clientId
    Cuando envío PUT /api/v1/reservations/{id} cambiando startTime a "19:30" sin chocar con otras reservations
    Entonces la respuesta es HTTP 200 OK
    Y los cambios se persisten en "data/data.json"

  @SG-RES-15
  Escenario: Rechazo de modificación sobre una reservation CANCELLED, pasada o intentando cambiar clientId
    Dado que la reservation está en status "CANCELLED", ya inició en el pasado o la solicitud cambia el clientId titular
    Cuando envío PUT /api/v1/reservations/{id}
    Entonces la respuesta es HTTP 409 Conflict con código "RESERVATIONS_BS007", "RESERVATIONS_BS011" o "RESERVATIONS_BS012"

  @SG-RES-16
  Escenario: Confirmación exitosa de una reservation PENDING futura por client o personal
    Dado que existe una reservation futura en status "PENDING"
    Cuando el client o el personal envía PATCH /api/v1/reservations/{id}/confirm
    Entonces la respuesta es HTTP 200 OK
    Y la reservation cambia a status "CONFIRMED" en "data/data.json"

  @SG-RES-17
  Escenario: Rechazo de confirmación sobre una reservation CONFIRMED, CANCELLED o pasada
    Dado que la reservation está en status "CONFIRMED", "CANCELLED" o su fecha/hora de inicio ya ocurrió
    Cuando envío PATCH /api/v1/reservations/{id}/confirm
    Entonces la respuesta es HTTP 409 Conflict con categoría "BUSINESS"

  @SG-RES-18
  Escenario: Cancelación de reservation vigente futura y liberación inmediata de la table
    Dado que existe una reservation futura en status "PENDING" o "CONFIRMED" para la table 2 el "2026-12-20" a las "20:00"
    Cuando envío PATCH /api/v1/reservations/{id}/cancel
    Entonces la respuesta es HTTP 200 OK
    Y la reservation cambia a status "CANCELLED" en "data/data.json"
    Y la table 2 vuelve a aparecer disponible al consultar GET /api/v1/tables/availability para ese mismo horario

  @SG-RES-19
  Escenario: Rechazo de cancelación sobre una reservation CANCELLED o pasada
    Dado que la reservation ya está en status "CANCELLED" o su fecha/hora de inicio ya ocurrió
    Cuando envío PATCH /api/v1/reservations/{id}/cancel
    Entonces la respuesta es HTTP 409 Conflict con categoría "BUSINESS"

  @SG-RES-20
  Escenario: Registro de cancelación tardía (< 24h) y baneo automático al tercer incumplimiento
    Dado que un client "ACTIVE" tiene lateCancellations igual a 2
    Y posee una reservation futura cuyo inicio ocurre en menos de 24 horas
    Cuando envía PATCH /api/v1/reservations/{id}/cancel
    Entonces la reservation se cancela (HTTP 200 OK) y libera la table
    Y en la misma unidad de trabajo atómica el contador lateCancellations sube a 3 y el status pasa a "BANNED"
```
