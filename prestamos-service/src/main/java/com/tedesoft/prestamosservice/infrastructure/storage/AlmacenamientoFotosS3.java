package com.tedesoft.prestamosservice.infrastructure.storage;

import com.tedesoft.prestamosservice.domain.clients.IAlmacenamientoFotos;
import com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

/**
 * Guarda las fotografías en un almacenamiento compatible con S3 (SCRUM-22). Solo traduce a S3: la validación
 * del archivo es de {@code ValidadorFotos}, que el servicio ejecuta antes de llegar aquí.
 *
 * <p>Si el almacenamiento falla o no responde, lanza {@link DependenciaNoDisponibleException} (503) y la
 * causa queda en el log; si el objeto no existe, {@link RecursoNoEncontradoException} (404).
 */
@Service
@ConditionalOnProperty(
        name = "gestilab.fotos.s3.habilitado",
        havingValue = "true"
)
public class AlmacenamientoFotosS3 implements IAlmacenamientoFotos {

    private static final Logger log = LoggerFactory.getLogger(AlmacenamientoFotosS3.class);

    private static final String ALMACENAMIENTO_NO_DISPONIBLE =
            "El almacenamiento de fotografías no está disponible. Intenta más tarde.";

    private final S3Client cliente;
    private final String bucket;

    public AlmacenamientoFotosS3(
            @Value("${gestilab.fotos.s3.bucket:}") String bucket,
            @Value("${gestilab.fotos.s3.region:us-east-1}") String region,
            @Value("${gestilab.fotos.s3.endpoint:}") String endpoint,
            @Value("${gestilab.fotos.s3.timeout-segundos:10}") int timeoutSegundos) {

        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException(
                    "Falta configurar el bucket de fotografías S3"
            );
        }

        this.bucket = bucket;

        // Sin tope, un almacenamiento que acepta la conexión y no responde deja la petición colgada ~2 minutos
        // con la transacción y el bloqueo del préstamo abiertos. El tope cubre la llamada completa con sus reintentos.
        Duration tope = Duration.ofSeconds(Math.max(1, timeoutSegundos));

        S3ClientBuilder constructor = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .overrideConfiguration(configuracion -> configuracion
                        .apiCallTimeout(tope)
                        .apiCallAttemptTimeout(tope));
        if (endpoint != null && !endpoint.isBlank()) {
            constructor.endpointOverride(URI.create(endpoint))
                    .forcePathStyle(true);
        }

        this.cliente = constructor.build();
    }

    @Override
    public String guardar(byte[] contenido, String tipoMime) {

        // Clave aleatoria: no contiene el id del préstamo ni el nombre original del archivo (CA-9)
        String clave = "prestamos/fotos/" + UUID.randomUUID();

        try {
            cliente.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(clave)
                            .contentType(tipoMime)
                            .build(),
                    RequestBody.fromBytes(contenido)
            );

            return clave;

        } catch (SdkException ex) {
            throw noDisponible("guardar", ex);
        }
    }

    @Override
    public Foto recuperar(String clave) {

        if (clave == null || clave.isBlank()) {
            throw new RecursoNoEncontradoException("La fotografía no está registrada");
        }

        try {
            var respuesta = cliente.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(clave)
                            .build()
            );

            return new Foto(
                    respuesta.asByteArray(),
                    respuesta.response().contentType()
            );

        } catch (NoSuchKeyException ex) {
            log.warn("La fotografía {} no existe en el almacenamiento", clave);
            throw new RecursoNoEncontradoException("La fotografía no fue encontrada");
        } catch (SdkException ex) {
            throw noDisponible("recuperar", ex);
        }
    }

    @Override
    public void eliminar(String clave) {
        if (clave == null || clave.isBlank()) {
            return;
        }

        try {
            cliente.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(clave)
                            .build()
            );
        } catch (SdkException ex) {
            throw noDisponible("eliminar", ex);
        }
    }

    @PreDestroy
    public void cerrar() {
        cliente.close();
    }

    /** La causa va al log; al cliente solo el mensaje fijo (mismo criterio que con inventario). */
    private DependenciaNoDisponibleException noDisponible(String operacion, SdkException causa) {
        log.error("No se pudo {} la fotografía en el almacenamiento: {}", operacion, causa.getMessage());
        return new DependenciaNoDisponibleException(ALMACENAMIENTO_NO_DISPONIBLE, causa);
    }
}
