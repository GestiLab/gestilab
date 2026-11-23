package com.tedesoft.prestamosservice.infrastructure.inventario;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tedesoft.prestamosservice.config.InventarioClientConfig;
import com.tedesoft.prestamosservice.config.InventarioClientProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

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
    private Logger registro;
    private ListAppender<ILoggingEvent> lineas;

    @BeforeEach
    void iniciarStub() throws Exception {
        // También antes: otra clase del mismo JVM podría haber dejado un contexto en este hilo
        SecurityContextHolder.clearContext();
        stub = InventarioStub.iniciar();
        registro = (Logger) LoggerFactory.getLogger(ReenvioDeTokenInterceptor.class);
        lineas = new ListAppender<>();
        lineas.start();
        registro.addAppender(lineas);
    }

    @AfterEach
    void limpiar() {
        registro.detachAppender(lineas);
        SecurityContextHolder.clearContext();
        stub.close();
    }

    private List<ILoggingEvent> avisos() {
        return lineas.list.stream().filter(linea -> linea.getLevel() == Level.WARN).toList();
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
        assertThat(avisos()).isEmpty();
        assertThat(lineas.list).noneMatch(linea -> linea.getFormattedMessage().contains("token-de-ana"));
    }

    @Test
    @DisplayName("CA-3: cada consulta lleva el token de quien la hace en ese momento, no el de otra persona")
    void cadaConsultaLlevaElTokenDeQuienSolicita() {
        InventarioHttpClient cliente = cliente();

        autenticarComo("ana", "token-de-ana");
        cliente.obtenerUnidad(ID_MULTIMETRO);
        assertThat(stub.ultimaAutorizacion()).isEqualTo("Bearer token-de-ana");

        autenticarComo("luis", "token-de-luis");
        cliente.obtenerUnidad(ID_MULTIMETRO);
        assertThat(stub.ultimaAutorizacion()).isEqualTo("Bearer token-de-luis");
        assertThat(stub.peticiones()).isEqualTo(2);
    }

    @Test
    @DisplayName("sin nadie autenticado no se agrega la cabecera y queda un aviso que explica el 401 de inventario")
    void sinAutenticacion_noAgregaLaCabecera_yLoAvisa() {
        cliente().obtenerUnidad(ID_MULTIMETRO);

        assertThat(stub.ultimaAutorizacion()).isNull();
        assertThat(avisos()).singleElement()
                .satisfies(aviso -> assertThat(aviso.getFormattedMessage()).contains("sin token", "ninguna"));
    }

    @Test
    @DisplayName("con una autenticación que no es un JWT tampoco se agrega la cabecera, y el aviso dice de qué tipo era")
    void autenticacionQueNoEsJwt_noAgregaLaCabecera_yLoAvisa() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ana", "clave-que-no-debe-salir", List.of()));

        cliente().obtenerUnidad(ID_MULTIMETRO);

        assertThat(stub.ultimaAutorizacion()).isNull();
        assertThat(avisos()).singleElement()
                .satisfies(aviso -> assertThat(aviso.getFormattedMessage())
                        .contains("UsernamePasswordAuthenticationToken")
                        .doesNotContain("clave-que-no-debe-salir"));
    }
}
