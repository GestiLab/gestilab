
package com.tedesoft.prestamosservice.integration;

import com.tedesoft.prestamosservice.domain.services.IPrestamoService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "gestilab.eventos.procesador.habilitado=false",
                "management.health.rabbit.enabled=false",
                "gestilab.fotos.s3.habilitado=false"
        }
)
@ActiveProfiles("test")
class PrestamoFotosLimiteHttpTest {

    @LocalServerPort
    private int puerto;

    @MockitoBean
    private IPrestamoService prestamoService;

    @Test
    void archivoMayorDe5MiB_debeResponder400ConElMotivo()
            throws Exception {

        // Un byte más que el máximo permitido.
        byte[] foto = new byte[5 * 1024 * 1024 + 1];

        String boundary = "test-" + UUID.randomUUID();

        String inicio =
                "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; " +
                        "name=\"foto\"; filename=\"grande.png\"\r\n" +
                        "Content-Type: image/png\r\n\r\n";

        String fin = "\r\n--" + boundary + "--\r\n";

        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + puerto +
                                "/api/v1/prestamos-service/prestamos/1/entregar"
                ))
                .header(
                        "Content-Type",
                        "multipart/form-data; boundary=" + boundary
                )
                .header("Accept", "application/json")
                .method(
                        "PATCH",
                        HttpRequest.BodyPublishers.ofByteArrays(
                                List.of(
                                        inicio.getBytes(StandardCharsets.UTF_8),
                                        foto,
                                        fin.getBytes(StandardCharsets.UTF_8)
                                )
                        )
                )
                .build();

        try (HttpClient cliente = HttpClient.newHttpClient()) {

            HttpResponse<String> respuesta = cliente.send(
                    peticion,
                    HttpResponse.BodyHandlers.ofString()
            );

            // CA-6 de SCRUM-22: un archivo que supera el máximo responde 400 explicando el motivo
            assertThat(respuesta.statusCode()).isEqualTo(400);
            assertThat(respuesta.body())
                    .contains("\"status\":400")
                    .contains("La fotografía supera el tamaño máximo de 5 MB");
        }

        // El archivo debe rechazarse antes de llamar al servicio.
        verifyNoInteractions(prestamoService);
    }
}
