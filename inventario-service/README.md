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

## Seguridad (SCRUM-20)

El servicio valida el token firmado por Keycloak y lee el rol de `realm_access.roles`. Sin token o con uno inválido responde `401`; con un token válido del rol equivocado, `403`.

| Operación | `usuario` | `auxiliar` |
|---|---|---|
| `GET` tipos de equipo y unidades (lista y por id) | sí | sí |
| `POST` y `PUT` de tipos de equipo y de unidades | no (`403`) | sí |

| Variable | Qué es | Por defecto |
|---|---|---|
| `KEYCLOAK_ISSUER_URI` | El `iss` que debe traer el token. | `http://localhost:8180/realms/gestilab` |
| `KEYCLOAK_JWK_SET_URI` | De dónde se descargan las claves públicas para comprobar la firma. | `<issuer>/protocol/openid-connect/certs` |

Las claves se piden con el primer token, no al arrancar: el servicio levanta aunque Keycloak no esté. Si Keycloak no responde en 3 s, el token se rechaza con `401`. `/salud` (puerto de gestión) no pide token.

Cada `401` y `403` deja una línea en el registro `gestilab.seguridad` con fecha, sujeto, método, ruta, rol y motivo, y nunca el token. Los mensajes de error son fijos (ver `docs/guia-errores.md`).

**Cómo declarar el rol de un endpoint nuevo** (obligatorio desde la primera versión del endpoint):
1. Abre `SeguridadConfig` (`delivery/security`) y agrega una línea en `authorizeHttpRequests`, antes de `anyRequest()`.
2. Usa `antMatcher(HttpMethod.X, BASE + "/ruta")` con `hasRole(...)` o `hasAnyRole(...)`; la primera regla que coincide gana.
3. Un endpoint sin línea solo exige uno de los dos roles: no lo dejes así.
4. La regla se aplica antes de validar el cuerpo, así que quien no tiene el rol recibe `403` y no `400`.
5. Agrega la prueba con el rol equivocado que espera `403` en `AutorizacionInventarioTest`.

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
