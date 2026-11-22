# Módulo 2: Gestión de Clientes (`SG-CLI-01` a `SG-CLI-12`)

> **Rama de implementación:** `eva2/add-clients`  
> **Privacidad:** En cumplimiento del principio de minimización de datos (Ley N° 21.719 / Ley N° 19.628 de Protección de Datos Personales), no se solicita RUT. La unicidad se controla exclusivamente mediante `email` (normalizado en minúsculas); el `telefono` se almacena normalizado como dato de contacto sin restricción de unicidad.  
> **Volver al índice:** [README.md](./README.md)

```gherkin
# language: es
Característica: Gestión de clientes del restaurante
  Como personal o usuario del sistema
  Quiero registrar, consultar, actualizar, administrar el estado y eliminar clientes
  Para mantener un registro confiable y respetuoso de la privacidad

  @SG-CLI-01
  Escenario: Registro exitoso de un nuevo cliente
    Dado que no existe ningún cliente activo o baneado con email "ana.perez@correo.cl"
    Cuando envío POST /api/v1/clientes con nombre "Ana Pérez", email "ANA.PEREZ@correo.cl" y teléfono "+56 9 1234-5678"
    Entonces la respuesta es HTTP 201 Created
    Y el cliente se guarda con email normalizado "ana.perez@correo.cl", teléfono "+56912345678", estado "ACTIVO" y cancelacionesTardias 0

  @SG-CLI-02
  Escenario: Rechazo de registro con datos inválidos o faltantes
    Dado que preparo una solicitud de cliente con nombre vacío o email con formato inválido
    Cuando envío POST /api/v1/clientes
    Entonces la respuesta es HTTP 400 Bad Request
    Y el cuerpo JSON incluye categoría "VALIDATION" y el detalle de los campos inválidos sin trazas internas

  @SG-CLI-03
  Escenario: Rechazo de cliente duplicado por email aunque cambie el nombre o el teléfono
    Dado que existe un cliente "ACTIVO" o "BANEADO" registrado como "Juanito Pérez" con email "juan@correo.cl"
    Cuando envío POST /api/v1/clientes con nombre "Juan P.", otro teléfono y el mismo email "JUAN@CORREO.CL"
    Entonces la respuesta es HTTP 409 Conflict con categoría "BUSINESS"
    Y no se crea un cliente duplicado

  @SG-CLI-04
  Escenario: Listado de clientes no eliminados
    Dado que existen clientes registrados en estados "ACTIVO", "BANEADO" y "ELIMINADO"
    Cuando envío GET /api/v1/clientes
    Entonces la respuesta es HTTP 200 OK con la lista de clientes excluyendo los de estado "ELIMINADO"

  @SG-CLI-05
  Escenario: Consulta de cliente inexistente por ID
    Dado que no existe ningún cliente registrado con el ID consultado
    Cuando envío GET /api/v1/clientes/{idInexistente}
    Entonces la respuesta es HTTP 404 Not Found con categoría "BUSINESS"

  @SG-CLI-06
  Escenario: Actualización exitosa de datos de un cliente existente
    Dado que existe un cliente "ACTIVO" con ID 1
    Y el nuevo email no pertenece a otro cliente no eliminado
    Cuando envío PUT /api/v1/clientes/1 con los nuevos datos
    Entonces la respuesta es HTTP 200 OK
    Y los datos actualizados y normalizados se persisten en "data/datos.json"

  @SG-CLI-07
  Escenario: Rechazo de actualización de cliente por conflicto de email duplicado
    Dado que existen el cliente 1 y el cliente 2 no eliminados
    Cuando envío PUT /api/v1/clientes/1 utilizando el email que ya pertenece al cliente 2
    Entonces la respuesta es HTTP 409 Conflict con categoría "BUSINESS"

  @SG-CLI-08
  Escenario: Cambio manual de estado (baneo o rehabilitación) por el personal
    Dado que existe un cliente con ID 1 en estado "ACTIVO"
    Cuando el personal envía PATCH /api/v1/clientes/1/estado con estado "BANEADO"
    Entonces la respuesta es HTTP 200 OK
    Y el cliente queda persistido en estado "BANEADO"

  @SG-CLI-09
  Escenario: Rechazo de cambio de estado sobre un cliente eliminado
    Dado que existe un cliente con ID 2 en estado "ELIMINADO"
    Cuando el personal envía PATCH /api/v1/clientes/2/estado con estado "ACTIVO"
    Entonces la respuesta es HTTP 409 Conflict con categoría "BUSINESS"

  @SG-CLI-10
  Escenario: Eliminación física de un cliente sin historial de reservas
    Dado que el cliente con ID 1 no tiene reservas registradas
    Cuando envío DELETE /api/v1/clientes/1
    Entonces la respuesta es HTTP 204 No Content
    Y el registro del cliente se elimina físicamente de "data/datos.json"

  @SG-CLI-11
  Escenario: Soft-delete y anonimización de un cliente con historial de reservas no vigentes
    Dado que el cliente con ID 2 tiene únicamente reservas en estado "CANCELADA" o pasadas
    Cuando envío DELETE /api/v1/clientes/2
    Entonces la respuesta es HTTP 204 No Content
    Y el cliente pasa a estado "ELIMINADO" con nombre "Cliente Eliminado", email null y teléfono null
    Y sus reservas históricas conservan la referencia "clienteId: 2" sin quedar huérfanas

  @SG-CLI-12
  Escenario: Bloqueo de eliminación de un cliente con reservas vigentes
    Dado que el cliente con ID 3 tiene al menos una reserva vigente en estado "PENDIENTE" o "CONFIRMADA"
    Cuando envío DELETE /api/v1/clientes/3
    Entonces la respuesta es HTTP 409 Conflict indicando que debe cancelar sus reservas vigentes primero
```
