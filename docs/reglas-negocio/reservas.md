# Módulo 4: Gestión de Reservas y Disponibilidad (`SG-RES-01` a `SG-RES-20`)

> **Rama de implementación:** `eva2/add-reservations`  
> **Volver al índice:** [README.md](./README.md)

```gherkin
# language: es
Característica: Consulta de disponibilidad y ciclo de vida de reservas
  Como cliente o personal del restaurante
  Quiero consultar mesas disponibles, crear, modificar, confirmar y cancelar reservas
  Para gestionar eficientemente la ocupación del restaurante sin solapamientos

  @SG-RES-01
  Escenario: Consulta de disponibilidad filtrando por estado activo, capacidad, ocupación mínima y solapamiento
    Dado que existen las mesas activas: Mesa 1 (capacidad 2), Mesa 2 (capacidad 4) y Mesa 3 (capacidad 6)
    Y la Mesa 2 tiene una reserva "PENDIENTE" o "CONFIRMADA" el "2026-12-01" de "19:00" a "21:00"
    Cuando consulto GET /api/v1/mesas/disponibilidad?fecha=2026-12-01&hora=20:00&personas=3
    Entonces la respuesta es HTTP 200 OK
    Y se excluye la Mesa 1 (capacidad insuficiente: 3 > 2)
    Y se excluye la Mesa 2 (solapada con el turno 19:00-21:00)
    Y se excluye la Mesa 3 (no alcanza el 75% de ocupación mínima: mínimo 4 personas)

  @SG-RES-02
  Escenario: Creación exitosa de una reserva con bloqueo inmediato de mesa
    Dado que existe un cliente "ACTIVO" con ID 1 y una mesa activa con ID 2 de capacidad 4 disponible el "2026-12-05" a las "20:00"
    Cuando envío POST /api/v1/reservas con clienteId 1, mesaId 2, fecha "2026-12-05", horaInicio "20:00" y cantidadPersonas 3
    Entonces la respuesta es HTTP 201 Created
    Y la reserva nace en estado "PENDIENTE" con horaFin "22:00" (duración 120 minutos)
    Y la mesa 2 queda inmediatamente bloqueada para el intervalo "20:00"–"22:00"

  @SG-RES-03
  Escenario: Rechazo de reserva en fecha u hora pasada
    Dado que preparo una solicitud de reserva con fecha y hora anterior al momento actual
    Cuando envío POST /api/v1/reservas
    Entonces la respuesta es HTTP 400 Bad Request con categoría "BUSINESS"

  @SG-RES-04
  Escenario: Rechazo de reserva fuera del horario de atención del restaurante
    Dado que el horario de atención es de "12:00" a "23:00" con turnos de 120 minutos
    Cuando envío POST /api/v1/reservas con horaInicio "11:00" o "21:30" (cuyo horaFin "23:30" excede el cierre)
    Entonces la respuesta es HTTP 400 Bad Request con categoría "BUSINESS"

  @SG-RES-05
  Escenario: Rechazo de reserva cuando el cliente o la mesa no existen
    Dado que el clienteId o el mesaId indicado no existe en "data/datos.json"
    Cuando envío POST /api/v1/reservas
    Entonces la respuesta es HTTP 404 Not Found con categoría "BUSINESS"

  @SG-RES-06
  Escenario: Rechazo de reserva para cliente baneado/eliminado o mesa inactiva
    Dado que el cliente se encuentra en estado "BANEADO" o "ELIMINADO", o la mesa tiene activa "false"
    Cuando envío POST /api/v1/reservas
    Entonces la respuesta es HTTP 409 Conflict con categoría "BUSINESS"

  @SG-RES-07
  Escenario: Rechazo de reserva por superar la capacidad máxima de la mesa
    Dado que la mesa 3 tiene capacidad 6
    Cuando envío POST /api/v1/reservas para la mesa 3 con cantidadPersonas 7
    Entonces la respuesta es HTTP 409 Conflict por capacidad insuficiente

  @SG-RES-08
  Escenario: Rechazo de reserva por no cubrir la ocupación mínima del 75% (redondeo hacia abajo)
    Dado que la mesa 3 tiene capacidad 6 (mínimo requerido: 4 personas)
    Cuando envío POST /api/v1/reservas para la mesa 3 con cantidadPersonas 3
    Entonces la respuesta es HTTP 409 Conflict por no alcanzar la ocupación mínima permitida

  @SG-RES-09
  Escenario: Prevención de solapamiento de turnos en la misma mesa
    Dado que la mesa 1 tiene una reserva "PENDIENTE" o "CONFIRMADA" el "2026-12-05" de "19:00" a "21:00"
    Cuando envío POST /api/v1/reservas para la mesa 1 el "2026-12-05" a las "20:30" (turno "20:30"-"22:30")
    Entonces la respuesta es HTTP 409 Conflict por superposición de horario

  @SG-RES-10
  Escenario: Habilitación de turnos contiguos sin superposición
    Dado que la mesa 1 tiene una reserva "PENDIENTE" o "CONFIRMADA" el "2026-12-05" de "19:00" a "21:00"
    Cuando envío POST /api/v1/reservas para la mesa 1 el "2026-12-05" a las "21:00" (turno "21:00"-"23:00")
    Entonces la respuesta es HTTP 201 Created porque el turno contiguo no se superpone

  @SG-RES-11
  Escenario: Un mismo cliente puede reservar múltiples mesas en el mismo turno para grupos grandes
    Dado que el cliente 1 ya tiene una reserva vigente en la mesa 1 el "2026-12-05" a las "20:00"
    Y la mesa 2 está disponible en el mismo horario
    Cuando el cliente 1 envía POST /api/v1/reservas para la mesa 2 el "2026-12-05" a las "20:00"
    Entonces la respuesta es HTTP 201 Created registrando una reserva independiente para la mesa 2

  @SG-RES-12
  Escenario: Listado de reservas con filtros opcionales
    Dado que existen reservas registradas en distintas fechas y estados
    Cuando envío GET /api/v1/reservas?fecha=2026-12-05&estado=PENDIENTE
    Entonces la respuesta es HTTP 200 OK con las reservas que coinciden con los filtros aplicados

  @SG-RES-13
  Escenario: Consulta de reserva inexistente por ID
    Dado que no existe ninguna reserva registrada con el ID consultado
    Cuando envío GET /api/v1/reservas/{idInexistente}
    Entonces la respuesta es HTTP 404 Not Found con categoría "BUSINESS"

  @SG-RES-14
  Escenario: Modificación exitosa de una reserva vigente excluyendo auto-solapamiento
    Dado que existe la reserva 1 en estado "PENDIENTE" para la mesa 1 el "2026-12-05" a las "19:00"
    Cuando envío PUT /api/v1/reservas/1 cambiando la horaInicio a "19:30" sin chocar con otras reservas
    Entonces la respuesta es HTTP 200 OK
    Y los cambios se persisten en "data/datos.json"

  @SG-RES-15
  Escenario: Rechazo de modificación sobre una reserva cancelada
    Dado que la reserva 1 se encuentra en estado "CANCELADA"
    Cuando envío PUT /api/v1/reservas/1 con nuevos datos
    Entonces la respuesta es HTTP 409 Conflict con categoría "BUSINESS"

  @SG-RES-16
  Escenario: Confirmación exitosa de una reserva pendiente por cliente o administrador
    Dado que existe la reserva 1 en estado "PENDIENTE"
    Cuando el cliente o el personal envía PATCH /api/v1/reservas/1/confirmar
    Entonces la respuesta es HTTP 200 OK
    Y la reserva cambia a estado "CONFIRMADA" en "data/datos.json"

  @SG-RES-17
  Escenario: Rechazo de confirmación sobre una reserva ya confirmada o cancelada
    Dado que la reserva 1 se encuentra en estado "CONFIRMADA" o "CANCELADA"
    Cuando envío PATCH /api/v1/reservas/1/confirmar
    Entonces la respuesta es HTTP 409 Conflict con categoría "BUSINESS"

  @SG-RES-18
  Escenario: Cancelación de reserva vigente y liberación inmediata de la mesa
    Dado que existe la reserva 1 en estado "PENDIENTE" o "CONFIRMADA" para la mesa 1 el "2026-12-10" a las "20:00"
    Cuando envío PATCH /api/v1/reservas/1/cancelar
    Entonces la respuesta es HTTP 200 OK
    Y la reserva cambia a estado "CANCELADA" en "data/datos.json"
    Y la mesa 1 vuelve a aparecer disponible al consultar GET /api/v1/mesas/disponibilidad para ese mismo horario

  @SG-RES-19
  Escenario: Rechazo de cancelación sobre una reserva ya cancelada
    Dado que la reserva 1 ya se encuentra en estado "CANCELADA"
    Cuando envío PATCH /api/v1/reservas/1/cancelar
    Entonces la respuesta es HTTP 409 Conflict con categoría "BUSINESS"

  @SG-RES-20
  Escenario: Registro de cancelación tardía (< 24h) y baneo automático al tercer incumplimiento
    Dado que un cliente "ACTIVO" tiene cancelacionesTardias igual a 2
    Y posee una reserva vigente cuyo inicio ocurre en menos de 24 horas
    Cuando envía PATCH /api/v1/reservas/{id}/cancelar
    Entonces la reserva se cancela (HTTP 200 OK) y libera la mesa
    Y el contador cancelacionesTardias del cliente aumenta a 3
    Y el estado del cliente cambia automáticamente a "BANEADO" en "data/datos.json"
```
