package com.tedesoft.prestamosservice.infrastructure.inventario;

import com.tedesoft.prestamosservice.config.InventarioClientConfig;
import com.tedesoft.prestamosservice.config.InventarioClientProperties;
import com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import static com.tedesoft.prestamosservice.infrastructure.inventario.InventarioStub.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CA-4 y CA-5: la dirección y el tiempo de espera salen de la configuración, no del código.
 * Se arma el contexto de Spring solo con el cliente y se cambian las propiedades, como haría otro entorno.
 */
class InventarioClientConfigTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(InventarioClientConfig.class, InventarioHttpClient.class);

    @Test
    void sinConfigurarNada_elTimeoutPropuestoEsDe3Segundos() {
        contexto.run(ctx -> {
            InventarioClientProperties propiedades = ctx.getBean(InventarioClientProperties.class);

            assertThat(propiedades.timeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(propiedades.url()).isEqualTo("http://localhost:8080/api/v1/inventario-service");
        });
    }

    @Test
    void ca5_cambiarLaUrlEnLaConfiguracion_apuntaAlNuevoDestinoSinTocarCodigo() throws Exception {
        try (InventarioStub entornoA = InventarioStub.iniciar(); InventarioStub entornoB = InventarioStub.iniciar()) {
            contexto.withPropertyValues("inventario.url=" + entornoA.url()).run(ctx ->
                    assertThat(ctx.getBean(InventarioHttpClient.class).obtenerUnidad(ID_MULTIMETRO)).isEqualTo(UNIDAD_14));
            assertThat(entornoA.peticiones()).isEqualTo(1);
            assertThat(entornoB.peticiones()).isZero();

            contexto.withPropertyValues("inventario.url=" + entornoB.url()).run(ctx ->
                    assertThat(ctx.getBean(InventarioHttpClient.class).obtenerUnidad(ID_MULTIMETRO)).isEqualTo(UNIDAD_14));
            assertThat(entornoA.peticiones()).isEqualTo(1);
            assertThat(entornoB.peticiones()).isEqualTo(1);
        }
    }

    @Test
    void ca4_elTimeoutSeCambiaPorConfiguracion() throws Exception {
        try (InventarioStub stub = InventarioStub.iniciar()) {
            stub.retardar(2_000);

            contexto.withPropertyValues("inventario.url=" + stub.url(), "inventario.timeout=300ms").run(ctx ->
                    assertThatThrownBy(() -> ctx.getBean(InventarioHttpClient.class).obtenerUnidad(ID_MULTIMETRO))
                            .isInstanceOf(DependenciaNoDisponibleException.class));
        }
    }
}
