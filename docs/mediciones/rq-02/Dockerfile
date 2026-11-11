# Contenedor Linux para la medición RQ-02 (SCRUM-25, CA-4): Maven + JDK 21 + libfaketime.
# libfaketime intercepta las llamadas al reloj del sistema, así que un proceso cualquiera
# (aquí Maven y la JVM de las pruebas) cree que es otra fecha sin tocar el reloj real.
# Esta imagen vive aquí y no en los servicios: es solo para medir, no para ejecutar la aplicación.
# Parte de la imagen de Maven de referencia del proyecto (Alpine); este Dockerfile le agrega bash y libfaketime.
FROM maven:3.9-eclipse-temurin-21-alpine

RUN apk add --no-cache bash libfaketime
