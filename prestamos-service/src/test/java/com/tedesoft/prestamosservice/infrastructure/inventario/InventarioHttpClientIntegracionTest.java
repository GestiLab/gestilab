package com.tedesoft.prestamosservice.infrastructure.inventario;

import com.tedesoft.prestamosservice.config.InventarioClientConfig;
import com.tedesoft.prestamosservice.config.InventarioClientProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import static com.tedesoft.prestamosservice.infrastructure.inventario.InventarioStub.UNIDAD_14;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * CA-7 · Integración real. Solo corre si se pide explícitamente, porque necesita {@code inventario-service}
 * en ejecución con los datos iniciales (la unidad 14 es MUL-014, disponible, 7 días, sin autorización).
 * El CI normal la omite.
 *
 * <pre>
 *   INVENTARIO_IT=true [INVENTARIO_URL=http://localhost:8080/api/v1/inventario-service] ./mvnw test \
 *       -Dtest=InventarioHttpClientIntegracionTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "INVENTARIO_IT", matches = "true")
class InventarioHttpClientIntegracionTest {

    private static InventarioHttpClient clienteReal() {
        String url = System.getenv().getOrDefault("INVENTARIO_URL", "http://localhost:8080/api/v1/inventario-service");
        RestClient restClient = new InventarioClientConfig()
                .inventarioRestClient(RestClient.builder(), new InventarioClientProperties(url, Duration.ofSeconds(3)));
        return new InventarioHttpClient(restClient);
    }

    @Test
    void ca7_unidad14DeLosDatosIniciales_respondeLoMismoQueElStub() {
        assertThat(clienteReal().obtenerUnidad(14L)).isEqualTo(UNIDAD_14);
    }
}
