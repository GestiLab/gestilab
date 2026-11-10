package com.tedesoft.prestamosservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

/**
 * Configuración del cliente de inventario (CA-4 y CA-5). Se cambia con {@code INVENTARIO_URL} e
 * {@code INVENTARIO_TIMEOUT}, sin tocar código.
 *
 * @param url     base de la API de inventario, incluido el prefijo {@code /api/v1/inventario-service}.
 * @param timeout tiempo máximo de conexión y de espera de respuesta; pasado ese tiempo se corta la llamada.
 */
@Validated
@ConfigurationProperties(prefix = "inventario")
public record InventarioClientProperties(
        @NotBlank @DefaultValue("http://localhost:8080/api/v1/inventario-service") String url,
        @NotNull @DefaultValue("3s") Duration timeout
) {
}
