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
