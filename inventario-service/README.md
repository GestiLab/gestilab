# inventario-service

Microservicio de inventario de Gestilab (Spring Boot 3.5, Java 21).

## Puesta en marcha

1. **Requisitos:** Java 21 y Docker con Docker Compose.
2. **Base de datos:** levanta Postgres (puerto `15433`, BD `inventario_db`).
   ```bash
   docker compose up -d
   ```
3. **Arranque y salud:** inicia el servicio (API en `8080`, Actuator en `8081`) y, en otra terminal, comprueba la salud. Debe responder `200` con `{"status":"UP"}`.
   ```bash
   ./mvnw spring-boot:run
   curl localhost:8081/salud
   ```
4. **Pruebas:** casi todas usan H2 en memoria. Las 3 de `ConsumidorRabbitMqTest` levantan un RabbitMQ real con Testcontainers y se omiten solas si Docker no está disponible.
   ```bash
   ./mvnw test
   ```

Credenciales por defecto: `devdb` / `devdb`. Se cambian con `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`.

Para usar otros valores, copia `.env.example` a `.env` (está en el `.gitignore`, no se sube). `docker compose` lo lee solo; para la aplicación expórtalo antes de arrancar: `set -a; source .env; set +a; ./mvnw spring-boot:run`.

## Evento `UnidadCambioDeEstado` (SCRUM-21)

`inventario-service` escucha el evento que publica `prestamos-service` y deja la unidad como `PRESTADA` o `DISPONIBLE`. El formato del mensaje y cómo se publica están en el README de `prestamos-service`.

| Dato | Valor |
|---|---|
| Exchange | `gestilab.unidades.eventos` (`topic`, durable). También lo declara `prestamos-service`, con los mismos argumentos |
| Routing key | `unidad.estado.cambiado` |
| Cola propia | `inventario.unidad-estado` (durable). Se declara y se enlaza al exchange al conectar con RabbitMQ |

Variables de conexión (las mismas que usa `prestamos-service`):

| Variable | Por defecto |
|---|---|
| `RABBITMQ_HOST` | `localhost` |
| `RABBITMQ_PORT` | `5672` |
| `RABBITMQ_USER` | `guest` |
| `RABBITMQ_PASSWORD` | `guest` |

- Sin RabbitMQ el servicio arranca y `/salud` responde (no depende del broker), pero no consume eventos.
- El `docker compose up -d` de esta carpeta solo levanta Postgres: para consumir en local hace falta un RabbitMQ aparte.
- Cada unidad guarda la fecha del último evento aplicado (`ultimo_evento_en`).
- Se descartan con un aviso en el registro, sin error: unidad inexistente, estado distinto de `PRESTADA` o `DISPONIBLE` (incluye uno que no existe), unidad en revisión o fuera de servicio, y evento anterior al último aplicado a esa unidad. Un evento repetido no cambia nada.
- Para apagar el consumidor: `gestilab.eventos.consumidor.habilitado=false` (las pruebas con H2 lo hacen).
