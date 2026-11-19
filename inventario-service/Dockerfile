# Etapa 1: compilar la aplicacion con Maven y Java 21
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copiamos primero las dependencias para aprovechar la cache
COPY pom.xml .

# Reintenta fallos transitorios al resolver dependencias; falla si persisten.
RUN for intento in 1 2 3; do \
        mvn -U -B -ntp dependency:go-offline && exit 0; \
        if [ "$intento" -eq 3 ]; then exit 1; fi; \
        echo "No se pudieron resolver dependencias; reintentando ($intento/3)..." >&2; \
        sleep $((intento * 5)); \
    done; exit 1

# Copiamos el codigo fuente
COPY src ./src

# Compilamos el JAR
# Las pruebas se ejecutan en GitHub Actions
RUN mvn -B -ntp package -DskipTests

# Etapa 2: imagen final solo con Java
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Usuario sin privilegios de administrador
RUN addgroup -S app && adduser -S app -G app

# Solo copiamos el JAR generado
COPY --from=build --chown=app:app /app/target/*.jar app.jar

USER app

# API REST y Actuator
EXPOSE 8080 8081

# Comprobacion de salud del contenedor
HEALTHCHECK --interval=30s --timeout=5s \
  --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8081/salud || exit 1

# Iniciamos la aplicacion
ENTRYPOINT ["java", "-jar", "app.jar"]
