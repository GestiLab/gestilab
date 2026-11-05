package com.tedesoft.prestamosservice.config;

import com.tedesoft.prestamosservice.domain.clients.IInventarioClient;
import com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Unit tests — InventarioClientConfig (cliente provisional)")
class InventarioClientConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(InventarioClientConfig.class));

    @Test
    @DisplayName("sin cliente real → responde 'inventario no disponible' en vez de inventar datos")
    void sinClienteReal_debeLanzarDependenciaNoDisponible() {
        runner.run(contexto -> {
            IInventarioClient cliente = contexto.getBean(IInventarioClient.class);

            assertThatThrownBy(() -> cliente.obtenerUnidad(14L))
                    .isInstanceOf(DependenciaNoDisponibleException.class)
                    .hasMessage("inventario no disponible");
        });
    }

    @Test
    @DisplayName("con cliente real registrado → el provisional no actúa")
    void conClienteReal_noDebeRegistrarElProvisional() {
        IInventarioClient real = unidadId -> null;

        runner.withBean("clienteReal", IInventarioClient.class, () -> real)
                .run(contexto -> {
                    assertThat(contexto).hasSingleBean(IInventarioClient.class);
                    assertThat(contexto.getBean(IInventarioClient.class)).isSameAs(real);
                });
    }
}
