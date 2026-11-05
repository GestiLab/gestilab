package com.tedesoft.prestamosservice.config;

import com.tedesoft.prestamosservice.domain.clients.IInventarioClient;
import com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Unit tests — InventarioClientConfig (cliente provisional)")
class InventarioClientConfigTest {

    private static final IInventarioClient REAL = unidadId -> null;

    /** Cliente real declarado como @Bean en una configuración que se procesa DESPUÉS de la provisional. */
    @Configuration
    static class ClienteRealComoBean {
        @Bean
        IInventarioClient clienteReal() {
            return REAL;
        }
    }

    /** Cliente real declarado como @Component. */
    @Component
    static class ClienteRealComoComponente implements IInventarioClient {
        @Override
        public com.tedesoft.prestamosservice.domain.clients.UnidadInventario obtenerUnidad(Long unidadId) {
            return null;
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    @Test
    @DisplayName("sin cliente real → responde 'inventario no disponible' en vez de inventar datos")
    void sinClienteReal_debeLanzarDependenciaNoDisponible() {
        runner.withUserConfiguration(InventarioClientConfig.class).run(contexto -> {
            IInventarioClient cliente = contexto.getBean(IInventarioClient.class);

            assertThatThrownBy(() -> cliente.obtenerUnidad(14L))
                    .isInstanceOf(DependenciaNoDisponibleException.class)
                    .hasMessage("inventario no disponible");
        });
    }

    @Test
    @DisplayName("cliente real declarado DESPUÉS de la configuración provisional → arranca y gana el real")
    void clienteRealDespues_noDebeRomperElArranque() {
        runner.withUserConfiguration(InventarioClientConfig.class, ClienteRealComoBean.class).run(contexto -> {
            assertThat(contexto).hasNotFailed();
            assertThat(contexto.getBean(IInventarioClient.class)).isSameAs(REAL);
        });
    }

    @Test
    @DisplayName("cliente real declarado ANTES de la configuración provisional → arranca y gana el real")
    void clienteRealAntes_noDebeRomperElArranque() {
        runner.withUserConfiguration(ClienteRealComoBean.class, InventarioClientConfig.class).run(contexto -> {
            assertThat(contexto).hasNotFailed();
            assertThat(contexto.getBean(IInventarioClient.class)).isSameAs(REAL);
        });
    }

    @Test
    @DisplayName("cliente real como @Component → arranca y gana el real")
    void clienteRealComoComponente_noDebeRomperElArranque() {
        runner.withUserConfiguration(InventarioClientConfig.class, ClienteRealComoComponente.class).run(contexto -> {
            assertThat(contexto).hasNotFailed();
            assertThat(contexto.getBean(IInventarioClient.class)).isInstanceOf(ClienteRealComoComponente.class);
        });
    }
}
