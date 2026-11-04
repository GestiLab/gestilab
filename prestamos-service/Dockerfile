# Etapa 1: compilar prestamos-service
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

COPY pom.xml .

RUN mvn -B -ntp dependency:go-offline

COPY src ./src

RUN mvn -B -ntp package -DskipTests

# Etapa 2: ejecutar sin herramientas de compilacion
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=build --chown=app:app /app/target/*.jar app.jar

USER app

# Puerto de API y puerto de Actuator
EXPOSE 8090 8091

HEALTHCHECK --interval=30s --timeout=5s \
  --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8091/salud || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
