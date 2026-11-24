package com.tedesoft.prestamosservice.infrastructure.inventario;

import com.tedesoft.prestamosservice.config.InventarioClientConfig;
import com.tedesoft.prestamosservice.config.InventarioClientProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import static com.tedesoft.prestamosservice.infrastructure.inventario.InventarioStub.UNIDAD_14;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * CA-7 · Integración real. Solo corre si se pide explícitamente, porque necesita {@code inventario-service}
 * en ejecución con los datos iniciales (la unidad 14 es MUL-014, disponible, 7 días, sin autorización).
 * El CI normal la omite.
 *
 * <p>Desde SCRUM-20 inventario exige token, y desde SCRUM-24 el cliente reenvía el de quien solicita: la prueba
 * pone en el contexto de seguridad el token real de {@code TOKEN_USUARIO} (ver {@code keycloak/README.md}).
 *
 * <pre>
 *   eval "$(python3 keycloak/verificar_tokens.py --exportar)"
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

    @BeforeEach
    void autenticarConElTokenDelEntorno() {
        String token = System.getenv("TOKEN_USUARIO");
        assertThat(token)
                .as("Falta TOKEN_USUARIO: inventario exige token (ver keycloak/README.md, verificar_tokens.py --exportar)")
                .isNotBlank();
        // Préstamos no valida este JWT: solo lo reenvía, y quien lo valida es inventario
        Jwt jwt = Jwt.withTokenValue(token).header("alg", "RS256").subject("token-del-entorno").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ca7_unidad14DeLosDatosIniciales_respondeLoMismoQueElStub() {
        assertThat(clienteReal().obtenerUnidad(14L)).isEqualTo(UNIDAD_14);
    }
}
