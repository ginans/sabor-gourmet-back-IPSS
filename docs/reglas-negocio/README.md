# Reglas de Negocio y Escenarios BDD (Gherkin) — Restaurante "Sabor Gourmet"

Esta carpeta es la **fuente de verdad funcional** de la API REST de reservas del restaurante **Sabor Gourmet** (Evaluación Sumativa Unidad N° 2 — IPSS). Cada escenario es atómico (un único flujo `Dado → Cuando → Entonces`) y lleva un código trazable (`SG-*`).

## Módulos

| Módulo | Códigos | Rama de implementación | Archivo |
| :--- | :---: | :--- | :--- |
| **Persistencia JSON y Concurrencia** | `SG-PER-01..04` | `eva2/add-models-and-json-storage` | [persistencia.md](./persistencia.md) |
| **Gestión de Clientes** | `SG-CLI-01..12` | `eva2/add-clients` | [clientes.md](./clientes.md) |
| **Gestión de Mesas** | `SG-MES-01..10` | `eva2/add-tables` | [mesas.md](./mesas.md) |
| **Gestión de Reservas y Disponibilidad** | `SG-RES-01..20` | `eva2/add-reservations` | [reservas.md](./reservas.md) |

---

## Parámetros Configurables (`application.yml`)

| Parámetro | Valor por defecto | Descripción |
| :--- | :---: | :--- |
| `app.storage.file-path` | `data/datos.json` | Ruta del archivo JSON de persistencia |
| `app.reservas.duracion-turno-minutos` | `120` | Duración estándar de cada turno de reserva |
| `app.reservas.hora-apertura` | `12:00` | Hora de inicio de atención |
| `app.reservas.hora-cierre` | `23:00` | Hora de cierre (`horaFin <= 23:00`) |
| `app.reservas.ocupacion-minima-porcentaje` | `75` | Porcentaje mínimo de sillas ocupadas (redondeado hacia abajo, mínimo 1) |
| `app.reservas.horas-anticipacion-cancelacion` | `24` | Horas mínimas de anticipación para cancelar sin penalización |
| `app.reservas.max-cancelaciones-tardias` | `3` | Cantidad de cancelaciones tardías que gatilla el baneo automático |

### Tabla de referencia: Ocupación mínima del 75% (`Math.max(1, floor(capacidad * 0.75))`)

| Capacidad de la mesa | Mínimo permitido | Rango válido de personas |
| :---: | :---: | :---: |
| 2 | 1 | 1 – 2 |
| 4 | 3 | 3 – 4 |
| 6 | 4 | 4 – 6 |
| 7 | 5 | 5 – 7 |
| 8 | 6 | 6 – 8 |

---

## Proyección a Escala (Documentado, Fuera del Alcance de Eva 2)

Los siguientes mecanismos no se implementan en esta entrega para mantener el foco en la arquitectura CSR y persistencia JSON sin sobreingeniería, pero el diseño queda preparado para incorporarlos en evoluciones futuras:

1. **Persistencia relacional (MySQL / SQLite + Spring Data JPA):** Las interfaces en `repository/` y el modelo plano por IDs permiten sustituir la implementación JSON sin modificar controladores ni lógica de negocio.
2. **Concurrencia distribuida multi-instancia:** En múltiples réplicas detrás de un balanceador, `ReentrantReadWriteLock` (ámbito JVM local) se reemplazaría por bloqueo optimista (`@Version`) o bloqueo pesimista (`SELECT ... FOR UPDATE`) en base de datos, más restricciones `UNIQUE` / índices por rango.
3. **Seguridad y control de acceso (Spring Security + JWT):** Separación de roles `ROLE_CLIENTE` y `ROLE_PERSONAL` con códigos `401 Unauthorized` y `403 Forbidden`.
4. **Auto-cancelación programada (`@Scheduled` + `ShedLock`):** Tarea periódica para cancelar automáticamente reservas `PENDIENTE` que no hayan sido confirmadas antes del plazo configurado (ej. 24 horas antes), utilizando `ShedLock` para evitar ejecuciones duplicadas en entornos distribuidos.
