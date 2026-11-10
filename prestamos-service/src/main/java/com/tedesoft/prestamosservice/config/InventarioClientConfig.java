package com.tedesoft.prestamosservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Único lugar donde se decide a dónde apunta el cliente de inventario y cuánto espera (CA-4, CA-5).
 * Los tiempos van al socket (conexión y lectura): sin ellos, un inventario colgado dejaría colgado
 * también a préstamos.
 */
@Configuration
@EnableConfigurationProperties(InventarioClientProperties.class)
public class InventarioClientConfig {

    @Bean
    public RestClient inventarioRestClient(RestClient.Builder builder, InventarioClientProperties propiedades) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(propiedades.timeout());
        fabrica.setReadTimeout(propiedades.timeout());
        return builder
                .baseUrl(propiedades.url())
                .requestFactory(fabrica)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
