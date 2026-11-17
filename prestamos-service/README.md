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
4. **Pruebas:** casi todas usan H2 en memoria. Las de concurrencia (PostgreSQL) y las del evento (RabbitMQ) usan Testcontainers: levantan `postgres:16-alpine` y `rabbitmq:3.13-alpine` solas y **se omiten si Docker no está disponible**.
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

### `PATCH /prestamos/{id}/entregar` y `PATCH /prestamos/{id}/recibir`: entregar y recibir una unidad, con foto

La **fotografía es obligatoria**: va como la parte `foto` de una petición `multipart/form-data` (PNG o JPEG, máximo 5 MB). Sin ella (sin cuerpo, con JSON o sin esa parte) responde `400` con `La fotografía es obligatoria` y el préstamo no cambia de estado. Responden `200` con el préstamo actualizado.

```bash
curl -X PATCH http://localhost:8090/api/v1/prestamos-service/prestamos/1/entregar -F "foto=@entrega.jpg"
```

| Ruta | Pasa de | A | Evento publicado |
|---|---|---|---|
| `PATCH /prestamos/{id}/entregar` | `SOLICITADO` | `ENTREGADO` | `UnidadCambioDeEstado` con `nuevoEstado = PRESTADA` |
| `PATCH /prestamos/{id}/recibir` | `ENTREGADO` | `DEVUELTO` | `UnidadCambioDeEstado` con `nuevoEstado = DISPONIBLE` |

Un estado que no corresponde (entregar uno ya entregado, recibir uno sin entregar) responde `409` con el estado actual, no sube la foto y no publica ningún evento. Un préstamo inexistente responde `404`. Si dos personas entregan el mismo préstamo a la vez, la fila se bloquea: una entrega y la otra recibe `409`, y se registra un solo evento.

**Qué se valida de la foto** (`ValidadorFotos`, antes de subir nada): que exista, que no pase de 5 MB (`400` con `La fotografía supera el tamaño máximo de 5 MB`), que su contenido real sea PNG o JPEG (no basta la extensión ni el `Content-Type`) y que el tipo declarado coincida con ese contenido.

### `GET /prestamos/{id}/foto-entrega` y `GET /prestamos/{id}/foto-devolucion`: pedir las fotos

Devuelven la imagen tal cual, con su `Content-Type` (`image/png` o `image/jpeg`) y `Cache-Control: no-store`. La de entrega existe desde que se entrega; la de devolución, solo en un préstamo `DEVUELTO`. Si el préstamo no existe o aún no tiene esa foto responde `404` (JSON). Un cliente web las baja con `HttpClient` y el token y las muestra con una URL de objeto, porque un `<img src>` no puede enviar el encabezado `Authorization`.

### Dónde se guardan las fotos (S3)

La imagen va a un almacenamiento compatible con S3 y en la tabla `prestamo` solo quedan las **claves** (`foto_entrega_clave`, `foto_devolucion_clave`, texto de hasta 255 caracteres). La clave es `prestamos/fotos/<UUID>`: aleatoria, sin el id del préstamo ni el nombre del archivo. Si la transacción falla después de subir la foto, el servicio la borra de S3 para no dejarla huérfana. La foto se sube sin la codificación `aws-chunked` que el SDK de AWS usa por defecto, para que llegue intacta a cualquier almacenamiento compatible, también a los que no la decodifican. Las columnas las crea JPA al arrancar (`ddl-auto=update`); `docs/sql/prestamo-fotografias.sql` es el mismo cambio para aplicarlo a mano.

| Variable | Qué es | Por defecto |
|---|---|---|
| `FOTOS_S3_HABILITADO` | `true` para guardar fotos en S3. Con `false`, entregar y recibir responden `503` (no hay dónde guardar la foto). | `false` |
| `FOTOS_S3_BUCKET` | Nombre del bucket. Obligatorio si está habilitado. | vacío |
| `AWS_REGION` | Región del bucket. | `us-east-1` |
| `FOTOS_S3_ENDPOINT` | Solo para un S3 compatible que no es AWS (MinIO, S3Mock...). Con él se usa acceso por ruta. | vacío |
| `FOTOS_S3_TIMEOUT_SEGUNDOS` | Tope de cada llamada al almacenamiento, con sus reintentos. Si no responde en ese tiempo, `503`. | `10` |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | Credenciales: las lee la cadena por defecto del SDK de AWS (variables de entorno, perfil o rol). **Nunca** van en el repositorio. | |

## Evento `UnidadCambioDeEstado`

`prestamos-service` **nunca escribe en la base de datos de inventario**: solo avisa por RabbitMQ y quien lo consume (SCRUM-21) actualiza la unidad.

| Dato | Valor |
|---|---|
| Exchange | `gestilab.unidades.eventos` (tipo `topic`, durable). Se declara al abrir la primera conexión con RabbitMQ, en el primer ciclo del buzón de eventos (unos segundos después de arrancar), no al arrancar. |
| Routing key | `unidad.estado.cambiado` |
| Mensaje | JSON persistente, `content-type: application/json` |
| Campo `unidadId` | Número: id de la unidad en inventario. |
| Campo `nuevoEstado` | Texto: `PRESTADA` o `DISPONIBLE` (los nombres de `EstadoUnidad` del contrato de inventario). |
| Campo `fecha` | Texto ISO-8601 con desfase, tomado del reloj inyectado (por ejemplo `2026-11-20T10:00:00-05:00`). Sirve para descartar eventos fuera de orden. |

```json
{ "unidadId": 14, "nuevoEstado": "PRESTADA", "fecha": "2026-11-20T10:00:00-05:00" }
```

**Cómo se publica (buzón de eventos).** Al entregar o recibir, el cambio de estado y el evento se guardan **en la misma transacción** (tabla `evento_unidad_pendiente`). Un proceso interno revisa el buzón cada `gestilab.rabbitmq.unidad.reintento-ms` (5000 ms), publica en orden y espera la confirmación de RabbitMQ antes de marcar el evento como publicado. Así la entrega **no falla ni se pierde el evento si RabbitMQ está caído**: el evento sale cuando el broker vuelve. La entrega es *al menos una vez*: quien consume debe tolerar un evento repetido (SCRUM-21, CA-4).

**Para quien consume (inventario).** Declara tu propia cola y enlázala al exchange con la routing key de arriba. Mientras no exista una cola enlazada, RabbitMQ devuelve el mensaje (`mandatory`), el evento queda pendiente y se reintenta sin perder el orden: no se descarta nada. Con `gestilab.eventos.procesador.habilitado=false` el proceso no corre (así lo hacen las pruebas con H2).

### Errores

Todos usan el formato común de `docs/guia-errores.md`.

| Código | Cuándo |
|---|---|
| 400 | Cuerpo que no cumple las validaciones, JSON ilegible o `id` que no es un número. Al entregar o recibir: foto ausente, vacía, de más de 5 MB o que no es PNG/JPEG (ver arriba). |
| 404 | La unidad (al solicitar) o el préstamo (al consultar, entregar o recibir) no existe; o el préstamo aún no tiene la foto que se pide. |
| 409 | La unidad no está disponible, la unidad ya tiene un préstamo activo, la persona ya tiene 3 préstamos activos (tope), o el préstamo no está en el estado que pide entregar o recibir. |
| 502 | Inventario respondió, pero rechazó la petición (4xx distinto de 404). |
| 503 | Inventario no responde, tarda más del tiempo máximo, falla (5xx) o contesta algo que no cumple el contrato. También el almacenamiento de fotos (S3): caído, sin responder en `FOTOS_S3_TIMEOUT_SEGUNDOS`, con error o no configurado. |

Un préstamo activo es el que aún no se ha devuelto (`SOLICITADO` o `ENTREGADO`). El tope de 3 es una regla de este servicio.

**Solicitudes simultáneas.** Dos protecciones, ambas con PostgreSQL:
- *Una unidad, un solo préstamo abierto:* un índice único parcial `ux_prestamo_unidad_abierto` sobre `prestamo (unidad_id)` para los estados activos. Las anotaciones de JPA no pueden declararlo, así que `IndiceUnicoPrestamoAbierto` lo crea con SQL propio en cada arranque (se elimina y se recrea; solo en PostgreSQL). Si la tabla ya tiene una unidad con dos préstamos abiertos no se puede crear: el servicio arranca, registra un `ERROR` con el SQL y hay que corregir esos datos.
- *Tope de 3 por persona:* la solicitud toma un bloqueo de transacción por solicitante (`pg_advisory_xact_lock`) y vuelve a contar dentro de él; quien llega segundo espera y ve el préstamo del primero.

Las pruebas de concurrencia corren contra PostgreSQL real (`ConcurrenciaPostgresTest`), no contra H2, que no reproduce ninguna de las dos protecciones. `/salud` no depende de RabbitMQ (`management.health.rabbit.enabled=false`): con el buzón de eventos, que el broker esté caído no impide operar.

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
