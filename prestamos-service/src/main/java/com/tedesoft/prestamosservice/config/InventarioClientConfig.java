package com.tedesoft.prestamosservice.config;

import com.tedesoft.prestamosservice.domain.clients.IInventarioClient;
import com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cliente provisional mientras no exista el cliente HTTP real (SCRUM-10).
 *
 * <p>Responde siempre "inventario no disponible" en lugar de inventar datos. Solo se registra si no hay
 * otra implementación de {@link IInventarioClient}: al llegar el cliente real este bean deja de actuar y
 * esta clase puede borrarse.
 */
@Configuration
public class InventarioClientConfig {

    public static final String MENSAJE_NO_DISPONIBLE = "inventario no disponible";

    @Bean
    @ConditionalOnMissingBean(IInventarioClient.class)
    public IInventarioClient inventarioClientProvisional() {
        return unidadId -> {
            throw new DependenciaNoDisponibleException(MENSAJE_NO_DISPONIBLE);
        };
    }
}
