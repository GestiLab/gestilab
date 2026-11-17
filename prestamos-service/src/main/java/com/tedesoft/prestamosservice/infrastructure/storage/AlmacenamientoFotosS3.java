
package com.tedesoft.prestamosservice.infrastructure.storage;

import com.tedesoft.prestamosservice.domain.clients.IAlmacenamientoFotos;
import com.tedesoft.prestamosservice.domain.services.ValidadorFotos;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.net.URI;
import java.util.UUID;

@Service
@ConditionalOnProperty(
        name = "gestilab.fotos.s3.habilitado",
        havingValue = "true"
)
public class AlmacenamientoFotosS3 implements IAlmacenamientoFotos {

    private final S3Client cliente;
    private final String bucket;

    public AlmacenamientoFotosS3(
            @Value("${gestilab.fotos.s3.bucket:}") String bucket,
            @Value("${gestilab.fotos.s3.region:us-east-1}") String region,
            @Value("${gestilab.fotos.s3.endpoint:}") String endpoint) {

        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException(
                    "Falta configurar el bucket de fotografías S3"
            );
        }

        this.bucket = bucket;

        S3ClientBuilder constructor = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build());
        if (endpoint != null && !endpoint.isBlank()) {
            constructor.endpointOverride(URI.create(endpoint))
                    .forcePathStyle(true);
        }

        this.cliente = constructor.build();
    }

    @Override
    public String guardar(byte[] contenido, String tipoMime) {

        String tipoValidado = ValidadorFotos.validar(
                contenido, tipoMime
        );

        String clave = "prestamos/fotos/" + UUID.randomUUID();

        try {
            cliente.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(clave)
                            .contentType(tipoValidado)
                            .build(),
                    RequestBody.fromBytes(contenido)
            );

            return clave;

        } catch (SdkException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo guardar la fotografía en S3",
                    ex
            );
        }
    }

    @Override
    public Foto recuperar(String clave) {

        if (clave == null || clave.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "La fotografía no está registrada"
            );
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
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "La fotografía no fue encontrada",
                    ex
            );
        } catch (SdkException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo recuperar la fotografía de S3",
                    ex
            );
        }
    }

    @PreDestroy
    public void cerrar() {
        cliente.close();
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
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo eliminar la fotografía de S3",
                    ex
            );
        }
    }

}
