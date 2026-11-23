package com.tedesoft.prestamosservice.infrastructure.inventario;

import com.tedesoft.prestamosservice.config.InventarioClientConfig;
import com.tedesoft.prestamosservice.config.InventarioClientProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static com.tedesoft.prestamosservice.infrastructure.inventario.InventarioStub.ID_MULTIMETRO;
import static com.tedesoft.prestamosservice.infrastructure.inventario.InventarioStub.UNIDAD_14;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-24, CA-3: la consulta a inventario viaja con el token de quien solicita. El cliente se arma con la misma
 * configuración de producción ({@link InventarioClientConfig}) y habla con un stub HTTP real que anota la
 * cabecera {@code Authorization}, sin servicios externos.
 */
@DisplayName("SCRUM-24: el cliente de inventario reenvía el token de quien solicita")
class InventarioHttpClientTokenTest {

    private InventarioStub stub;

    @BeforeEach
    void iniciarStub() throws Exception {
        stub = InventarioStub.iniciar();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        stub.close();
    }

    private InventarioHttpClient cliente() {
        RestClient restClient = new InventarioClientConfig().inventarioRestClient(RestClient.builder(),
                new InventarioClientProperties(stub.url(), Duration.ofSeconds(3)));
        return new InventarioHttpClient(restClient);
    }

    private static void autenticarComo(String sujeto, String token) {
        Jwt jwt = Jwt.withTokenValue(token).header("alg", "RS256").subject(sujeto).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    @DisplayName("CA-3: con un usuario autenticado, la consulta lleva su token y la unidad se lee con normalidad")
    void conUsuarioAutenticado_reenviaSuToken() {
        autenticarComo("ana", "token-de-ana");

        assertThat(cliente().obtenerUnidad(ID_MULTIMETRO)).isEqualTo(UNIDAD_14);

        assertThat(stub.ultimaAutorizacion()).isEqualTo("Bearer token-de-ana");
    }

    @Test
    @DisplayName("CA-3: cada consulta lleva el token de quien la hace en ese momento, no el de otra persona")
    void cadaConsultaLlevaElTokenDeQuienSolicita() {
        InventarioHttpClient cliente = cliente();

        autenticarComo("ana", "token-de-ana");
        cliente.obtenerUnidad(ID_MULTIMETRO);
        autenticarComo("luis", "token-de-luis");
        cliente.obtenerUnidad(ID_MULTIMETRO);

        assertThat(stub.ultimaAutorizacion()).isEqualTo("Bearer token-de-luis");
    }

    @Test
    @DisplayName("sin nadie autenticado no se agrega ninguna cabecera Authorization")
    void sinAutenticacion_noAgregaLaCabecera() {
        cliente().obtenerUnidad(ID_MULTIMETRO);

        assertThat(stub.ultimaAutorizacion()).isNull();
    }
}
