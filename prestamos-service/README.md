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
