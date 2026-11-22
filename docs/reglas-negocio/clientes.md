# Módulo 2: Gestión de Clients (`SG-CLI-01` a `SG-CLI-12`)

> **Rama de implementación:** `eva2/add-clients`  
> **Privacidad:** En cumplimiento del principio de minimización de datos (Ley N° 21.719 / Ley N° 19.628), no se solicita RUT. La unicidad se controla exclusivamente mediante `email` (normalizado en minúsculas); `phone` exige entre 7 y 15 dígitos reales y se almacena normalizado sin restricción de unicidad. Las respuestas de escritura (`POST`, `PUT`, `PATCH`) devuelven `OperationResponse` sin eco de datos personales.  
> **Volver al índice:** [README.md](./README.md)

```gherkin
# language: es
Característica: Gestión de clients del restaurante
  Como personal o usuario del sistema
  Quiero registrar, consultar, actualizar, administrar el estado y eliminar clients
  Para mantener un registro confiable y respetuoso de la privacidad

  @SG-CLI-01
  Escenario: Registro exitoso de un nuevo client sin eco de datos personales
    Dado que no existe ningún client en estado ACTIVE o BANNED con email "ana.perez@correo.cl"
    Cuando envío POST /api/v1/clients con name "Ana Perez", email "ANA.PEREZ@correo.cl" y phone "+56 9 1234-5678"
    Entonces la respuesta es HTTP 201 Created devolviendo únicamente id y mensaje de confirmación
    Y en "data/data.json" el client se guarda con email "ana.perez@correo.cl", phone "+56912345678", status "ACTIVE" y lateCancellations 0

  @SG-CLI-02
  Escenario: Rechazo de registro con datos inválidos o faltantes
    Dado que preparo una solicitud de client con name de menos de 2 caracteres reales, email inválido o phone sin 7 dígitos reales
    Cuando envío POST /api/v1/clients
    Entonces la respuesta es HTTP 400 Bad Request
    Y el cuerpo JSON incluye categoría "VALIDATION", código "VALIDATION_VL001" y detalle en "fieldErrors"

  @SG-CLI-03
  Escenario: Rechazo de client duplicado por email aunque cambie el nombre o el teléfono
    Dado que existe un client "ACTIVE" o "BANNED" registrado como "Juanito Perez" con email "juan@correo.cl"
    Cuando envío POST /api/v1/clients con name "Juan P.", otro phone y el mismo email "JUAN@CORREO.CL"
    Entonces la respuesta es HTTP 409 Conflict con categoría "BUSINESS" y código "CLIENTS_BS002"
    Y no se crea un client duplicado

  @SG-CLI-04
  Escenario: Listado de clients no eliminados
    Dado que existen clients registrados en estados "ACTIVE", "BANNED" y "DELETED"
    Cuando envío GET /api/v1/clients
    Entonces la respuesta es HTTP 200 OK con la lista de clients excluyendo los de estado "DELETED"

  @SG-CLI-05
  Escenario: Consulta de client inexistente por ID
    Dado que no existe ningún client registrado con el ID consultado
    Cuando envío GET /api/v1/clients/{nonExistentId}
    Entonces la respuesta es HTTP 404 Not Found con categoría "BUSINESS" y código "CLIENTS_BS001"

  @SG-CLI-06
  Escenario: Actualización exitosa de un client reportando solo nombres de campos modificados
    Dado que existe un client "ACTIVE" con ID 1
    Y el nuevo email no pertenece a otro client no eliminado
    Cuando envío PUT /api/v1/clients/1 con nuevos datos
    Entonces la respuesta es HTTP 200 OK devolviendo id, mensaje y lista "updatedFields" sin exponer valores
    Y los cambios normalizados se persisten en "data/data.json" solo si existieron modificaciones

  @SG-CLI-07
  Escenario: Rechazo de actualización de client por conflicto de email duplicado o estado BANNED/DELETED
    Dado que existen el client 1 y el client 2 no eliminados, o el client 1 se encuentra en estado "BANNED" o "DELETED"
    Cuando envío PUT /api/v1/clients/1 utilizando el email del client 2, o intentando modificar un client "BANNED" o "DELETED"
    Entonces la respuesta es HTTP 409 Conflict con categoría "BUSINESS" y código "CLIENTS_BS002", "CLIENTS_BS008" o "CLIENTS_BS003"

  @SG-CLI-08
  Escenario: Cambio manual de estado (baneo o rehabilitación) por el personal
    Dado que existe un client con ID 1 en estado "ACTIVE" o "BANNED"
    Cuando el personal envía PATCH /api/v1/clients/1/status con status "BANNED" o "ACTIVE"
    Entonces la respuesta es HTTP 200 OK con "updatedFields" reflejando los campos modificados
    Y si se rehabilita de "BANNED" a "ACTIVE", el contador lateCancellations se reinicia a 0

  @SG-CLI-09
  Escenario: Rechazo de cambio de estado sobre un client eliminado
    Dado que existe un client con ID 2 en estado "DELETED"
    Cuando el personal envía PATCH /api/v1/clients/2/status con status "ACTIVE"
    Entonces la respuesta es HTTP 409 Conflict con categoría "BUSINESS" y código "CLIENTS_BS003"

  @SG-CLI-10
  Escenario: Eliminación física de un client sin historial de reservas
    Dado que el client con ID 1 está "ACTIVE" y no tiene reservations registradas
    Cuando envío DELETE /api/v1/clients/1
    Entonces la respuesta es HTTP 204 No Content
    Y el registro se elimina físicamente de "data/data.json" sin reciclar su ID

  @SG-CLI-11
  Escenario: Soft-delete y anonimización de un client con historial de reservas no vigentes
    Dado que el client con ID 2 está "ACTIVE" y tiene únicamente reservations en estado "CANCELLED" o finalizadas
    Cuando envío DELETE /api/v1/clients/2
    Entonces la respuesta es HTTP 204 No Content
    Y el client pasa a status "DELETED" con name "Deleted Client", email null y phone null
    Y sus reservations históricas conservan la referencia "clientId: 2" sin quedar huérfanas

  @SG-CLI-12
  Escenario: Bloqueo de eliminación de un client con reservas vigentes o en estado BANNED
    Dado que el client con ID 3 tiene al menos una reservation vigente en estado "PENDING" o "CONFIRMED", o se encuentra en estado "BANNED"
    Cuando envío DELETE /api/v1/clients/3
    Entonces la respuesta es HTTP 409 Conflict con código "CLIENTS_BS004" o "CLIENTS_BS007" (exigiendo levantar el baneo antes de eliminar)
```
