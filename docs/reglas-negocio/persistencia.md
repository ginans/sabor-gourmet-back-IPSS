# Módulo 1: Persistencia en Archivo JSON y Concurrencia (`SG-PER-01` a `SG-PER-04`)

> **Rama de implementación:** `eva2/add-models-and-json-storage`  
> **Volver al índice:** [README.md](./README.md)

```gherkin
# language: es
Característica: Persistencia confiable en JSON y control de concurrencia local
  Como sistema de reservas basado en archivo JSON
  Quiero garantizar la integridad de los datos ante reinicios, errores de E/S y solicitudes simultáneas
  Para evitar pérdida de información y reservas duplicadas

  @SG-PER-01
  Escenario: Inicialización automática del archivo JSON cuando no existe
    Dado que el archivo "data/data.json" no existe en el sistema de archivos
    Cuando la aplicación inicia o realiza la primera operación de escritura sobre el repositorio
    Entonces se crea automáticamente el archivo "data/data.json"
    Y su contenido inicial posee las colecciones vacías "clients", "tables" y "reservations" con formato JSON válido

  @SG-PER-02
  Escenario: Conservación íntegra de los datos tras reiniciar el servidor
    Dado que existen clients, tables y reservations (incluyendo CANCELLED) persistidas en "data/data.json"
    Cuando el servidor se detiene y vuelve a iniciarse
    Entonces todas las consultas GET retornan exactamente los mismos registros e IDs previos al reinicio
    Y los registros en "data/data.json" mantienen referencias por ID ("clientId", "tableId") sin objetos duplicados

  @SG-PER-03
  Escenario: Protección de datos existentes ante errores de escritura en disco
    Dado que "data/data.json" contiene información válida previa
    Cuando ocurre un error de E/S durante la escritura del archivo temporal o su reemplazo atómico
    Entonces el archivo "data/data.json" conserva íntegros los datos previos sin borrarse ni corromperse
    Y la API responde con estado HTTP 500, categoría "INFRASTRUCTURE" y código "INFRA_IN001" sin exponer trazas internas

  @SG-PER-04
  Escenario: Exclusión mutua ante dos reservas simultáneas sobre la misma mesa y turno
    Dado que existen clientes ACTIVE y la table 1 (capacity 4) está disponible el "2026-12-20" a las "20:00"
    Cuando dos hilos ejecutan simultáneamente POST /api/v1/reservations para la table 1 el "2026-12-20" a las "20:00"
    Entonces el mecanismo ReentrantReadWriteLock serializa ambas escrituras en unidad de trabajo atómica
    Y exactamente una solicitud responde HTTP 201 Created
    Y la otra solicitud responde HTTP 409 Conflict con código "RESERVATIONS_BS006"
    Y en "data/data.json" queda registrada únicamente una reserva activa para ese turno
```
