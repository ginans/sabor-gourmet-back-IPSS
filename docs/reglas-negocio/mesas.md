# Módulo 3: Gestión de Mesas (`SG-MES-01` a `SG-MES-10`)

> **Rama de implementación:** `eva2/add-tables`  
> **Volver al índice:** [README.md](./README.md)

```gherkin
# language: es
Característica: Administración y configuración de mesas
  Como personal del restaurante Sabor Gourmet
  Quiero crear, consultar, actualizar y eliminar mesas
  Para mantener actualizada la configuración del salón sin afectar reservas vigentes

  @SG-MES-01
  Escenario: Creación exitosa de una mesa
    Dado que no existe una mesa con número 5
    Cuando envío POST /api/v1/mesas con numero 5, capacidad 4 y ubicacion "Salón Principal"
    Entonces la respuesta es HTTP 201 Created
    Y la mesa queda persistida con estado activa "true"

  @SG-MES-02
  Escenario: Rechazo de creación de mesa con número duplicado
    Dado que ya existe una mesa registrada con número 5
    Cuando envío POST /api/v1/mesas con numero 5
    Entonces la respuesta es HTTP 409 Conflict con categoría "BUSINESS"

  @SG-MES-03
  Escenario: Rechazo de creación de mesa con datos inválidos
    Dado que preparo una solicitud de mesa con numero 0, capacidad menor a 1 o ubicación vacía
    Cuando envío POST /api/v1/mesas
    Entonces la respuesta es HTTP 400 Bad Request con categoría "VALIDATION"

  @SG-MES-04
  Escenario: Listado completo de mesas
    Dado que existen mesas configuradas en el restaurante
    Cuando envío GET /api/v1/mesas
    Entonces la respuesta es HTTP 200 OK con el listado completo de mesas

  @SG-MES-05
  Escenario: Consulta de mesa inexistente por ID
    Dado que no existe ninguna mesa registrada con el ID consultado
    Cuando envío GET /api/v1/mesas/{idInexistente}
    Entonces la respuesta es HTTP 404 Not Found con categoría "BUSINESS"

  @SG-MES-06
  Escenario: Actualización exitosa de configuración de mesa
    Dado que existe la mesa con ID 1 (número 1, capacidad 4, activa true) sin conflictos con reservas vigentes
    Cuando envío PUT /api/v1/mesas/1 cambiando su capacidad a 6 y ubicación a "Terraza"
    Entonces la respuesta es HTTP 200 OK con los datos actualizados en "data/datos.json"

  @SG-MES-07
  Escenario: Impedir reducción de capacidad si afecta reservas vigentes
    Dado que la mesa con ID 1 (capacidad 6) tiene una reserva vigente ("PENDIENTE" o "CONFIRMADA") para 5 personas
    Cuando envío PUT /api/v1/mesas/1 intentando reducir su capacidad a 4
    Entonces la respuesta es HTTP 409 Conflict indicando que existen reservas vigentes afectadas

  @SG-MES-08
  Escenario: Impedir desactivación operativa de una mesa con reservas vigentes
    Dado que la mesa con ID 1 tiene al menos una reserva vigente ("PENDIENTE" o "CONFIRMADA")
    Cuando envío PUT /api/v1/mesas/1 con activa "false"
    Entonces la respuesta es HTTP 409 Conflict con categoría "BUSINESS"

  @SG-MES-09
  Escenario: Eliminación exitosa de una mesa sin reservas asociadas
    Dado que la mesa con ID 2 no tiene ninguna reserva asociada
    Cuando envío DELETE /api/v1/mesas/2
    Entonces la respuesta es HTTP 204 No Content
    Y la mesa se elimina de "data/datos.json"

  @SG-MES-10
  Escenario: Bloqueo de eliminación de una mesa con reservas asociadas
    Dado que la mesa con ID 1 posee al menos una reserva asociada (vigente o histórica)
    Cuando envío DELETE /api/v1/mesas/1
    Entonces la respuesta es HTTP 409 Conflict protegiendo la integridad referencial
```
