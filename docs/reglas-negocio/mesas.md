# Módulo 3: Gestión de Tables (`SG-MES-01` a `SG-MES-10`)

> **Rama de implementación:** `eva2/add-tables`  
> **Volver al índice:** [README.md](./README.md)

```gherkin
# language: es
Característica: Administración y configuración de tables
  Como personal del restaurante Sabor Gourmet
  Quiero crear, consultar, actualizar y eliminar tables
  Para mantener actualizada la configuración del salón sin afectar reservations vigentes

  @SG-MES-01
  Escenario: Creación exitosa de una table
    Dado que no existe una table con number 5
    Cuando envío POST /api/v1/tables con number 5, capacity 4 y location "Main Hall"
    Entonces la respuesta es HTTP 201 Created
    Y la table queda persistida con active "true"

  @SG-MES-02
  Escenario: Rechazo de creación de table con número duplicado
    Dado que ya existe una table registrada con number 5
    Cuando envío POST /api/v1/tables con number 5
    Entonces la respuesta es HTTP 409 Conflict con categoría "BUSINESS" y código "TABLES_BS002"

  @SG-MES-03
  Escenario: Rechazo de creación de table con datos inválidos
    Dado que preparo una solicitud de table con number 0, capacity menor a 1 o location con menos de 2 caracteres reales
    Cuando envío POST /api/v1/tables
    Entonces la respuesta es HTTP 400 Bad Request con categoría "VALIDATION" y código "VALIDATION_VL001"

  @SG-MES-04
  Escenario: Listado completo de tables
    Dado que existen tables configuradas en el restaurante
    Cuando envío GET /api/v1/tables
    Entonces la respuesta es HTTP 200 OK con el listado completo de tables

  @SG-MES-05
  Escenario: Consulta de table inexistente por ID
    Dado que no existe ninguna table registrada con el ID consultado
    Cuando envío GET /api/v1/tables/{nonExistentId}
    Entonces la respuesta es HTTP 404 Not Found con categoría "BUSINESS" y código "TABLES_BS001"

  @SG-MES-06
  Escenario: Actualización exitosa de configuración de table
    Dado que existe la table con ID 1 (number 1, capacity 4, active true) sin conflictos con reservations vigentes
    Cuando envío PUT /api/v1/tables/1 cambiando su capacity a 6 y location a "Terrace"
    Entonces la respuesta es HTTP 200 OK con los datos actualizados en "data/data.json"

  @SG-MES-07
  Escenario: Impedir cambios de capacidad que invaliden reservations vigentes (sobrecupo o subocupación)
    Dado que la table con ID 1 (capacity 6) tiene una reservation vigente ("PENDING" o "CONFIRMED") para 5 personas
    Cuando envío PUT /api/v1/tables/1 intentando reducir su capacity a 4 (o subirla rompiendo el 75% mínimo)
    Entonces la respuesta es HTTP 409 Conflict con código "TABLES_BS003" o "TABLES_BS007"

  @SG-MES-08
  Escenario: Impedir desactivación operativa de una table con reservations vigentes
    Dado que la table con ID 1 tiene al menos una reservation vigente ("PENDING" o "CONFIRMED")
    Cuando envío PUT /api/v1/tables/1 con active "false"
    Entonces la respuesta es HTTP 409 Conflict con categoría "BUSINESS" y código "TABLES_BS004"

  @SG-MES-09
  Escenario: Eliminación exitosa de una table sin reservations asociadas
    Dado que la table con ID 2 no tiene ninguna reservation asociada
    Cuando envío DELETE /api/v1/tables/2
    Entonces la respuesta es HTTP 204 No Content
    Y la table se elimina de "data/data.json" sin reciclar su ID

  @SG-MES-10
  Escenario: Bloqueo de eliminación de una table con reservations asociadas
    Dado que la table con ID 1 posee al menos una reservation asociada (vigente o histórica)
    Cuando envío DELETE /api/v1/tables/1
    Entonces la respuesta es HTTP 409 Conflict con código "TABLES_BS005" protegiendo la integridad referencial
```
