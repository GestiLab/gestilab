# prestamos-service

Microservicio de préstamos de Gestilab (Spring Boot 3.5, Java 21). Usa un `java.time.Clock` inyectado (zona `America/Bogota`) para que la fecha "de hoy" se pueda fijar en las pruebas.

## Puesta en marcha

1. **Requisitos:** Java 21 y Docker con Docker Compose.
2. **Base de datos:** levanta Postgres (puerto `15434`, BD `prestamos_db`).
   ```bash
   docker compose up -d
   ```
3. **Arranque y salud:** inicia el servicio (API en `8090`, Actuator en `8091`) y, en otra terminal, comprueba la salud. Debe responder `200` con `{"status":"UP"}`.
   ```bash
   ./mvnw spring-boot:run
   curl localhost:8091/salud
   ```
4. **Pruebas:** usan H2 en memoria, no necesitan Docker.
   ```bash
   ./mvnw test
   ```

Credenciales por defecto: `devdb` / `devdb`. Se cambian con `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`.

Para usar otros valores, copia `.env.example` a `.env` (está en el `.gitignore`, no se sube). `docker compose` lo lee solo; para la aplicación expórtalo antes de arrancar: `set -a; source .env; set +a; ./mvnw spring-boot:run`.

## Préstamos

Ruta base: `/api/v1/prestamos-service/prestamos`.

### `POST /prestamos`: solicitar una unidad

```bash
curl -X POST localhost:8090/api/v1/prestamos-service/prestamos \
  -H 'Content-Type: application/json' \
  -d '{"unidadId": 14, "solicitanteId": "ana"}'
```

| Campo | Reglas |
|---|---|
| `unidadId` | Obligatorio, mayor que 0. Es el id de la unidad en `inventario-service`. |
| `solicitanteId` | Obligatorio, hasta 100 caracteres, sin caracteres de control. Se recortan los espacios. Es **provisional**: con la seguridad (SCRUM-20) saldrá del `sub` del token y este campo desaparecerá. |

Responde `201` con el encabezado `Location` y el préstamo:

```json
{
  "id": 1,
  "unidadId": 14,
  "tipoId": 1,
  "solicitanteId": "ana",
  "estado": "SOLICITADO",
  "duracionAplicadaDias": 7,
  "fechaSolicitud": "2026-11-09",
  "fechaDevolucionPrevista": "2026-11-16"
}
```

La fecha prevista es la de hoy (reloj inyectado) más la duración máxima del tipo, que informa inventario. Esa duración se guarda en el préstamo: si la regla del tipo cambia después, el préstamo no cambia. No hay reglas por tipo de equipo en este servicio.

### `GET /prestamos/{id}`: consultar un préstamo

Responde `200` con el mismo cuerpo, o `404` si no existe.

### `GET /prestamos`: listar préstamos

```bash
curl 'localhost:8090/api/v1/prestamos-service/prestamos?persona=ana&estado=SOLICITADO'
```

| Parámetro | Reglas |
|---|---|
| `persona` | Opcional. Solo los préstamos de esa persona. Se recortan los espacios y después debe tener entre 1 y 100 caracteres, sin caracteres de control. |
| `estado` | Opcional. `SOLICITADO`, `ENTREGADO` o `DEVUELTO`, en mayúsculas. |

Los dos filtros se combinan; sin filtros trae todos. Responde `200` con una lista del mismo cuerpo que al consultar un préstamo, del más reciente al más antiguo, o con `[]` si nada coincide. Un parámetro inválido responde `400` con el formato común y el detalle en `errores`.

Hoy el servicio no valida quién pregunta. Con la seguridad (SCRUM-20), ver los préstamos de otra persona quedará reservado al auxiliar y los propios saldrán del token (SCRUM-24).

### `GET /prestamos/retrasos`: listar los préstamos retrasados

```bash
curl localhost:8090/api/v1/prestamos-service/prestamos/retrasos
```

Es solo para el auxiliar. Hoy el servicio no valida el rol: la restricción llega con la seguridad (SCRUM-20, CA-13). Responde `200` con la lista, del más atrasado al menos atrasado, o con `[]` si no hay retrasos:

```json
[
  {
    "prestamoId": 1,
    "unidadId": 14,
    "solicitanteId": "ana",
    "fechaDevolucionPrevista": "2026-11-17",
    "diasRetraso": 3
  }
]
```

Un préstamo está retrasado cuando está `ENTREGADO` y su fecha de devolución prevista es **anterior** a hoy; el día del vencimiento aún no cuenta. Los `SOLICITADO` y los `DEVUELTO` nunca aparecen. El retraso no se guarda: se calcula en cada consulta con el reloj inyectado (`diasRetraso` = hoy menos la fecha prevista), así que las pruebas lo fijan con `Clock.fixed(...)`. `unidadId` es el id de la unidad en inventario: este listado no consulta a `inventario-service`.

### Errores

Todos usan el formato común de `docs/guia-errores.md`.

| Código | Cuándo |
|---|---|
| 400 | Cuerpo que no cumple las validaciones, JSON ilegible o `id` que no es un número. |
| 404 | La unidad (al solicitar) o el préstamo (al consultar) no existe. |
| 409 | La unidad no está disponible, la unidad ya tiene un préstamo activo, o la persona ya tiene 3 préstamos activos (tope). |
| 502 | Inventario respondió, pero rechazó la petición (4xx distinto de 404). |
| 503 | Inventario no responde, tarda más del tiempo máximo, falla (5xx) o contesta algo que no cumple el contrato. |

Un préstamo activo es el que aún no se ha devuelto (`SOLICITADO` o `ENTREGADO`). El tope de 3 es una regla de este servicio.

**Límite conocido:** el tope y la regla de "una unidad, un préstamo" se comprueban antes de guardar y no son atómicos. Con solicitudes simultáneas pueden superarse. Cerrarlo exige una restricción en la base de datos y está previsto con SCRUM-19.

## Cliente de inventario

`prestamos-service` consulta a `inventario-service` por HTTP (`GET /unidades/{id}`, contrato en `docs/contrato/openapi.yaml`) para saber el estado de una unidad y las reglas de su tipo. Nunca accede a la base de datos de inventario. El dominio usa la interfaz `IInventarioClient`; la implementación es `InventarioHttpClient`.

| Variable | Por defecto | Para qué |
|---|---|---|
| `INVENTARIO_URL` | `http://localhost:8080/api/v1/inventario-service` | Dirección de inventario, con el prefijo de la API. |
| `INVENTARIO_TIMEOUT` | `3s` | Tiempo máximo de conexión y de espera de respuesta. |

Resultados: unidad encontrada → objeto de dominio; inventario responde 404 → `RecursoNoEncontradoException` (404); inventario apagado, lento (más del timeout), con 5xx o con una respuesta que no cumple el contrato → `DependenciaNoDisponibleException` (503); inventario responde otro 4xx (400, 401, 403...) → `DependenciaRechazadaException` (502). Ver `docs/guia-errores.md`.

Las pruebas del cliente usan un stub HTTP del contrato y no necesitan inventario. La prueba contra inventario real (CA-7) está desactivada por defecto; con ambos servicios en ejecución y los datos iniciales cargados:

```bash
INVENTARIO_IT=true ./mvnw test -Dtest=InventarioHttpClientIntegracionTest
```
