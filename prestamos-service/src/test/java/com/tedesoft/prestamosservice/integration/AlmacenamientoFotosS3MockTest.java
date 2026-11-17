
package com.tedesoft.prestamosservice.integration;

import com.tedesoft.prestamosservice.infrastructure.storage.AlmacenamientoFotosS3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(
        named = "gestilab.s3mock.test",
        matches = "true"
)

class AlmacenamientoFotosS3MockTest {

    @Test
    void guardarRecuperarYEliminarFotografia() throws Exception {

        // Credenciales falsas, solamente para S3Mock.
        String anteriorId = System.getProperty("aws.accessKeyId");
        String anteriorClave = System.getProperty("aws.secretAccessKey");

        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");

        String endpoint = "http://127.0.0.1:9090";
        String bucket = "gestilab-test-" + UUID.randomUUID();

        try (S3Client administrador = S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(endpoint))
                .forcePathStyle(true)
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("test", "test")
                        )
                )
                .build()) {

            // Crear un bucket temporal.
            administrador.createBucket(b -> b.bucket(bucket));

            // Probar la clase real de GestiLab.
            AlmacenamientoFotosS3 almacenamiento =
                    new AlmacenamientoFotosS3(
                            bucket,
                            "us-east-1",
                            endpoint
                    );

            try {
                // Generar una fotografía PNG válida.
                BufferedImage imagen = new BufferedImage(
                        2, 2, BufferedImage.TYPE_INT_RGB
                );

                ByteArrayOutputStream salida =
                        new ByteArrayOutputStream();

                ImageIO.write(imagen, "png", salida);
                byte[] contenidoOriginal = salida.toByteArray();

                // 1. GUARDAR
                String clave = almacenamiento.guardar(
                        contenidoOriginal,
                        "image/png"
                );

                assertThat(clave).startsWith("prestamos/fotos/");

                // 2. RECUPERAR
                var recuperada = almacenamiento.recuperar(clave);

                assertThat(recuperada.tipoMime())
                        .isEqualTo("image/png");

                assertThat(recuperada.contenido())
                        .containsExactly(contenidoOriginal);

                // 3. ELIMINAR
                almacenamiento.eliminar(clave);

                // Confirmar que ya no existe.
                assertThatThrownBy(
                        () -> almacenamiento.recuperar(clave)
                )
                        .isInstanceOf(ResponseStatusException.class)
                        .satisfies(error ->
                                assertThat(
                                        ((ResponseStatusException) error)
                                                .getStatusCode()
                                ).isEqualTo(HttpStatus.NOT_FOUND)
                        );

                // Limpiar el bucket temporal.
                administrador.deleteBucket(b -> b.bucket(bucket));

            } finally {
                almacenamiento.cerrar();
            }

        } finally {
            // Restaurar las propiedades anteriores.
            if (anteriorId == null) {
                System.clearProperty("aws.accessKeyId");
            } else {
                System.setProperty("aws.accessKeyId", anteriorId);
            }

            if (anteriorClave == null) {
                System.clearProperty("aws.secretAccessKey");
            } else {
                System.setProperty("aws.secretAccessKey", anteriorClave);
            }
        }
    }
}
