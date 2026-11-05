package com.tedesoft.prestamosservice.config;

import com.tedesoft.prestamosservice.domain.clients.IInventarioClient;
import com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;

/**
 * Cliente provisional mientras no exista el cliente HTTP real (SCRUM-10).
 *
 * <p>Responde siempre "inventario no disponible" en lugar de inventar datos. Está marcado
 * {@link Fallback}: Spring lo usa solo si no hay otra implementación de {@link IInventarioClient}. A
 * diferencia de {@code @ConditionalOnMissingBean}, esto no depende del orden en que se procesen las
 * configuraciones, así que el cliente real puede declararse como {@code @Component} o como {@code @Bean}
 * sin provocar un error de arranque. Al llegar el cliente real esta clase puede borrarse.
 */
@Configuration
public class InventarioClientConfig {

    public static final String MENSAJE_NO_DISPONIBLE = "inventario no disponible";

    @Bean
    @Fallback
    public IInventarioClient inventarioClientProvisional() {
        return unidadId -> {
            throw new DependenciaNoDisponibleException(MENSAJE_NO_DISPONIBLE);
        };
    }
}
